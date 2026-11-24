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
    void returnsTheBundledPng() {
        Thumbnail thumbnail = generator.generate(Path.of("/tmp/song.mp3"), "mp3", new ThumbnailOptions(10, 10, Duration.ZERO));

        assertThat(thumbnail.mimeType()).isEqualTo("image/png");
        assertThat(thumbnail.data()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }
}
