package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaTypeResolverTest {

    @ParameterizedTest
    @CsvSource({
            "photo.jpg, IMAGE, image/jpeg, jpg",
            "PHOTO.JPEG, IMAGE, image/jpeg, jpeg",
            "logo.svg, IMAGE, image/svg+xml, svg",
            "banner.webp, IMAGE, image/webp, webp",
            "clip.mp4, VIDEO, video/mp4, mp4",
            "clip.MOV, VIDEO, video/quicktime, mov",
            "brochure.pdf, DOCUMENT, application/pdf, pdf",
            "folder/sub/photo.png, IMAGE, image/png, png"
    })
    void resolvesSupportedTypes(String filename, MediaType type, String mime, String ext) {
        MediaTypeResolver.Resolved resolved = MediaTypeResolver.resolve(filename);

        assertThat(resolved.mediaType()).isEqualTo(type);
        assertThat(resolved.mimeType()).isEqualTo(mime);
        assertThat(resolved.extension()).isEqualTo(ext);
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.mp3", "voice.wav", "track.flac"})
    void rejectsAudio(String filename) {
        assertThatThrownBy(() -> MediaTypeResolver.resolve(filename))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Audio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.txt", "archive.tar", "README", ".hidden", "trailingdot."})
    void rejectsUnsupportedOrMissingExtensions(String filename) {
        assertThatThrownBy(() -> MediaTypeResolver.resolve(filename)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void detectsZip() {
        assertThat(MediaTypeResolver.isZip("bundle.ZIP")).isTrue();
        assertThat(MediaTypeResolver.isZip("photo.jpg")).isFalse();
        assertThat(MediaTypeResolver.isSupported("bundle.zip")).isFalse();
    }
}
