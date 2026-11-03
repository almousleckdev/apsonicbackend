package com.apsonic.backend.shared.controller;

import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.upload.ImageOptimizer;
import com.apsonic.backend.shared.upload.UploadContentValidator;
import com.apsonic.backend.shared.upload.UploadFileType;
import com.apsonic.backend.shared.upload.VideoOptimizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
@RequiredArgsConstructor
@Tag(name = "Media Upload", description = "Secure endpoints for uploading images and videos")
public class MediaUploadController {

    private final UploadContentValidator contentValidator;
    private final ImageOptimizer imageOptimizer;
    private final VideoOptimizer videoOptimizer;
    private final Path rootUploadDir = Paths.get("uploads").toAbsolutePath().normalize();

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootUploadDir);
        } catch (IOException e) {
            log.error("Could not create uploads directory", e);
            throw new IllegalStateException("Could not initialize upload storage directory", e);
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload image or video file from computer with strict security validation")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Selected file is empty", 400, "/api/v1/admin/media/upload"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error("File exceeds maximum allowed size of 50MB", 413, "/api/v1/admin/media/upload"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "uploaded-file";
        }

        UploadContentValidator.Result validation;
        try (InputStream in = file.getInputStream()) {
            validation = contentValidator.validate(in, originalFilename, file.getContentType());
        }
        if (!validation.accepted()) {
            log.warn("Rejected upload '{}' (declared {}): {}", sanitizeForLog(originalFilename),
                    sanitizeForLog(file.getContentType()), validation.reason());
            return ResponseEntity.badRequest().body(ApiResponse.error(validation.reason(), 400, "/api/v1/admin/media/upload"));
        }

        // Photos are shrunk before they are stored so pages stay fast on slow connections. The result can be a different
        // type than the upload (an opaque PNG photo becomes a JPEG), so the extension is chosen after optimising.
        UploadFileType storedType = validation.type();
        byte[] imageBytes = null;
        if (storedType == UploadFileType.JPEG || storedType == UploadFileType.PNG) {
            var optimized = imageOptimizer.optimize(file.getBytes(), storedType);
            if (optimized.isPresent()) {
                imageBytes = optimized.get().bytes();
                storedType = optimized.get().type();
            }
        }

        // Extension comes from the detected type, never from the client-supplied name.
        String newFilename = UUID.randomUUID() + "." + storedType.canonicalExtension();
        Path targetPath = this.rootUploadDir.resolve(newFilename).normalize();

        // Path traversal guard
        if (!targetPath.startsWith(this.rootUploadDir)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Path traversal detected", 400, "/api/v1/admin/media/upload"));
        }

        if (imageBytes != null) {
            Files.write(targetPath, imageBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        } else {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath);
            }
        }
        if (storedType == UploadFileType.MP4) {
            // Re-encoded in the background; the file at this URL is swapped for a smaller one when ready.
            videoOptimizer.optimizeAsync(targetPath);
        }
        String fileUrl = "/uploads/" + newFilename;
        log.info("Securely uploaded file: {} -> {}", originalFilename, fileUrl);

        return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", Map.of(
                "url", fileUrl,
                "filename", originalFilename
        )));
    }

    private static String sanitizeForLog(String value) {
        return value == null ? "null" : value.replaceAll("[\\r\\n\\t]", "_");
    }
}
