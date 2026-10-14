package com.apsonic.backend.shared.upload;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides what an uploaded file really is from its leading bytes and requires the filename extension and
 * the client-declared Content-Type to agree with that. Active-content formats (SVG, HTML, XML) are never
 * accepted because they can carry script and would execute on the site origin.
 */
@Component
public class UploadContentValidator {

    private static final int HEADER_BYTES = 16;

    public record Result(boolean accepted, UploadFileType type, String reason) {
        static Result ok(UploadFileType type) {
            return new Result(true, type, null);
        }

        static Result reject(String reason) {
            return new Result(false, null, reason);
        }
    }

    public Result validate(InputStream content, String originalFilename, String declaredContentType) throws IOException {
        byte[] header = content.readNBytes(HEADER_BYTES);
        Optional<UploadFileType> detected = detect(header);
        if (detected.isEmpty()) {
            return Result.reject("Unsupported or unrecognised file content. Allowed: JPG, PNG, GIF, WEBP, MP4, WEBM, PDF");
        }
        UploadFileType type = detected.get();

        String extension = extensionOf(originalFilename);
        if (!type.acceptsExtension(extension)) {
            return Result.reject("File extension does not match the file content");
        }
        if (declaredContentType == null || !type.mimeType().equalsIgnoreCase(baseMediaType(declaredContentType))) {
            return Result.reject("Declared content type does not match the file content");
        }
        return Result.ok(type);
    }

    static Optional<UploadFileType> detect(byte[] h) {
        if (startsWith(h, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(UploadFileType.JPEG);
        }
        if (startsWith(h, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(UploadFileType.PNG);
        }
        if (startsWithAscii(h, 0, "GIF87a") || startsWithAscii(h, 0, "GIF89a")) {
            return Optional.of(UploadFileType.GIF);
        }
        if (startsWithAscii(h, 0, "RIFF") && startsWithAscii(h, 8, "WEBP")) {
            return Optional.of(UploadFileType.WEBP);
        }
        if (startsWithAscii(h, 4, "ftyp")) {
            return Optional.of(UploadFileType.MP4);
        }
        if (startsWith(h, 0x1A, 0x45, 0xDF, 0xA3)) {
            return Optional.of(UploadFileType.WEBM);
        }
        if (startsWithAscii(h, 0, "%PDF-")) {
            return Optional.of(UploadFileType.PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] h, int... expected) {
        if (h.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((h[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] h, int offset, String ascii) {
        if (h.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (h[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseMediaType(String contentType) {
        int semi = contentType.indexOf(';');
        return (semi < 0 ? contentType : contentType.substring(0, semi)).trim();
    }
}
