package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.entities.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers {@link ImageMagickImageThumbnailGenerator} and {@link ImageMagickPdfThumbnailGenerator}. */
@ExtendWith(MockitoExtension.class)
class ImageMagickThumbnailGeneratorsTest {

    private static final ThumbnailOptions OPTIONS = new ThumbnailOptions(200, 100, Duration.ZERO);
    private static final Path SOURCE = Path.of("/tmp/dam-thumb-1.jpg");

    @Mock
    private CommandRunner runner;

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "gif", "png", "svg", "bmp", "psd"})
    void supportsTheSpecifiedImageFormats(String extension) {
        assertThat(new ImageMagickImageThumbnailGenerator(runner, PROPERTIES).supports(MediaType.IMAGE, extension)).isTrue();
    }

    @Test
    void doesNotSupportOtherTypes() {
        ImageMagickImageThumbnailGenerator generator = new ImageMagickImageThumbnailGenerator(runner, PROPERTIES);

        assertThat(generator.supports(MediaType.IMAGE, "heic")).isFalse();
        assertThat(generator.supports(MediaType.DOCUMENT, "pdf")).isFalse();
        assertThat(generator.supports(MediaType.VIDEO, "mp4")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"jpg, JPEG", "psd, PSD", "svg, SVG", "gif, GIF"})
    void scalesTheFirstFrameIntoTheBoxWithAnExplicitInputFormat(String extension, String coder) {
        when(runner.run(anyList(), any())).thenReturn(new byte[]{7});

        Thumbnail thumbnail = new ImageMagickImageThumbnailGenerator(runner, PROPERTIES).generate(SOURCE, extension, OPTIONS);

        assertThat(thumbnail.data()).containsExactly(7);
        assertThat(thumbnail.mimeType()).isEqualTo("image/jpeg");
        assertThat(thumbnail.extension()).isEqualTo("jpg");
        verify(runner).run(List.of("magick", coder + ":/tmp/dam-thumb-1.jpg[0]", "-auto-orient",
                "-thumbnail", "200x100>", "-background", "white", "-alpha", "remove", "-alpha", "off",
                "-strip", "-quality", "85", "JPEG:-"), Duration.ofSeconds(60));
    }

    @Test
    void failsWhenNothingIsProduced() {
        when(runner.run(anyList(), any())).thenReturn(new byte[0]);

        assertThatThrownBy(() -> new ImageMagickImageThumbnailGenerator(runner, PROPERTIES).generate(SOURCE, "png", OPTIONS))
                .isInstanceOf(ThumbnailGenerationException.class);
    }

    @Test
    void rendersTheFirstPdfPageOnWhite() {
        ImageMagickPdfThumbnailGenerator generator = new ImageMagickPdfThumbnailGenerator(runner, PROPERTIES);
        when(runner.run(anyList(), any())).thenReturn(new byte[]{3});

        Thumbnail thumbnail = generator.generate(Path.of("/tmp/a.pdf"), "pdf", OPTIONS);

        assertThat(generator.supports(MediaType.DOCUMENT, "pdf")).isTrue();
        assertThat(generator.supports(MediaType.IMAGE, "png")).isFalse();
        assertThat(thumbnail.data()).containsExactly(3);
        assertThat(thumbnail.mimeType()).isEqualTo("image/jpeg");
        verify(runner).run(List.of("magick", "-density", "150", "PDF:/tmp/a.pdf[0]", "-thumbnail", "200x100>",
                "-background", "white", "-alpha", "remove", "-alpha", "off",
                "-strip", "-quality", "85", "JPEG:-"), Duration.ofSeconds(60));
    }
}
