package io.github.gflabandon.counselor.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import io.github.gflabandon.counselor.TestImages;
import io.github.gflabandon.counselor.mapper.CounselorMapper;
import io.github.gflabandon.counselor.web.CounselorForm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:image-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(ImageLifecycleTests.FilesConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ImageLifecycleTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.upload-dir", () -> uploads.toString()); }
    @Autowired FailingStorage storage;
    @Autowired CounselorService counselors;
    @Autowired DepartmentService departments;
    @Autowired ImageLifecycle lifecycle;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean CounselorMapper mapper;

    @TestConfiguration static class FilesConfig {
        @Bean @Primary FailingStorage testStorage() { return new FailingStorage(uploads.toString()); }
    }
    static class FailingStorage extends FileStorageService {
        volatile boolean failWrite;
        volatile boolean failPartialDelete;
        volatile Path failDelete;
        FailingStorage(String path) { super(path); }
        @Override OutputStream openImageOutput(Path path) throws IOException {
            OutputStream output = super.openImageOutput(path);
            if (!failWrite) return output;
            return new FilterOutputStream(output) {
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    out.write(bytes, offset, Math.min(4, length));
                    throw new IOException("private-filesystem-detail");
                }
            };
        }
        @Override boolean removeImage(Path path) throws IOException {
            if (failPartialDelete || path.equals(failDelete)) throw new IOException("private-filesystem-detail");
            return super.removeImage(path);
        }
    }
    @AfterEach void resetFailures() { storage.failWrite = false; storage.failPartialDelete = false; storage.failDelete = null; }
    private String image() throws Exception { return storage.storeImage(new MockMultipartFile("photo", "photo.png", "image/png", TestImages.png())); }
    private Path path(String image) { return uploads.resolve(image.substring(9)); }
    private CounselorForm form() {
        var f = new CounselorForm(); f.setEmployeeNo("IMG-" + UUID.randomUUID().toString().substring(0, 8));
        f.setName("图片故障测试"); f.setDepartmentId(departments.all().get(0).getId()); return f;
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test coordination timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test void partialWriteFailureRemovesOnlyItsPartialFileAndLogsUnremovableFile(CapturedOutput output) throws Exception {
        String existing = image();
        long before;
        try (var files = Files.list(uploads)) { before = files.count(); }
        storage.failWrite = true;
        assertThatThrownBy(this::image).isInstanceOf(IOException.class);
        try (var files = Files.list(uploads)) { assertThat(files.count()).isEqualTo(before); }
        storage.failPartialDelete = true;
        assertThatThrownBy(this::image).isInstanceOf(IOException.class);
        try (var files = Files.list(uploads)) { assertThat(files.count()).isEqualTo(before + 1); }
        assertThat(path(existing)).exists();
        assertThat(output).contains("result=failed trigger=partial_write").doesNotContain("private-filesystem-detail");
    }

    @Test void auditFailureRollsBackPhotoReferenceAndCleansNewImageAfterRollback() throws Exception {
        String old = image(), fresh = image(); var form = form();
        int id = counselors.create(form, old, "admin");
        assertThatThrownBy(() -> counselors.update(id, form, fresh, null)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(counselors.get(id).getPhotoPath()).isEqualTo(old);
        assertThat(counselors.get(id).getVersion()).isZero();
        assertThat(path(old)).exists(); assertThat(path(fresh)).doesNotExist();
    }

    @Test void outerTransactionKeepsOldImageUntilCommit() throws Exception {
        String old = image(), fresh = image(); var form = form(); int id = counselors.create(form, old, "admin");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            counselors.update(id, form, fresh, "admin");
            assertThat(path(old)).exists(); assertThat(path(fresh)).exists();
        });
        assertThat(path(old)).doesNotExist(); assertThat(path(fresh)).exists();
        assertThat(counselors.get(id).getPhotoPath()).isEqualTo(fresh);
    }

    @Test void sharedHistoricalImageSurvivesUntilLastReferenceIsReplaced() throws Exception {
        String shared = image(), firstNew = image(), secondNew = image(); var a = form(); var b = form();
        int first = counselors.create(a, shared, "admin"), second = counselors.create(b, shared, "admin");
        counselors.update(first, a, firstNew, "admin");
        assertThat(path(shared)).exists(); assertThat(mapper.photoReferences(shared)).isEqualTo(1);
        counselors.update(second, b, secondNew, "admin");
        assertThat(path(shared)).doesNotExist(); assertThat(path(firstNew)).exists(); assertThat(path(secondNew)).exists();
    }

    @Test void legacyMigrationReferenceAlsoProtectsOldImage() throws Exception {
        String old = image(), fresh = image(); var f = form(); int id = counselors.create(f, old, "admin");
        jdbc.update("INSERT INTO users(username,note,dept_id,photo_path) VALUES (?,?,?,?)", "legacy-" + id, "synthetic", f.getDepartmentId(), old);
        counselors.update(id, f, fresh, "admin");
        assertThat(path(old)).exists(); assertThat(mapper.photoReferences(old)).isZero();
        assertThat(mapper.imageReferencesForCleanup(old)).isEqualTo(1);
    }

    @Test void deleteFailureDoesNotUndoCommittedSaveOrExposeFilesystemDetails(CapturedOutput output) throws Exception {
        String old = image(), fresh = image(); var f = form(); int id = counselors.create(f, old, "admin");
        storage.failDelete = path(old);
        counselors.update(id, f, fresh, "admin");
        assertThat(counselors.get(id).getPhotoPath()).isEqualTo(fresh);
        assertThat(counselors.get(id).getVersion()).isEqualTo(1);
        assertThat(path(old)).exists(); assertThat(path(fresh)).exists();
        assertThat(output).contains("file=" + old.substring(9) + " result=failed trigger=commit").doesNotContain("private-filesystem-detail");
    }

    @Test void failedReferenceCheckRetainsFileAndCommittedReference(CapturedOutput output) throws Exception {
        String old = image(), fresh = image(); var f = form(); int id = counselors.create(f, old, "admin");
        doThrow(new DataAccessResourceFailureException("private-database-detail")).when(mapper).imageReferencesForCleanup(old);
        counselors.update(id, f, fresh, "admin");
        assertThat(counselors.get(id).getPhotoPath()).isEqualTo(fresh);
        assertThat(path(old)).exists(); assertThat(path(fresh)).exists();
        assertThat(output).contains("result=reference_check_failed").doesNotContain("private-database-detail");
    }

    @Test void unknownTransactionOutcomePreservesNewImageForOfflineReview(CapturedOutput output) throws Exception {
        String fresh = image();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { lifecycle.change(fresh).afterCompletion(TransactionSynchronization.STATUS_UNKNOWN); }
        finally { TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(false); }
        lifecycle.retainForReview(fresh);
        assertThat(path(fresh)).exists();
        assertThat(output).contains("result=transaction_unknown", "result=retained_for_review");
        // The completion callback also releases the gate.
        counselors.create(form(), fresh, "admin");
    }

    @Test void deletionWinningRaceRejectsLaterAssignmentOfMissingFile() throws Exception {
        String old = image(), fresh = image(); var f = form(); int id = counselors.create(f, old, "admin");
        CountDownLatch updated = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                counselors.update(id, f, fresh, "admin"); updated.countDown(); await(release);
            }));
            await(updated);
            var late = pool.submit(() -> { attempting.countDown(); return counselors.create(form(), old, "admin"); });
            await(attempting);
            assertThatThrownBy(() -> late.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(path(old)).exists(); release.countDown(); writer.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> late.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            assertThat(path(old)).doesNotExist(); assertThat(mapper.photoReferences(old)).isZero();
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void referenceWinningRaceProtectsImageFromConcurrentCleanup() throws Exception {
        String old = image(), fresh = image(); var f = form(); int id = counselors.create(f, old, "admin");
        CountDownLatch assigned = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var owner = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                counselors.create(form(), old, "admin"); assigned.countDown(); await(release);
            }));
            await(assigned);
            var edit = pool.submit(() -> { attempting.countDown(); return counselors.update(id, f, fresh, "admin"); });
            await(attempting);
            assertThatThrownBy(() -> edit.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); owner.get(5, TimeUnit.SECONDS); edit.get(5, TimeUnit.SECONDS);
            assertThat(path(old)).exists(); assertThat(mapper.photoReferences(old)).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
