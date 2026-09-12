package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.util.JsonReader;
import android.util.JsonToken;

import com.zcz.javatavern.memory.LongTermMemoryStore;
import com.zcz.javatavern.model.MemoryEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 把本机全部用户数据导出成一个可分享的 ZIP，以及从该 ZIP 恢复。
 *
 * <p><b>为什么是 JSON 而不是直接拷贝 {@code tavern.db}：</b>拷贝数据库文件需要先关闭
 * 连接（单例数据库做不到），而 {@code VACUUM INTO} 要 API 30+（本应用 minSdk 24）。
 * 改用「逐表导出成 JSON」还额外带来两个好处：备份可读可审，且能跨 schema 版本恢复
 * （导入时忽略当前表里不存在的列）。
 *
 * <p><b>两端都是流式的：</b>数据表单独放在 {@code tables.json}，导出时一行一行写、
 * 导入时用 {@link JsonReader} 一行一行解析，几十万条消息的备份也不会把整棵 JSON 树
 * 读进内存；只有体积很小的 {@code manifest.json} 会整体驻留。
 *
 * <p><b>恢复是整体替换，不是合并：</b>先清空受管表再写入，整个过程在单个 SQLite
 * 事务里，失败自动回滚。图片先于数据库落盘，所以事务失败时最坏也只是多出几个无人
 * 引用的孤儿文件，不会出现「数据库指向不存在的图」。
 *
 * <p><b>归档里没有 API Key：</b>密钥由 Android Keystore 加密且与本机绑定，放进可分享
 * 的文件里既无法在别的设备解密、也不安全。恢复时不触碰本机已保存的 Key。
 */
public final class BackupRepository {
    /** 参与备份的表；{@code messages_fts} 由触发器维护，不入档，导入时自动重建。 */
    private static final String[] BACKUP_TABLES = {
            TavernDatabase.TABLE_CHARACTERS,
            TavernDatabase.TABLE_WORLD_ENTRIES,
            TavernDatabase.TABLE_MESSAGES,
            TavernDatabase.TABLE_MESSAGE_VERSIONS,
            TavernDatabase.TABLE_GROUPS,
            TavernDatabase.TABLE_PRESETS,
            TavernDatabase.TABLE_AGENT_AUDIT
    };
    /** 值里存的是本机文件路径的列：导出转相对路径，导入转回绝对路径。 */
    private static final Map<String, String> PATH_COLUMNS = Map.of(
            TavernDatabase.TABLE_CHARACTERS, "avatar",
            TavernDatabase.TABLE_MESSAGES, "attachment_path"
    );
    private static final String[] MANAGED_DIRECTORIES = {
            BackupPaths.AVATAR_DIR,
            BackupPaths.ATTACHMENT_DIR
    };
    private static final String STAGED_TABLES_NAME = "tables.json";

    /** 导出/恢复完成后的统计，用于给用户一句「处理了什么」。 */
    public static final class Summary {
        private final Map<String, Integer> counts;
        private final int fileCount;

        Summary(Map<String, Integer> counts, int fileCount) {
            this.counts = counts;
            this.fileCount = fileCount;
        }

        public int countOf(String table) {
            Integer value = counts.get(table);
            return value == null ? 0 : value;
        }

        public int getCharacterCount() {
            return countOf(TavernDatabase.TABLE_CHARACTERS);
        }

        public int getMessageCount() {
            return countOf(TavernDatabase.TABLE_MESSAGES);
        }

        public int getFileCount() {
            return fileCount;
        }
    }

    private final Context context;
    private final TavernDatabase database;
    private final SecureModelSettingsStore settingsStore;
    private final LongTermMemoryStore memoryStore;
    private final ConversationDraftStore draftStore;

    public BackupRepository(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext;
        this.database = TavernDatabase.get(applicationContext);
        this.settingsStore = new SecureModelSettingsStore(applicationContext);
        this.memoryStore = new LongTermMemoryStore(applicationContext);
        this.draftStore = new ConversationDraftStore(applicationContext);
    }

    // ------------------------------------------------------------------ 导出

    /** 把全部数据写进 {@code destination} 指向的 ZIP。只读本机数据，不改动任何内容。 */
    public Summary export(Uri destination) throws IOException, BackupException {
        String filesDirectory = filesDirectory();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> referencedFiles = new LinkedHashSet<>();

        OutputStream rawOutput = context.getContentResolver().openOutputStream(destination);
        if (rawOutput == null) {
            throw new BackupException("无法写入所选位置，请换一个目录再试");
        }
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(rawOutput))) {
            // 数据表先写：它要遍历游标，同时顺手统计行数与被引用的图片。
            zip.putNextEntry(new ZipEntry(BackupManifest.TABLES_ENTRY_NAME));
            Writer tablesWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            writeTables(tablesWriter, filesDirectory, counts, referencedFiles);
            tablesWriter.flush();
            zip.closeEntry();

            List<String> files = new ArrayList<>();
            for (String relative : referencedFiles) {
                if (new File(filesDirectory, relative).isFile()) {
                    files.add(relative);
                }
            }
            for (String relative : files) {
                zip.putNextEntry(new ZipEntry(BackupPaths.toArchiveEntry(relative)));
                copy(new File(filesDirectory, relative), zip);
                zip.closeEntry();
            }

            // 清单最后写，因为它要带上前面统计出来的行数与文件列表。
            zip.putNextEntry(new ZipEntry(BackupManifest.ENTRY_NAME));
            Writer manifestWriter = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            manifestWriter.write(exportManifest(counts, files).toJson().toString());
            manifestWriter.flush();
            zip.closeEntry();
        }
        return new Summary(counts, countManagedFiles(filesDirectory));
    }

    private BackupManifest exportManifest(Map<String, Integer> counts, List<String> files) {
        return new BackupManifest(
                System.currentTimeMillis(),
                appVersionName(),
                appVersionCode(),
                databaseVersion(),
                counts,
                files,
                settingsStore.exportConnectionSettings(),
                memoriesToJson(),
                draftsToJson()
        );
    }

    /** 逐行写出所有表：内存里始终只有当前这一行。 */
    private void writeTables(
            Writer writer,
            String filesDirectory,
            Map<String, Integer> counts,
            Set<String> referencedFiles
    ) throws IOException {
        SQLiteDatabase readable = database.getReadableDatabase();
        writer.write('{');
        for (int tableIndex = 0; tableIndex < BACKUP_TABLES.length; tableIndex++) {
            String table = BACKUP_TABLES[tableIndex];
            if (tableIndex > 0) {
                writer.write(',');
            }
            writer.write(JSONObject.quote(table));
            writer.write(":[");

            List<String> columns = new ArrayList<>();
            List<Boolean> integerColumns = new ArrayList<>();
            try (Cursor info = readable.rawQuery("PRAGMA table_info(" + table + ")", null)) {
                while (info.moveToNext()) {
                    columns.add(info.getString(1));
                    String declared = info.getString(2);
                    integerColumns.add(declared != null
                            && declared.toUpperCase(Locale.ROOT).contains("INT"));
                }
            }
            String pathColumn = PATH_COLUMNS.get(table);
            int written = 0;
            try (Cursor cursor = readable.query(
                    table, null, null, null, null, null, "rowid ASC")) {
                boolean first = true;
                while (cursor.moveToNext()) {
                    if (!first) {
                        writer.write(',');
                    }
                    first = false;
                    writer.write(rowToJson(
                            cursor, columns, integerColumns, pathColumn,
                            filesDirectory, referencedFiles).toString());
                    written++;
                }
            }
            writer.write(']');
            counts.put(table, written);
        }
        writer.write('}');
    }

    private JSONObject rowToJson(
            Cursor cursor,
            List<String> columns,
            List<Boolean> integerColumns,
            String pathColumn,
            String filesDirectory,
            Set<String> referencedFiles
    ) {
        JSONObject row = new JSONObject();
        try {
            for (int index = 0; index < columns.size(); index++) {
                String column = columns.get(index);
                if (cursor.isNull(index)) {
                    row.put(column, JSONObject.NULL);
                    continue;
                }
                if (integerColumns.get(index)) {
                    row.put(column, cursor.getLong(index));
                    continue;
                }
                String value = cursor.getString(index);
                if (column.equals(pathColumn) && value != null && !value.isEmpty()) {
                    String relative = BackupPaths.toRelative(value, filesDirectory);
                    if (BackupPaths.isManaged(relative)) {
                        referencedFiles.add(relative);
                        value = relative;
                    }
                }
                row.put(column, value == null ? "" : value);
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("无法序列化 " + columns + " 这一行", exception);
        }
        return row;
    }

    private JSONObject memoriesToJson() {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, List<MemoryEntry>> entry : memoryStore.loadAll().entrySet()) {
            JSONArray array = new JSONArray();
            for (MemoryEntry memory : entry.getValue()) {
                JSONObject item = new JSONObject();
                putQuietly(item, "id", memory.getId());
                putQuietly(item, "content", memory.getContent());
                putQuietly(item, "createdAt", memory.getCreatedAt());
                array.put(item);
            }
            putQuietly(result, entry.getKey(), array);
        }
        return result;
    }

    private JSONObject draftsToJson() {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, String> entry : draftStore.loadAll().entrySet()) {
            putQuietly(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static void putQuietly(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("无法序列化 " + key, exception);
        }
    }

    // ------------------------------------------------------------------ 恢复

    /**
     * 用 {@code source} 里的备份整体替换本机数据。
     *
     * <p>调用方必须在后台线程执行，并已向用户确认——这会清空现有角色、聊天与记忆。
     */
    public Summary restore(Uri source) throws IOException, BackupException {
        String filesDirectory = filesDirectory();
        File staging = new File(context.getCacheDir(), "restore-" + System.nanoTime());
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new BackupException("无法创建临时目录，恢复已中止");
        }
        try {
            // 1) 先完整拆包并校验：这一步不做任何破坏性改动，坏归档走到这里就报错退出。
            Archive archive = unpack(source, staging);
            BackupManifest manifest = BackupManifest.fromJson(archive.manifest);

            // 2) 图片先落盘。文件按 UUID 命名，不会覆盖现有文件；即使后面的数据库
            //    事务回滚，也只是多出几个孤儿文件，现有数据不受影响。
            for (String relative : archive.stagedFiles) {
                File target = new File(filesDirectory, relative);
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new BackupException("无法创建数据目录，恢复已中止");
                }
                copy(new File(staging, relative), target);
            }

            // 3) 数据库整体替换（单事务，失败回滚）。
            restoreTables(archive.tablesFile, filesDirectory);

            // 4) 记忆、草稿与非敏感连接设置。
            restorePreferences(manifest);

            // 5) 清掉只属于旧数据集的遗留图片。
            pruneOrphanFiles(filesDirectory);

            return new Summary(manifest.getCounts(), archive.stagedFiles.size());
        } finally {
            deleteRecursively(staging);
        }
    }

    /** 拆包结果：清单（小，驻留内存）、落盘的原始数据表、以及受管图片的相对路径。 */
    private static final class Archive {
        private final JSONObject manifest;
        private final File tablesFile;
        private final List<String> stagedFiles;

        Archive(JSONObject manifest, File tablesFile, List<String> stagedFiles) {
            this.manifest = manifest;
            this.tablesFile = tablesFile;
            this.stagedFiles = stagedFiles;
        }
    }

    /**
     * 把归档拆到暂存目录：清单读进内存，数据表与图片落盘。
     *
     * <p>不修改任何现有数据，因此坏归档不会造成任何破坏。
     */
    private Archive unpack(Uri source, File staging) throws IOException, BackupException {
        InputStream rawInput = context.getContentResolver().openInputStream(source);
        if (rawInput == null) {
            throw new BackupException("无法读取所选文件");
        }
        JSONObject manifest = null;
        File tablesFile = new File(staging, STAGED_TABLES_NAME);
        boolean hasTables = false;
        List<String> stagedFiles = new ArrayList<>();
        String stagingPrefix = staging.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(rawInput))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (BackupManifest.ENTRY_NAME.equals(name)) {
                    manifest = new JSONObject(readEntry(zip));
                    continue;
                }
                if (BackupManifest.TABLES_ENTRY_NAME.equals(name)) {
                    copy(zip, tablesFile);
                    hasTables = true;
                    continue;
                }
                String relative = BackupPaths.fromArchiveEntry(name);
                if (relative.isEmpty()) {
                    continue;
                }
                File target = new File(staging, relative);
                // fromArchiveEntry 已挡掉 .. 与绝对路径，这里再确认落点仍在暂存目录内。
                if (!target.getCanonicalPath().startsWith(stagingPrefix)) {
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new BackupException("无法创建临时目录，恢复已中止");
                }
                copy(zip, target);
                stagedFiles.add(relative);
            }
        } catch (JSONException exception) {
            throw new BackupException("备份清单不是有效的 JSON，文件可能已损坏", exception);
        }
        if (manifest == null) {
            throw new BackupException("压缩包里没有 " + BackupManifest.ENTRY_NAME
                    + "，这不是 JavaTavern 的备份文件");
        }
        if (!hasTables) {
            throw new BackupException("压缩包里缺少 " + BackupManifest.TABLES_ENTRY_NAME
                    + "，备份不完整");
        }
        return new Archive(manifest, tablesFile, stagedFiles);
    }

    /** 用 {@link JsonReader} 流式读回数据表，全程只在内存里保留当前这一行。 */
    private void restoreTables(File tablesFile, String filesDirectory) throws BackupException {
        SQLiteDatabase writable = database.getWritableDatabase();
        writable.beginTransaction();
        try {
            for (String table : BACKUP_TABLES) {
                writable.delete(table, null, null);
            }
            try (JsonReader reader = new JsonReader(new InputStreamReader(
                    new BufferedInputStream(new FileInputStream(tablesFile)),
                    StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String table = reader.nextName();
                    if (!isBackupTable(table)) {
                        reader.skipValue();
                        continue;
                    }
                    Set<String> columns = columnNames(writable, table);
                    String pathColumn = PATH_COLUMNS.get(table);
                    reader.beginArray();
                    while (reader.hasNext()) {
                        insertRow(writable, table, reader, columns, pathColumn, filesDirectory);
                    }
                    reader.endArray();
                }
                reader.endObject();
            }
            writable.setTransactionSuccessful();
        } catch (SQLiteException | IllegalArgumentException exception) {
            throw new BackupException(
                    "写入数据库失败，已回滚到恢复前的数据：" + exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new BackupException("数据段读取失败，文件可能已损坏，已回滚到恢复前的数据",
                    exception);
        } finally {
            writable.endTransaction();
        }
    }

    private void insertRow(
            SQLiteDatabase writable,
            String table,
            JsonReader reader,
            Set<String> columns,
            String pathColumn,
            String filesDirectory
    ) throws IOException {
        ContentValues values = new ContentValues();
        boolean attachmentMissing = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String column = reader.nextName();
            if (!columns.contains(column)) {
                // 来自另一个 schema 版本：当前表里没有这一列，读掉值后忽略。
                reader.skipValue();
                continue;
            }
            JsonToken token = reader.peek();
            if (token == JsonToken.NULL) {
                reader.nextNull();
                values.putNull(column);
                continue;
            }
            if (token == JsonToken.NUMBER) {
                values.put(column, (long) reader.nextDouble());
                continue;
            }
            if (token == JsonToken.BOOLEAN) {
                values.put(column, reader.nextBoolean() ? 1 : 0);
                continue;
            }
            String text = reader.nextString();
            if (column.equals(pathColumn) && BackupPaths.isManaged(text)) {
                if (new File(filesDirectory, text).isFile()) {
                    text = BackupPaths.toAbsolute(text, filesDirectory);
                } else {
                    // 归档里缺这张图（旧备份或手工改过）：清空引用，免得界面上
                    // 留一个永远加载不出来的空图占位。
                    text = "";
                    attachmentMissing = true;
                }
            }
            values.put(column, text);
        }
        reader.endObject();
        if (attachmentMissing) {
            values.put("attachment_mime_type", "");
        }
        if (values.size() == 0) {
            return;
        }
        writable.insertOrThrow(table, null, values);
    }

    private void restorePreferences(BackupManifest manifest) {
        Map<String, List<MemoryEntry>> memories = new LinkedHashMap<>();
        JSONObject memoryJson = manifest.getMemories();
        for (Iterator<String> keys = memoryJson.keys(); keys.hasNext(); ) {
            String characterId = keys.next();
            JSONArray entries = memoryJson.optJSONArray(characterId);
            if (entries == null) {
                continue;
            }
            List<MemoryEntry> parsed = new ArrayList<>();
            for (int index = 0; index < entries.length(); index++) {
                JSONObject item = entries.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                parsed.add(new MemoryEntry(
                        item.optString("id", ""),
                        item.optString("content", ""),
                        item.optLong("createdAt", 0L)
                ));
            }
            memories.put(characterId, parsed);
        }
        memoryStore.replaceAll(memories);

        Map<String, String> drafts = new LinkedHashMap<>();
        JSONObject draftJson = manifest.getDrafts();
        for (Iterator<String> keys = draftJson.keys(); keys.hasNext(); ) {
            String characterId = keys.next();
            drafts.put(characterId, draftJson.optString(characterId, ""));
        }
        draftStore.replaceAll(drafts);

        settingsStore.restoreConnectionSettings(manifest.getSettings());
    }

    /**
     * 删除受管目录里已无任何数据库记录指向的图片。
     *
     * <p>整体替换之后旧数据集的图片必然全部成为孤儿，不清理会一直占着空间。
     */
    private void pruneOrphanFiles(String filesDirectory) {
        Set<String> referenced = new LinkedHashSet<>();
        collectReferencedPaths(
                "SELECT avatar FROM " + TavernDatabase.TABLE_CHARACTERS,
                filesDirectory,
                referenced
        );
        collectReferencedPaths(
                "SELECT attachment_path FROM " + TavernDatabase.TABLE_MESSAGES
                        + " WHERE attachment_path != ''",
                filesDirectory,
                referenced
        );
        for (String directory : MANAGED_DIRECTORIES) {
            File[] children = new File(filesDirectory, directory).listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (!child.isFile() || referenced.contains(directory + "/" + child.getName())) {
                    continue;
                }
                // noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }

    private void collectReferencedPaths(String sql, String filesDirectory, Set<String> output) {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                String relative = BackupPaths.toRelative(cursor.getString(0), filesDirectory);
                if (BackupPaths.isManaged(relative)) {
                    output.add(relative);
                }
            }
        } catch (SQLiteException ignored) {
            // 清理是尽力而为：查不到就什么也不删。
        }
    }

    // ------------------------------------------------------------------ 工具

    private static boolean isBackupTable(String table) {
        for (String candidate : BACKUP_TABLES) {
            if (candidate.equals(table)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> columnNames(SQLiteDatabase readable, String table) {
        Set<String> columns = new LinkedHashSet<>();
        try (Cursor info = readable.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (info.moveToNext()) {
                columns.add(info.getString(1));
            }
        }
        return columns;
    }

    private int countManagedFiles(String filesDirectory) {
        int total = 0;
        for (String directory : MANAGED_DIRECTORIES) {
            File[] children = new File(filesDirectory, directory).listFiles();
            if (children != null) {
                total += children.length;
            }
        }
        return total;
    }

    private String filesDirectory() {
        return context.getFilesDir().getAbsolutePath();
    }

    private int databaseVersion() {
        return database.getReadableDatabase().getVersion();
    }

    private String appVersionName() {
        try {
            String name = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException exception) {
            return "";
        }
    }

    private int appVersionCode() {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            }
            //noinspection deprecation
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException exception) {
            return 0;
        }
    }

    /** 读取 ZIP 当前条目的全部内容。**不关闭** {@code input}，否则后续条目读不到。 */
    private static String readEntry(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8 * 1024];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copy(input, output);
        }
    }

    private static void copy(File source, OutputStream destination) throws IOException {
        try (InputStream input = new FileInputStream(source)) {
            copy(input, destination);
        }
    }

    private static void copy(InputStream source, File destination) throws IOException {
        try (OutputStream output = new FileOutputStream(destination)) {
            copy(source, output);
        }
    }

    private static void copy(InputStream source, OutputStream destination) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = source.read(buffer)) != -1) {
            destination.write(buffer, 0, read);
        }
        destination.flush();
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        // noinspection ResultOfMethodCallIgnored
        target.delete();
    }
}
