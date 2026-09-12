package com.zcz.javatavern.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份归档里的清单（{@code manifest.json}）的读写与校验。
 *
 * <p>归档结构：
 * <pre>
 * manifest.json                     ← 本类读写的小体积元数据
 * tables.json                       ← 全部数据表，逐行流式读写
 * files/avatars/&lt;uuid&gt;.png         ← 角色立绘原始文件
 * files/message_images/&lt;uuid&gt;.jpg  ← 聊天图片原始文件
 * </pre>
 *
 * <p>数据表与清单分成两个条目，是为了让两端都能流式处理：写的时候一行一行写，
 * 读的时候一行一行解析，几十万条消息的备份也不会把整棵 JSON 树塞进内存。
 *
 * <p>清单里**不含 API Key**：密钥由 Android Keystore 加密且与本机绑定，
 * 放进可分享的备份里既不安全，也无法在别的设备解密。
 *
 * <p>只用 org.json（JVM 单测下有同名实现），不依赖任何 Android 类型。
 */
public final class BackupManifest {
    public static final String FORMAT = "javatavern.backup";
    public static final int FORMAT_VERSION = 1;
    /** 归档内元数据条目的固定名字。 */
    public static final String ENTRY_NAME = "manifest.json";
    /** 归档内数据表条目的固定名字。 */
    public static final String TABLES_ENTRY_NAME = "tables.json";

    private static final String KEY_FORMAT = "format";
    private static final String KEY_FORMAT_VERSION = "formatVersion";
    private static final String KEY_COUNTS = "counts";
    private static final String KEY_FILES = "files";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_MEMORIES = "memories";
    private static final String KEY_DRAFTS = "drafts";
    private static final String KEY_EXPORTED_AT = "exportedAt";
    private static final String KEY_APP_VERSION_NAME = "appVersionName";
    private static final String KEY_APP_VERSION_CODE = "appVersionCode";
    private static final String KEY_DATABASE_VERSION = "databaseVersion";

    private final long exportedAt;
    private final String appVersionName;
    private final int appVersionCode;
    private final int databaseVersion;
    private final Map<String, Integer> counts;
    private final List<String> files;
    private final JSONObject settings;
    private final JSONObject memories;
    private final JSONObject drafts;

    public BackupManifest(
            long exportedAt,
            String appVersionName,
            int appVersionCode,
            int databaseVersion,
            Map<String, Integer> counts,
            List<String> files,
            JSONObject settings,
            JSONObject memories,
            JSONObject drafts
    ) {
        this.exportedAt = exportedAt;
        this.appVersionName = appVersionName == null ? "" : appVersionName;
        this.appVersionCode = appVersionCode;
        this.databaseVersion = databaseVersion;
        this.counts = counts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(counts);
        this.files = files == null ? new ArrayList<>() : new ArrayList<>(files);
        this.settings = settings == null ? new JSONObject() : settings;
        this.memories = memories == null ? new JSONObject() : memories;
        this.drafts = drafts == null ? new JSONObject() : drafts;
    }

    /** 写出清单（元数据部分，体积很小）。 */
    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put(KEY_FORMAT, FORMAT);
            root.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
            root.put(KEY_EXPORTED_AT, exportedAt);
            root.put(KEY_APP_VERSION_NAME, appVersionName);
            root.put(KEY_APP_VERSION_CODE, appVersionCode);
            root.put(KEY_DATABASE_VERSION, databaseVersion);

            JSONObject countObject = new JSONObject();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                countObject.put(entry.getKey(), entry.getValue());
            }
            root.put(KEY_COUNTS, countObject);

            JSONArray fileArray = new JSONArray();
            for (String file : files) {
                fileArray.put(file);
            }
            root.put(KEY_FILES, fileArray);

            root.put(KEY_SETTINGS, settings);
            root.put(KEY_MEMORIES, memories);
            root.put(KEY_DRAFTS, drafts);
        } catch (JSONException exception) {
            throw new IllegalStateException("无法生成备份清单", exception);
        }
        return root;
    }

    /**
     * 解析并校验清单。校验不通过时抛 {@link BackupException}，消息可直接展示给用户。
     */
    public static BackupManifest fromJson(JSONObject root) throws BackupException {
        if (root == null) {
            throw new BackupException("备份文件是空的，或不是有效的 JSON");
        }
        if (!FORMAT.equals(root.optString(KEY_FORMAT))) {
            throw new BackupException("这不是 JavaTavern 的备份文件");
        }
        int formatVersion = root.optInt(KEY_FORMAT_VERSION, -1);
        if (formatVersion != FORMAT_VERSION) {
            throw new BackupException(
                    "备份格式版本为 " + formatVersion + "，本应用只支持 " + FORMAT_VERSION
                            + "。请升级应用后重试。");
        }

        JSONObject countObject = root.optJSONObject(KEY_COUNTS);
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (countObject != null) {
            for (java.util.Iterator<String> it = countObject.keys(); it.hasNext(); ) {
                String key = it.next();
                counts.put(key, countObject.optInt(key, 0));
            }
        }
        List<String> files = new ArrayList<>();
        JSONArray fileArray = root.optJSONArray(KEY_FILES);
        if (fileArray != null) {
            for (int index = 0; index < fileArray.length(); index++) {
                String file = fileArray.optString(index, "");
                if (BackupPaths.isManaged(file)) {
                    files.add(file);
                }
            }
        }
        return new BackupManifest(
                root.optLong(KEY_EXPORTED_AT, 0L),
                root.optString(KEY_APP_VERSION_NAME, ""),
                root.optInt(KEY_APP_VERSION_CODE, 0),
                root.optInt(KEY_DATABASE_VERSION, 0),
                counts,
                files,
                root.optJSONObject(KEY_SETTINGS),
                root.optJSONObject(KEY_MEMORIES),
                root.optJSONObject(KEY_DRAFTS)
        );
    }

    public long getExportedAt() {
        return exportedAt;
    }

    public String getAppVersionName() {
        return appVersionName;
    }

    public int getAppVersionCode() {
        return appVersionCode;
    }

    public int getDatabaseVersion() {
        return databaseVersion;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public List<String> getFiles() {
        return files;
    }

    public JSONObject getSettings() {
        return settings;
    }

    public JSONObject getMemories() {
        return memories;
    }

    public JSONObject getDrafts() {
        return drafts;
    }

    public int countOf(String table) {
        Integer value = counts.get(table);
        return value == null ? 0 : value;
    }
}
