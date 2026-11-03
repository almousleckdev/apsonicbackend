package com.apsonic.backend.shared.upload;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Shrinks uploaded JPEG and PNG images so pages load quickly on slow connections: the longest side is capped at
 * {@value #MAX_DIMENSION}px (never enlarged), photos are re-encoded as JPEG, and EXIF data (including GPS location)
 * is dropped. EXIF orientation is applied first so phone photos are not left sideways.
 *
 * <p>Transparent PNGs stay PNG. Opaque PNGs become JPEG only when that is at least twice as small, so flat
 * graphics and logos (which PNG compresses well and JPEG blurs) keep their format.
 *
 * <p>Optimisation is best-effort: anything undecodable, oversized or not made smaller yields {@link Optional#empty()}
 * and the caller stores the original bytes unchanged.
 */
@Slf4j
@Component
public class ImageOptimizer {

    static final int MAX_DIMENSION = 1920;
    /** Decoding needs about 4 bytes per pixel; above this a single upload could exhaust the heap. */
    static final long MAX_PIXELS = 50_000_000L;
    private static final float JPEG_QUALITY = 0.82f;
    private static final float OPAQUE_PNG_JPEG_QUALITY = 0.85f;

    public record Optimized(byte[] bytes, UploadFileType type) {
    }

    public Optional<Optimized> optimize(byte[] original, UploadFileType type) {
        if (type != UploadFileType.JPEG && type != UploadFileType.PNG) {
            return Optional.empty();
        }
        try {
            int[] size = dimensions(original);
            if (size == null || (long) size[0] * size[1] > MAX_PIXELS) {
                return Optional.empty();
            }
            BufferedImage image = decodeAndScale(original, Math.max(size[0], size[1]) > MAX_DIMENSION);
            Optimized result = type == UploadFileType.JPEG ? fromJpeg(image) : fromPng(image);
            if (result.bytes().length >= original.length) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (IOException | RuntimeException e) {
            log.warn("Image optimisation skipped, storing original: {}", e.toString());
            return Optional.empty();
        }
    }

    private static Optimized fromJpeg(BufferedImage image) throws IOException {
        return new Optimized(encodeJpeg(image, JPEG_QUALITY), UploadFileType.JPEG);
    }

    private static Optimized fromPng(BufferedImage image) throws IOException {
        byte[] png = encodePng(image);
        if (!hasTransparency(image)) {
            byte[] jpeg = encodeJpeg(image, OPAQUE_PNG_JPEG_QUALITY);
            if (jpeg.length < png.length / 2) {
                return new Optimized(jpeg, UploadFileType.JPEG);
            }
        }
        return new Optimized(png, UploadFileType.PNG);
    }

    /** Reads the size from the header only, without decoding the pixels. Null if no reader recognises the data. */
    private static int[] dimensions(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage decodeAndScale(byte[] bytes, boolean shrink) throws IOException {
        Thumbnails.Builder<? extends java.io.InputStream> builder = Thumbnails.of(new ByteArrayInputStream(bytes));
        if (shrink) {
            builder.size(MAX_DIMENSION, MAX_DIMENSION);
        } else {
            builder.scale(1.0);
        }
        return builder.asBufferedImage();
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(image).scale(1.0).outputFormat("jpg").outputQuality(quality).toOutputStream(out);
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return out.toByteArray();
    }

    private static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }
}
