package com.example.usermanagement.service;

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
}
