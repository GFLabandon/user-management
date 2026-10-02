package io.github.gflabandon.counselor.service;

import java.util.concurrent.locks.ReentrantLock;
import io.github.gflabandon.counselor.mapper.CounselorMapper;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

/** Single-instance coordination: reference writes keep the gate through transaction completion. */
@Service
public class ImageLifecycle {
    private final ReentrantLock gate = new ReentrantLock(true);
    private final FileStorageService storage;
    private final CounselorMapper counselors;
    private final TransactionTemplate read;

    public ImageLifecycle(FileStorageService storage, CounselorMapper counselors, PlatformTransactionManager transactions) {
        this.storage = storage;
        this.counselors = counselors;
        read = new TransactionTemplate(transactions);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
    }

    public Change change(String newImage) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Image changes require a transaction");
        }
        gate.lock();
        var change = new Change(newImage);
        TransactionSynchronizationManager.registerSynchronization(change);
        if (newImage != null && !storage.isStoredImage(newImage)) {
            throw new BusinessException("图片文件不存在或路径无效，请重新上传。");
        }
        return change;
    }

    public final class Change implements TransactionSynchronization {
        private final String newImage;
        private String oldImage;
        private Change(String newImage) { this.newImage = newImage; }
        public void replaced(String oldImage) { this.oldImage = oldImage; }
        @Override public void afterCompletion(int status) {
            try {
                if (status == STATUS_COMMITTED && newImage != null && !newImage.equals(oldImage)) {
                    clean(oldImage, "commit");
                } else if (status == STATUS_ROLLED_BACK) {
                    clean(newImage, "rollback");
                } else if (status == STATUS_UNKNOWN) {
                    report(newImage, "transaction_unknown", "unknown");
                }
            } finally { gate.unlock(); }
        }
    }

    /** A failed service invocation may have failed before enlistment or have an unknown commit outcome. */
    public void retainForReview(String path) {
        if (storage.isStoredImage(path)) report(path, "retained_for_review", "failed_invocation");
    }

    private void clean(String path, String trigger) {
        if (path == null) return;
        try {
            if (!FileStorageService.validStoredPath(path)) { report(null, "invalid_path", trigger); return; }
            Integer references = read.execute(status -> counselors.imageReferencesForCleanup(path));
            if (references == null || references > 0) { report(path, "referenced", trigger); return; }
            report(path, storage.deleteUnreferenced(path).name().toLowerCase(java.util.Locale.ROOT), trigger);
        } catch (RuntimeException unavailable) {
            // Commit outcome is already known. Cleanup failure must not turn a committed save into a failed save.
            report(path, "reference_check_failed", trigger);
        }
    }

    private static void report(String path, String result, String trigger) {
        String file = FileStorageService.validStoredPath(path) ? path.substring(9) : "-";
        var log = LoggerFactory.getLogger(ImageLifecycle.class);
        if (result.equals("deleted") || result.equals("absent") || result.equals("referenced"))
            log.info("image_cleanup file={} result={} trigger={}", file, result, trigger);
        else log.warn("image_cleanup file={} result={} trigger={}", file, result, trigger);
    }
}
