package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailGenerationServiceImplTest {

    static final ThumbnailProperties PROPERTIES = new ThumbnailProperties(
            200, 100, Duration.ofSeconds(2), 150, Duration.ofSeconds(60), "magick", "ffmpeg");

    @Mock
    private ThumbnailGenerator first;

    @Mock
    private ThumbnailGenerator second;

    private ThumbnailGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ThumbnailGenerationServiceImpl(List.of(first, second), PROPERTIES);
    }

    @Test
    void handsTheDataToTheFirstSupportingGeneratorAsATemporaryFileAndDeletesIt() {
        when(first.supports(MediaType.IMAGE, "png")).thenReturn(false);
        when(second.supports(MediaType.IMAGE, "png")).thenReturn(true);
        AtomicReference<Path> seen = new AtomicReference<>();
        Thumbnail expected = Thumbnail.png(new byte[]{9});
        when(second.generate(any(), eq("png"), any())).thenAnswer(inv -> {
            Path source = inv.getArgument(0);
            seen.set(source);
            assertThat(Files.readAllBytes(source)).containsExactly(1, 2, 3);
            assertThat(source.getFileName().toString()).endsWith(".png");
            return expected;
        });

        Thumbnail result = service.generate(new byte[]{1, 2, 3}, "photos/Logo.PNG");

        assertThat(result).isSameAs(expected);
        assertThat(seen.get()).doesNotExist();
        verify(first, never()).generate(any(), anyString(), any());
    }

    @Test
    void usesTheConfiguredDefaultOptions() {
        when(first.supports(MediaType.VIDEO, "mp4")).thenReturn(true);
        when(first.generate(any(), eq("mp4"), eq(new ThumbnailOptions(200, 100, Duration.ofSeconds(2)))))
                .thenReturn(Thumbnail.png(new byte[]{1}));

        service.generate(new ByteArrayInputStream(new byte[]{5}), "clip.mp4");

        assertThat(service.defaultOptions()).isEqualTo(new ThumbnailOptions(200, 100, Duration.ofSeconds(2)));
    }

    @Test
    void passesCustomOptionsThrough() {
        ThumbnailOptions options = service.defaultOptions().withVideoOffset(Duration.ofMillis(1500));
        when(first.supports(MediaType.VIDEO, "mov")).thenReturn(true);
        when(first.generate(any(), eq("mov"), eq(options))).thenReturn(Thumbnail.png(new byte[]{1}));

        assertThat(service.generate(new byte[]{5}, "clip.mov", options).data()).containsExactly(1);
    }

    @Test
    void deletesTheTemporaryFileWhenTheGeneratorFails() {
        when(first.supports(MediaType.DOCUMENT, "pdf")).thenReturn(true);
        AtomicReference<Path> seen = new AtomicReference<>();
        when(first.generate(any(), eq("pdf"), any())).thenAnswer(inv -> {
            seen.set(inv.getArgument(0));
            throw new ThumbnailGenerationException("boom");
        });

        assertThatThrownBy(() -> service.generate(new byte[]{1}, "doc.pdf"))
                .isInstanceOf(ThumbnailGenerationException.class);
        assertThat(seen.get()).doesNotExist();
    }

    @Test
    void classifiesAudioSoItCanGetAThumbnail() {
        when(first.supports(MediaType.AUDIO, "mp3")).thenReturn(true);

        assertThat(service.supports("song.mp3")).isTrue();
    }

    @Test
    void rejectsFilesNoGeneratorSupports() {
        lenient().when(first.supports(any(), anyString())).thenReturn(false);
        lenient().when(second.supports(any(), anyString())).thenReturn(false);

        assertThat(service.supports("photo.webp")).isFalse();
        assertThatThrownBy(() -> service.generate(new byte[]{1}, "photo.webp"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("photo.webp");
        assertThatThrownBy(() -> service.generate(new byte[]{1}, "notes.txt"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.generate(new byte[]{1}, "no-extension"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsEmptyData() {
        lenient().when(first.supports(MediaType.IMAGE, "jpg")).thenReturn(true);

        assertThatThrownBy(() -> service.generate(new byte[0], "photo.jpg"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> service.generate(new ByteArrayInputStream(new byte[0]), "photo.jpg"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("empty");
        verify(first, never()).generate(any(), anyString(), any());
    }

    @Test
    void optionsMustBePositive() {
        assertThatThrownBy(() -> new ThumbnailOptions(0, 10, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThumbnailOptions(10, 10, Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
