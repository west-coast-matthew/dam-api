package com.twofourseven.dam.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Thumbnail generation settings (dam.thumbnails.*). Thumbnails fit inside
 * {@code maxWidth} x {@code maxHeight} with the aspect ratio kept, and are never enlarged.
 *
 * @param maxWidth         widest thumbnail, in pixels
 * @param maxHeight        tallest thumbnail, in pixels
 * @param videoFrameOffset how far into a video the thumbnail frame is taken
 * @param pdfDensity       DPI used to render the first page of a PDF before it is scaled down
 * @param commandTimeout   longest a thumbnail command may run before it is stopped
 * @param magickCommand    ImageMagick executable ({@code magick}, or {@code convert} for ImageMagick 6)
 * @param ffmpegCommand    ffmpeg executable
 * @param threads          how many thumbnails are generated at the same time after uploads
 */
@Validated
@ConfigurationProperties(prefix = "dam.thumbnails")
public record ThumbnailProperties(
        @DefaultValue("320") @Min(1) int maxWidth,
        @DefaultValue("320") @Min(1) int maxHeight,
        @DefaultValue("1s") @NotNull Duration videoFrameOffset,
        @DefaultValue("150") @Min(1) int pdfDensity,
        @DefaultValue("60s") @NotNull Duration commandTimeout,
        @DefaultValue("magick") @NotBlank String magickCommand,
        @DefaultValue("ffmpeg") @NotBlank String ffmpegCommand,
        @DefaultValue("4") @Min(1) int threads) {
}
