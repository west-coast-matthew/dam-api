package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.entities.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.twofourseven.dam.services.thumbnails.ThumbnailGenerationServiceImplTest.PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FfmpegVideoThumbnailGeneratorTest {

    private static final Path SOURCE = Path.of("/tmp/clip.mp4");
    private static final ThumbnailOptions OPTIONS = new ThumbnailOptions(200, 100, Duration.ofMillis(2500));

    @Mock
    private CommandRunner runner;

    private List<String> commandAt(String seconds) {
        return List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
                "-ss", seconds, "-i", "/tmp/clip.mp4", "-frames:v", "1",
                "-vf", "scale='min(200,iw)':'min(100,ih)':force_original_aspect_ratio=decrease",
                "-f", "image2pipe", "-c:v", "png", "pipe:1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"avi", "mkv", "mp4", "mov"})
    void supportsTheSpecifiedVideoFormats(String extension) {
        assertThat(new FfmpegVideoThumbnailGenerator(runner, PROPERTIES).supports(MediaType.VIDEO, extension)).isTrue();
    }

    @Test
    void doesNotSupportOtherTypes() {
        FfmpegVideoThumbnailGenerator generator = new FfmpegVideoThumbnailGenerator(runner, PROPERTIES);

        assertThat(generator.supports(MediaType.VIDEO, "webm")).isFalse();
        assertThat(generator.supports(MediaType.IMAGE, "png")).isFalse();
    }

    @Test
    void takesAFrameAtTheRequestedOffset() {
        when(runner.run(commandAt("2.500"), Duration.ofSeconds(60))).thenReturn(new byte[]{4});

        Thumbnail thumbnail = new FfmpegVideoThumbnailGenerator(runner, PROPERTIES).generate(SOURCE, "mp4", OPTIONS);

        assertThat(thumbnail.data()).containsExactly(4);
        assertThat(thumbnail.mimeType()).isEqualTo("image/png");
    }

    @Test
    void usesTheFirstFrameWhenTheVideoIsShorterThanTheOffset() {
        when(runner.run(commandAt("2.500"), Duration.ofSeconds(60))).thenReturn(new byte[0]);
        when(runner.run(commandAt("0.000"), Duration.ofSeconds(60))).thenReturn(new byte[]{5});

        Thumbnail thumbnail = new FfmpegVideoThumbnailGenerator(runner, PROPERTIES).generate(SOURCE, "mp4", OPTIONS);

        assertThat(thumbnail.data()).containsExactly(5);
    }

    @Test
    void failsWhenNoFrameCanBeRead() {
        when(runner.run(anyList(), any())).thenReturn(new byte[0]);

        assertThatThrownBy(() -> new FfmpegVideoThumbnailGenerator(runner, PROPERTIES)
                .generate(SOURCE, "mp4", OPTIONS.withVideoOffset(Duration.ZERO)))
                .isInstanceOf(ThumbnailGenerationException.class);
        verify(runner, times(1)).run(anyList(), any());
    }
}
