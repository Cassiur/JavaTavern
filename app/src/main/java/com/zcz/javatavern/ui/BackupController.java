package com.zcz.javatavern.ui;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zcz.javatavern.R;
import com.zcz.javatavern.data.BackupException;
import com.zcz.javatavern.data.BackupRepository;
import com.zcz.javatavern.util.AppExecutors;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 设置页「备份与恢复」的交互。整套流程放在这里，避免 {@code SettingsActivity} 继续膨胀。
 *
 * <p>导出走系统的「新建文档」，恢复走系统的「打开文档」，所以应用不需要任何存储权限，
 * 用户自己决定把备份放到哪（本地、网盘、聊天软件都行）。
 */
public final class BackupController {
    public interface Listener {
        boolean isHostActive();

        /** 恢复完成后重读配置——备份里带了连接与采样设置。 */
        void onRestored();
    }

    private static final String[] IMPORT_MIME_TYPES = {
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
    };

    private final AppCompatActivity activity;
    private final BackupRepository repository;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> exportLauncher;
    private final ActivityResultLauncher<String[]> importLauncher;
    /** 导出/恢复进行中：忽略重复点击，避免两次覆盖写同一个文件。 */
    private boolean busy;

    public BackupController(
            AppCompatActivity activity,
            BackupRepository repository,
            Listener listener
    ) {
        this.activity = activity;
        this.repository = repository;
        this.listener = listener;
        this.exportLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                this::onExportTargetChosen
        );
        this.importLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onImportSourceChosen
        );
    }

    /** 让用户选保存位置，再写出备份。 */
    public void exportBackup() {
        if (busy) {
            return;
        }
        exportLauncher.launch(defaultFileName());
    }

    /** 让用户选备份文件；确认后整体覆盖本机数据。 */
    public void restoreBackup() {
        if (busy) {
            return;
        }
        importLauncher.launch(IMPORT_MIME_TYPES);
    }

    private String defaultFileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        return "JavaTavern-backup-" + stamp + ".zip";
    }

    private void onExportTargetChosen(Uri destination) {
        if (destination == null) {
            return;
        }
        busy = true;
        toast(R.string.backup_exporting);
        AppExecutors.get().diskIo().execute(() -> {
            BackupRepository.Summary summary;
            try {
                summary = repository.export(destination);
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    busy = false;
                    showError(R.string.backup_export_failed, exception);
                });
                return;
            }
            mainHandler.post(() -> {
                busy = false;
                if (!listener.isHostActive()) {
                    return;
                }
                toast(activity.getString(
                        R.string.backup_export_done,
                        summary.getCharacterCount(),
                        summary.getMessageCount(),
                        summary.getFileCount()
                ));
            });
        });
    }
    private void onImportSourceChosen(Uri source) {
        if (source == null) {
            return;
        }
        // 恢复会清空现有数据，先让用户明确确认一次。
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.backup_restore_confirm_title)
                .setMessage(R.string.backup_restore_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.backup_restore_confirm_action,
                        (dialog, which) -> performRestore(source))
                .show();
    }

    private void performRestore(Uri source) {
        busy = true;
        toast(R.string.backup_restoring);
        AppExecutors.get().diskIo().execute(() -> {
            BackupRepository.Summary summary;
            try {
                summary = repository.restore(source);
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    busy = false;
                    showError(R.string.backup_restore_failed, exception);
                });
                return;
            }
            mainHandler.post(() -> {
                busy = false;
                if (!listener.isHostActive()) {
                    return;
                }
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.backup_restore_done_title)
                        .setMessage(activity.getString(
                                R.string.backup_restore_done,
                                summary.getCharacterCount(),
                                summary.getMessageCount()
                        ))
                        .setPositiveButton(R.string.confirm, (dialog, which) ->
                                listener.onRestored())
                        .show();
            });
        });
    }

    private void toast(int stringRes) {
        toast(activity.getString(stringRes));
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private void showError(int titleRes, Exception exception) {
        if (!listener.isHostActive()) {
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(titleRes)
                .setMessage(describe(exception))
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    /** 备份流程里的异常消息本来就是写给用户看的，直接展示；其他异常兜底成通用文案。 */
    private String describe(Exception exception) {
        if (exception instanceof BackupException) {
            String message = exception.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
        }
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? activity.getString(R.string.backup_unknown_error)
                : message;
    }
}
