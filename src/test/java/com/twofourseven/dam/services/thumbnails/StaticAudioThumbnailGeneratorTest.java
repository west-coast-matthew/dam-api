package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.entities.core.MediaType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAudioThumbnailGeneratorTest {

    private final StaticAudioThumbnailGenerator generator = new StaticAudioThumbnailGenerator();

    @Test
    void supportsAudioOnly() {
        assertThat(generator.supports(MediaType.AUDIO, "mp3")).isTrue();
        assertThat(generator.supports(MediaType.IMAGE, "png")).isFalse();
    }

    @Test
    void returnsTheBundledJpeg() {
        Thumbnail thumbnail = generator.generate(Path.of("/tmp/song.mp3"), "mp3", new ThumbnailOptions(10, 10, Duration.ZERO));

        assertThat(thumbnail.mimeType()).isEqualTo("image/jpeg");
        assertThat(thumbnail.data()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }
}
