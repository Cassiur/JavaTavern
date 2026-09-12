package com.zcz.javatavern.data;

/**
 * 备份文件不可用（格式不符、版本过新、归档缺件等）时抛出，
 * 消息直接面向用户展示，所以用中文写清楚"哪里不对、该怎么办"。
 */
public final class BackupException extends Exception {
    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
