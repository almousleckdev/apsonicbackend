package com.apsonic.backend.shared.upload;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer();

    /** Photo-like content: smooth gradients plus noise, so it is large when stored losslessly. */
    private static BufferedImage photoLike(int w, int h, int type) {
        BufferedImage img = new BufferedImage(w, h, type);
        Random rnd = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 255 / w + rnd.nextInt(24)) & 0xFF;
                int g = (y * 255 / h + rnd.nextInt(24)) & 0xFF;
                int b = ((x + y) * 255 / (w + h) + rnd.nextInt(24)) & 0xFF;
                img.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void largeJpegIsScaledDownAndSmaller() throws Exception {
        byte[] original = encode(photoLike(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg");

        Optional<ImageOptimizer.Optimized> result = optimizer.optimize(original, UploadFileType.JPEG);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(UploadFileType.JPEG);
        assertThat(result.get().bytes().length).isLessThan(original.length);
        BufferedImage out = decode(result.get().bytes());
        assertThat(Math.max(out.getWidth(), out.getHeight())).isEqualTo(1920);
        assertThat(out.getWidth() * 3).isEqualTo(out.getHeight() * 4); // aspect ratio kept (4:3)
    }

    @Test
    void smallImageIsNeverUpscaled() throws Exception {
        byte[] original = encode(photoLike(800, 600, BufferedImage.TYPE_INT_RGB), "png");

        Optional<ImageOptimizer.Optimized> result = optimizer.optimize(original, UploadFileType.PNG);

        // Either left alone (empty) or re-encoded at the same size, but never enlarged.
        result.ifPresent(r -> {
            try {
                BufferedImage out = decode(r.bytes());
                assertThat(out.getWidth()).isLessThanOrEqualTo(800);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void opaquePhotoPngBecomesJpeg() throws Exception {
        byte[] original = encode(photoLike(2400, 1600, BufferedImage.TYPE_INT_RGB), "png");

        Optional<ImageOptimizer.Optimized> result = optimizer.optimize(original, UploadFileType.PNG);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(UploadFileType.JPEG);
        assertThat(result.get().bytes().length).isLessThan(original.length / 3);
    }

    @Test
    void transparentPngStaysPngAndKeepsTransparency() throws Exception {
        BufferedImage img = photoLike(2400, 1600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, 600, 1600); // a transparent band
        g.dispose();
        byte[] original = encode(img, "png");

        Optional<ImageOptimizer.Optimized> result = optimizer.optimize(original, UploadFileType.PNG);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(UploadFileType.PNG);
        BufferedImage out = decode(result.get().bytes());
        assertThat(out.getWidth()).isEqualTo(1920);
        assertThat(new Color(out.getRGB(10, 10), true).getAlpha()).isZero();
    }

    @Test
    void flatGraphicStaysPng() throws Exception {
        BufferedImage img = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 2400, 1200);
        g.setColor(Color.RED);
        g.fillRect(100, 100, 800, 400);
        g.dispose();
        byte[] original = encode(img, "png");

        Optional<ImageOptimizer.Optimized> result = optimizer.optimize(original, UploadFileType.PNG);

        // Flat colours compress far better as PNG, so it must not be turned into a JPEG.
        result.ifPresent(r -> assertThat(r.type()).isEqualTo(UploadFileType.PNG));
    }

    @Test
    void undecodableContentIsLeftAlone() {
        byte[] fake = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

        assertThat(optimizer.optimize(fake, UploadFileType.PNG)).isEmpty();
        assertThat(optimizer.optimize(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}, UploadFileType.JPEG)).isEmpty();
    }

    @Test
    void imageWithTooManyPixelsIsLeftAlone() throws Exception {
        // 9000 x 9000 = 81 MP, over the decode cap. Blank, so the encoded file is tiny.
        byte[] original = encode(new BufferedImage(9000, 9000, BufferedImage.TYPE_BYTE_GRAY), "png");

        assertThat(optimizer.optimize(original, UploadFileType.PNG)).isEmpty();
    }

    @Test
    void otherTypesAreNotTouched() {
        assertThat(optimizer.optimize(new byte[]{1, 2, 3}, UploadFileType.GIF)).isEmpty();
        assertThat(optimizer.optimize(new byte[]{1, 2, 3}, UploadFileType.WEBP)).isEmpty();
        assertThat(optimizer.optimize(new byte[]{1, 2, 3}, UploadFileType.PDF)).isEmpty();
    }
}
