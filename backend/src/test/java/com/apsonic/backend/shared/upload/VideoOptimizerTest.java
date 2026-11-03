package com.apsonic.backend.shared.upload;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VideoOptimizerTest {

    @TempDir
    Path dir;

    private static boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** A noisy 1080p clip is large; ffmpeg can synthesise one without any fixture file. */
    private Path makeLargeVideo() throws Exception {
        Path src = dir.resolve("src.mp4");
        Process p = new ProcessBuilder("ffmpeg", "-nostdin", "-y", "-v", "error",
                "-f", "lavfi", "-i", "testsrc2=size=1920x1080:rate=25:duration=4",
                "-c:v", "libx264", "-b:v", "12M", "-pix_fmt", "yuv420p", src.toString())
                .redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertThat(p.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isZero();
        return src;
    }

    @Test
    void largeVideoIsReplacedByASmallerOneAtTheSamePath() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "ffmpeg not installed");
        Path video = makeLargeVideo();
        long before = Files.size(video);

        boolean replaced = new VideoOptimizer("ffmpeg", 0L).optimize(video);

        assertThat(replaced).isTrue();
        assertThat(Files.size(video)).isLessThan(before / 2);
        try (var siblings = Files.list(dir)) {
            // No leftover temporary file next to it.
            assertThat(siblings.map(p -> p.getFileName().toString())).containsExactly("src.mp4");
        }
    }

    @Test
    void smallFilesAreLeftAlone() throws Exception {
        Path video = dir.resolve("tiny.mp4");
        Files.write(video, new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'});

        assertThat(new VideoOptimizer("ffmpeg", 1_000_000L).optimize(video)).isFalse();
        assertThat(Files.size(video)).isEqualTo(8);
    }

    @Test
    void missingFfmpegLeavesTheOriginalInPlace() throws Exception {
        Path video = dir.resolve("a.mp4");
        Files.write(video, new byte[]{1, 2, 3, 4});

        assertThat(new VideoOptimizer("/definitely/not/ffmpeg", 0L).optimize(video)).isFalse();
        assertThat(Files.readAllBytes(video)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void corruptVideoLeavesTheOriginalAndNoTempFile() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "ffmpeg not installed");
        Path video = dir.resolve("bad.mp4");
        Files.write(video, new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4, 5, 6, 7, 8});

        assertThat(new VideoOptimizer("ffmpeg", 0L).optimize(video)).isFalse();
        assertThat(Files.size(video)).isEqualTo(20);
        try (var siblings = Files.list(dir)) {
            assertThat(siblings.count()).isEqualTo(1);
        }
    }
}
