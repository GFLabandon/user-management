package io.github.gflabandon.counselor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesImageWithGeneratedName() throws Exception {
        FileStorageService service = new FileStorageService(temporaryDirectory.toString());
        MockMultipartFile image = new MockMultipartFile(
                "photo", "portrait.png", "image/png", new byte[]{1, 2, 3});

        String storedPath = service.storeImage(image);

        assertThat(storedPath).startsWith("/uploads/").endsWith(".png");
        assertThat(Files.exists(temporaryDirectory.resolve(Path.of(storedPath).getFileName())))
                .isTrue();
    }

    @Test
    void rejectsNonImageExtension() {
        FileStorageService service = new FileStorageService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "photo", "notes.txt", "text/plain", "not an image".getBytes());

        assertThatThrownBy(() -> service.storeImage(file))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("JPG and PNG");
    }

    @Test
    void deletesOnlyTheNamedStoredFile() throws Exception {
        FileStorageService service = new FileStorageService(temporaryDirectory.toString());
        Path stored = temporaryDirectory.resolve("avatar.jpg");
        Files.write(stored, new byte[]{1});

        service.delete("/uploads/avatar.jpg");

        assertThat(stored).doesNotExist();
    }
    @Test
    void failedStreamCopyRemovesPartialUpload() throws Exception {
        FileStorageService service = new FileStorageService(temporaryDirectory.toString());
        MockMultipartFile broken = new MockMultipartFile("photo", "broken.png", "image/png", new byte[]{1}) {
            @Override public java.io.InputStream getInputStream() {
                return new java.io.InputStream() {
                    private int reads;
                    @Override public int read() throws java.io.IOException {
                        if (reads++ < 10) return 1;
                        throw new java.io.IOException("simulated interrupted upload");
                    }
                };
            }
        };
        assertThatThrownBy(() -> service.storeImage(broken)).isInstanceOf(java.io.IOException.class);
        try (var files = Files.list(temporaryDirectory)) { assertThat(files.toList()).isEmpty(); }
    }

}
