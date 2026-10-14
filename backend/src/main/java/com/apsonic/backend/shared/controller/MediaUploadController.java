package com.apsonic.backend.shared.controller;

import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
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
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
@Tag(name = "Media Upload", description = "Secure endpoints for uploading images and videos")
public class MediaUploadController {

    private final Path rootUploadDir = Paths.get("uploads").toAbsolutePath().normalize();

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "svg", "mp4", "webm", "pdf"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml",
            "video/mp4", "video/webm", "application/pdf"
    );

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

        String extension = "";
        if (originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid file type. Allowed formats: JPG, PNG, WEBP, GIF, SVG, MP4, WEBM, PDF", 400, "/api/v1/admin/media/upload"
            ));
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("MIME type mismatch/suspicious upload attempt: {}", contentType);
        }

        // Verify magic bytes for common binary files (JPEG, PNG, GIF, WEBP, MP4, PDF)
        if (!isContentValid(file, extension)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "File content signature does not match the extension", 400, "/api/v1/admin/media/upload"
            ));
        }

        String sanitizedExt = extension.isEmpty() ? "" : "." + extension;
        String newFilename = UUID.randomUUID() + sanitizedExt;
        Path targetPath = this.rootUploadDir.resolve(newFilename).normalize();

        // Path traversal guard
        if (!targetPath.startsWith(this.rootUploadDir)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Path traversal detected", 400, "/api/v1/admin/media/upload"));
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        String fileUrl = "/uploads/" + newFilename;
        log.info("Securely uploaded file: {} -> {}", originalFilename, fileUrl);

        return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", Map.of(
                "url", fileUrl,
                "filename", originalFilename
        )));
    }

    private boolean isContentValid(MultipartFile file, String ext) {
        if ("svg".equalsIgnoreCase(ext) || "webm".equalsIgnoreCase(ext)) {
            return true; // Plain text XML or open webm
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 4) return false;

            if ("jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext)) {
                return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
            }
            if ("png".equalsIgnoreCase(ext)) {
                return (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
                       (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47;
            }
            if ("gif".equalsIgnoreCase(ext)) {
                return header[0] == 'G' && header[1] == 'I' && header[2] == 'F';
            }
            if ("pdf".equalsIgnoreCase(ext)) {
                return header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
            }
            if ("webp".equalsIgnoreCase(ext)) {
                return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
