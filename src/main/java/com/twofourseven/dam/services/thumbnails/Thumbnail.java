package com.twofourseven.dam.services.thumbnails;

/**
 * A generated thumbnail image.
 *
 * @param data      the encoded image
 * @param mimeType  its MIME type, e.g. {@code image/png}
 * @param extension file extension without the dot, e.g. {@code png}
 */
public record Thumbnail(byte[] data, String mimeType, String extension) {

    public static final String PNG_MIME_TYPE = "image/png";

    public static Thumbnail png(byte[] data) {
        return new Thumbnail(data, PNG_MIME_TYPE, "png");
    }
}
