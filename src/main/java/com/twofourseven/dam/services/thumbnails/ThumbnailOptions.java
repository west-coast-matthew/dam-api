package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.config.ThumbnailProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * How a thumbnail is made. The image fits inside {@code maxWidth} x {@code maxHeight} with its
 * aspect ratio kept (portrait stays portrait) and is never enlarged.
 *
 * @param maxWidth    widest thumbnail, in pixels
 * @param maxHeight   tallest thumbnail, in pixels
 * @param videoOffset point in a video to take the frame from; videos shorter than this use the first frame
 */
public record ThumbnailOptions(int maxWidth, int maxHeight, Duration videoOffset) {

    public ThumbnailOptions {
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Thumbnail width and height must be at least 1");
        }
        Objects.requireNonNull(videoOffset, "videoOffset");
        if (videoOffset.isNegative()) {
            throw new IllegalArgumentException("videoOffset must not be negative");
        }
    }

    /** The configured defaults. */
    public static ThumbnailOptions from(ThumbnailProperties properties) {
        return new ThumbnailOptions(properties.maxWidth(), properties.maxHeight(), properties.videoFrameOffset());
    }

    /** These options with the video frame taken at {@code offset}. */
    public ThumbnailOptions withVideoOffset(Duration offset) {
        return new ThumbnailOptions(maxWidth, maxHeight, offset);
    }
}
