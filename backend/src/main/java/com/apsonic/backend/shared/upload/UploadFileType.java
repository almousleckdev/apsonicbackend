package com.apsonic.backend.shared.upload;

import java.util.Set;

/** File formats the CMS accepts. The canonical extension and MIME type come from here, never from the client. */
public enum UploadFileType {
    JPEG("jpg", "image/jpeg", Set.of("jpg", "jpeg")),
    PNG("png", "image/png", Set.of("png")),
    GIF("gif", "image/gif", Set.of("gif")),
    WEBP("webp", "image/webp", Set.of("webp")),
    MP4("mp4", "video/mp4", Set.of("mp4")),
    WEBM("webm", "video/webm", Set.of("webm")),
    PDF("pdf", "application/pdf", Set.of("pdf"));

    private final String canonicalExtension;
    private final String mimeType;
    private final Set<String> acceptedExtensions;

    UploadFileType(String canonicalExtension, String mimeType, Set<String> acceptedExtensions) {
        this.canonicalExtension = canonicalExtension;
        this.mimeType = mimeType;
        this.acceptedExtensions = acceptedExtensions;
    }

    public String canonicalExtension() {
        return canonicalExtension;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean acceptsExtension(String extension) {
        return extension != null && acceptedExtensions.contains(extension.toLowerCase(java.util.Locale.ROOT));
    }
}
