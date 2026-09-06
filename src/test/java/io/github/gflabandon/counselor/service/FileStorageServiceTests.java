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
                "photo", "portrait.png", "image/png", io.github.gflabandon.counselor.TestImages.png());

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


    @Test void rejectsFakeImageAndMismatchedContent() {
        var service = new FileStorageService(temporaryDirectory.toString());
        assertThatThrownBy(() -> service.storeImage(new MockMultipartFile("photo", "empty-type.png", null, io.github.gflabandon.counselor.TestImages.png())))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> service.storeImage(new MockMultipartFile("photo", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("5 MB");
        assertThatThrownBy(() -> service.storeImage(new MockMultipartFile("photo", "fake.png", "image/png", "<script>bad</script>".getBytes())))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> service.storeImage(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", io.github.gflabandon.counselor.TestImages.png())))
                .isInstanceOf(java.io.IOException.class);
    }
    @Test void rejectsOversizedDimensionsAndStripsTrailingPayload() throws Exception {
        var service = new FileStorageService(temporaryDirectory.toString());
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2049, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        assertThatThrownBy(() -> service.storeImage(new MockMultipartFile("photo", "wide.png", "image/png", output.toByteArray())))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("2048");
        output.reset(); output.write(io.github.gflabandon.counselor.TestImages.png()); output.write("trailing-untrusted-payload".getBytes());
        String stored = service.storeImage(new MockMultipartFile("photo", "valid.png", "image/png", output.toByteArray()));
        assertThat(new String(Files.readAllBytes(service.resolveImage(Path.of(stored).getFileName().toString())), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("trailing-untrusted-payload");
    }
}
