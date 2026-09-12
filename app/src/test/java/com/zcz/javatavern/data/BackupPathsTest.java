package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 备份里的文件路径映射。这一层决定备份能不能跨设备恢复，也是归档这种外部输入
 * 唯一可能的越界入口，所以把边界情况都钉住。
 */
public class BackupPathsTest {
    private static final String FILES_DIR = "/data/user/0/com.zcz.javatavern/files";

    @Test
    public void absoluteManagedPathBecomesRelative() {
        assertEquals(
                "avatars/abc.png",
                BackupPaths.toRelative(FILES_DIR + "/avatars/abc.png", FILES_DIR));
        assertEquals(
                "message_images/def.jpg",
                BackupPaths.toRelative(FILES_DIR + "/message_images/def.jpg", FILES_DIR));
    }

    @Test
    public void nonManagedValuesAreLeftAlone() {
        // 内置角色薇拉的头像是资源引用，不是文件，不该被当成相对路径处理。
        assertEquals("res:avatar_vera", BackupPaths.toRelative("res:avatar_vera", FILES_DIR));
        assertEquals("res:avatar_vera", BackupPaths.toAbsolute("res:avatar_vera", FILES_DIR));
        // 受管目录之外的绝对路径同样保留原样。
        String outside = "/storage/emulated/0/DCIM/photo.jpg";
        assertEquals(outside, BackupPaths.toRelative(outside, FILES_DIR));
    }

    @Test
    public void relativePathBecomesAbsoluteAgain() {
        assertEquals(
                FILES_DIR + "/avatars/abc.png",
                BackupPaths.toAbsolute("avatars/abc.png", FILES_DIR));
    }

    @Test
    public void roundTripIsStable() {
        String absolute = FILES_DIR + "/message_images/xyz.jpg";
        String relative = BackupPaths.toRelative(absolute, FILES_DIR);
        assertEquals(absolute, BackupPaths.toAbsolute(relative, FILES_DIR));
    }

    @Test
    public void emptyAndNullBecomeEmptyString() {
        assertEquals("", BackupPaths.toRelative(null, FILES_DIR));
        assertEquals("", BackupPaths.toRelative("", FILES_DIR));
        assertEquals("", BackupPaths.toAbsolute(null, FILES_DIR));
        assertEquals("", BackupPaths.toAbsolute("", FILES_DIR));
    }

    @Test
    public void traversalAndAbsoluteFormsAreNotManaged() {
        assertFalse(BackupPaths.isManaged("avatars/../../shared_prefs/x.xml"));
        assertFalse(BackupPaths.isManaged("/avatars/abc.png"));
        assertFalse(BackupPaths.isManaged("avatars\\abc.png"));
        assertFalse(BackupPaths.isManaged(".."));
        assertFalse(BackupPaths.isManaged("databases/tavern.db"));
        assertFalse(BackupPaths.isManaged("avatars"));
        assertFalse(BackupPaths.isManaged(""));
        assertFalse(BackupPaths.isManaged(null));
    }

    @Test
    public void traversalPathIsNotRewrittenToAbsolute() {
        // 恶意条目名不应被拼成受管目录里的路径。
        assertEquals("../evil.png", BackupPaths.toAbsolute("../evil.png", FILES_DIR));
    }

    @Test
    public void archiveEntryNamesRoundTripAndRejectJunk() {
        assertEquals(
                "files/avatars/abc.png",
                BackupPaths.toArchiveEntry("avatars/abc.png"));
        assertEquals(
                "avatars/abc.png",
                BackupPaths.fromArchiveEntry("files/avatars/abc.png"));
        assertEquals("", BackupPaths.fromArchiveEntry("files/../../etc/passwd"));
        assertEquals("", BackupPaths.fromArchiveEntry("backup.json"));
        assertEquals("", BackupPaths.fromArchiveEntry("files/databases/tavern.db"));
        assertEquals("", BackupPaths.toArchiveEntry("../evil.png"));
    }
}
