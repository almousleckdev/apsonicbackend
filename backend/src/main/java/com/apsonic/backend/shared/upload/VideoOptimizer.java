package com.apsonic.backend.shared.upload;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Re-encodes uploaded MP4 videos to web-friendly H.264/AAC at up to 1280px wide, with the index at the front of the file
 * so playback starts before the download finishes. A multi-minute 1080p upload commonly shrinks by 70-90%.
 *
 * <p>Encoding is slow, so it runs in the background, one video at a time: the admin gets the upload URL immediately and
 * the file at that URL is atomically swapped for the smaller one when it is ready. If ffmpeg is missing, fails, times out
 * or does not make the file meaningfully smaller, the original is left untouched.
 */
@Slf4j
@Component
public class VideoOptimizer {

    private static final long TIMEOUT_MINUTES = 15;
    /** Only replace when the result is clearly smaller; re-encoding an already-efficient video just loses quality. */
    private static final double MAX_RESULT_RATIO = 0.9;

    private final String ffmpegPath;
    private final long minBytes;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "video-optimizer");
        t.setDaemon(true);
        return t;
    });

    public VideoOptimizer(@Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
                          @Value("${app.media.video-optimize-min-bytes:2097152}") long minBytes) {
        this.ffmpegPath = ffmpegPath;
        this.minBytes = minBytes;
    }

    /** Queues the file for optimisation and returns immediately. */
    public void optimizeAsync(Path video) {
        executor.execute(() -> {
            try {
                optimize(video);
            } catch (RuntimeException e) {
                log.warn("Video optimisation failed for {}: {}", video.getFileName(), e.toString());
            }
        });
    }

    /** @return true if the file was replaced by a smaller version. Never throws for encoder problems. */
    boolean optimize(Path video) {
        Path tmp = video.resolveSibling("." + video.getFileName() + ".optimizing");
        try {
            long original = Files.size(video);
            if (original < minBytes) {
                return false;
            }
            if (!encode(video, tmp)) {
                return false;
            }
            long optimized = Files.size(tmp);
            if (optimized <= 0 || optimized > original * MAX_RESULT_RATIO) {
                log.info("Video {} kept as uploaded ({} -> {} bytes is not worth replacing)",
                        video.getFileName(), original, optimized);
                return false;
            }
            Files.move(tmp, video, StandardCopyOption.ATOMIC_MOVE);
            log.info("Optimised video {}: {} -> {} bytes", video.getFileName(), original, optimized);
            return true;
        } catch (IOException e) {
            log.warn("Video optimisation skipped for {}: {}", video.getFileName(), e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Best effort; the name is unguessable and the next restart's volume still holds only one stray file.
            }
        }
    }

    private boolean encode(Path in, Path out) throws IOException, InterruptedException {
        List<String> command = List.of(ffmpegPath, "-nostdin", "-y", "-v", "error",
                "-i", in.toString(),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-vf", "scale='min(1280,iw)':-2",
                "-c:v", "libx264", "-preset", "fast", "-crf", "27", "-maxrate", "2M", "-bufsize", "4M",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "96k",
                "-movflags", "+faststart",
                "-threads", "2",
                "-f", "mp4",
                out.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readNBytes(4000), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            log.warn("ffmpeg timed out after {} minutes on {}", TIMEOUT_MINUTES, in.getFileName());
            return false;
        }
        if (process.exitValue() != 0) {
            log.warn("ffmpeg failed (exit {}) on {}: {}", process.exitValue(), in.getFileName(), output.getNow("").strip());
            return false;
        }
        return true;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
