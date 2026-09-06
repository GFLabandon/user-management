package io.github.gflabandon.counselor.controller;
import io.github.gflabandon.counselor.mapper.CounselorMapper;
import io.github.gflabandon.counselor.service.FileStorageService;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class PhotoController {
    private final FileStorageService storage;
    private final CounselorMapper counselors;
    public PhotoController(FileStorageService storage, CounselorMapper counselors) { this.storage = storage; this.counselors = counselors; }
    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        var file = storage.resolveImage(filename);
        if (file == null || counselors.photoReferences("/uploads/" + filename) == 0) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(file));
    }
}
