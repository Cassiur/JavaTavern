package com.zcz.javatavern.data;

/**
 * 备份归档中被本应用管理的文件的路径映射。
 *
 * <p>角色头像与消息图片在数据库里存的是**本机绝对路径**
 * （{@code /data/user/0/com.zcz.javatavern/files/avatars/xxx.png}）。
 * 直接写进备份就无法跨设备恢复，因此导出时统一转成相对形式
 * （{@code avatars/xxx.png}），导入时再按本机的 files 目录还原。
 *
 * <p>不属于受管目录的值（例如内置角色薇拉的 {@code res:avatar_vera}）原样保留，
 * 这样备份里不会出现无法解释的路径。
 *
 * <p>纯字符串逻辑，无 Android 依赖，可直接 JVM 单测。
 */
public final class BackupPaths {
    /** {@code getFilesDir()/avatars} —— 角色卡导出的立绘。 */
    public static final String AVATAR_DIR = "avatars";
    /** {@code getFilesDir()/message_images} —— 聊天里发送的图片。 */
    public static final String ATTACHMENT_DIR = "message_images";
    /** 归档内文件条目的前缀。 */
    public static final String ARCHIVE_PREFIX = "files/";

    private BackupPaths() {
    }

    /**
     * 绝对路径 → 备份内的相对路径。不属于受管目录时原样返回，null/空串归一为 ""。
     */
    public static String toRelative(String path, String filesDirPath) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (filesDirPath == null || filesDirPath.isEmpty()) {
            return path;
        }
        String prefix = filesDirPath.endsWith("/") ? filesDirPath : filesDirPath + "/";
        if (!path.startsWith(prefix)) {
            return path;
        }
        String relative = path.substring(prefix.length());
        return isManaged(relative) ? relative : path;
    }

    /**
     * 相对路径 → 本机绝对路径。非受管值原样返回，null/空串归一为 ""。
     */
    public static String toAbsolute(String stored, String filesDirPath) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        if (!isManaged(stored) || filesDirPath == null || filesDirPath.isEmpty()) {
            return stored;
        }
        String prefix = filesDirPath.endsWith("/") ? filesDirPath : filesDirPath + "/";
        return prefix + stored;
    }

    /**
     * 是否是备份内受管文件的相对路径。
     *
     * <p>会拒绝 {@code ..}、绝对路径与反斜杠，避免构造出 {@code avatars/../../x}
     * 这类越界路径（导入归档是外部输入，必须按不可信数据处理）。
     */
    public static boolean isManaged(String stored) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        if (stored.startsWith("/") || stored.startsWith("\\")
                || stored.contains("..") || stored.contains("\\")) {
            return false;
        }
        return stored.startsWith(AVATAR_DIR + "/") || stored.startsWith(ATTACHMENT_DIR + "/");
    }

    /** 归档条目名（{@code files/avatars/xxx.png}）→ 相对路径（{@code avatars/xxx.png}）。 */
    public static String fromArchiveEntry(String entryName) {
        if (entryName == null || !entryName.startsWith(ARCHIVE_PREFIX)) {
            return "";
        }
        String relative = entryName.substring(ARCHIVE_PREFIX.length());
        return isManaged(relative) ? relative : "";
    }

    /** 相对路径 → 归档条目名。 */
    public static String toArchiveEntry(String relativePath) {
        return isManaged(relativePath) ? ARCHIVE_PREFIX + relativePath : "";
    }
}
