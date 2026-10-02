package io.github.gflabandon.counselor.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final Path uploadDirectory;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public String storeImage(MultipartFile file) throws IOException {
        final int maxBytes = 5 * 1024 * 1024;
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) throw new UploadValidationException("图片不能为空，且不能超过 5 MB。");
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (originalName.contains("..") || !ALLOWED_EXTENSIONS.contains(extension) || file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) throw new UploadValidationException("仅支持 JPG/PNG 图片，请重新选择。");
        byte[] bytes;
        try (InputStream input = file.getInputStream()) { bytes = input.readNBytes(maxBytes + 1); }
        if (bytes.length > maxBytes) throw new UploadValidationException("图片不能超过 5 MB。");
        java.awt.image.BufferedImage decoded;
        String format;
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new UploadValidationException("图片内容无法解码。");
            var reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String expected = extension.equals("png") ? "png" : "jpeg";
                if (!format.equals(expected) || !file.getContentType().equals("image/" + expected))
                    throw new UploadValidationException("图片内容与扩展名或类型不一致。");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 2048 || height > 2048 || (long) width * height > 4_000_000)
                    throw new UploadValidationException("图片边长不能超过 2048 像素，总像素不能超过 400 万。");
                decoded = reader.read(0);
                if (decoded == null) throw new UploadValidationException("图片内容无法解码。");
            } finally { reader.dispose(); }
        } catch (UploadValidationException invalid) {
            throw invalid;
        } catch (IOException invalid) {
            throw new UploadValidationException("图片内容无法解码，请重新选择有效的 JPG/PNG 图片。");
        }
        requireUploadDirectory();
        Files.createDirectories(uploadDirectory);
        String storedName = UUID.randomUUID() + (format.equals("png") ? ".png" : ".jpg");
        Path destination = uploadDirectory.resolve(storedName);
        boolean created = false;
        try (var output = openImageOutput(destination)) {
            created = true;
            if (!javax.imageio.ImageIO.write(decoded, format, output)) throw new IOException("图片无法重新编码。");
        } catch (IOException failure) {
            if (created) {
                try { removeImage(destination); }
                catch (IOException cleanup) {
                    log.warn("image_cleanup file={} result=failed trigger=partial_write", storedName);
                }
            }
            throw failure;
        }
        return "/uploads/" + storedName;
    }

    public Path resolveImage(String name) {
        if (Files.isSymbolicLink(uploadDirectory) || !name.matches("[A-Za-z0-9_-]+\\.(?:png|jpg|jpeg)")) return null;
        Path candidate = uploadDirectory.resolve(name);
        return Files.isRegularFile(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS) ? candidate : null;
    }

    static boolean validStoredPath(String path) {
        return path != null && path.length() <= 200 && path.matches("/uploads/[A-Za-z0-9_-]+\\.(?:png|jpg|jpeg)");
    }

    boolean isStoredImage(String path) {
        return validStoredPath(path) && !Files.isSymbolicLink(uploadDirectory) && resolveImage(path.substring(9)) != null;
    }

    enum DeleteResult { DELETED, ABSENT, INVALID, FAILED }

    // Only ImageLifecycle calls this after checking committed references while holding its gate.
    DeleteResult deleteUnreferenced(String storedPath) {
        if (!validStoredPath(storedPath) || Files.isSymbolicLink(uploadDirectory)) return DeleteResult.INVALID;
        Path file = uploadDirectory.resolve(storedPath.substring(9));
        if (Files.notExists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return DeleteResult.ABSENT;
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return DeleteResult.INVALID;
        try { return removeImage(file) ? DeleteResult.DELETED : DeleteResult.ABSENT; }
        catch (IOException failure) { return DeleteResult.FAILED; }
    }

    private void requireUploadDirectory() throws IOException {
        if (Files.isSymbolicLink(uploadDirectory)) throw new IOException("Upload directory cannot be a symbolic link");
    }

    java.io.OutputStream openImageOutput(Path destination) throws IOException {
        return Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    boolean removeImage(Path file) throws IOException { return Files.deleteIfExists(file); }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
