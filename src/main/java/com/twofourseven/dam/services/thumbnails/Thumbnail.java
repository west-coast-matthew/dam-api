package com.twofourseven.dam.services.thumbnails;

/**
 * A generated thumbnail image. Thumbnails are stored as {@code thumbnail.jpg} (STORAGE_SPECIFICATION.md),
 * so generators return JPEG.
 *
 * @param data      the encoded image
 * @param mimeType  its MIME type, e.g. {@code image/jpeg}
 * @param extension file extension without the dot, e.g. {@code jpg}
 */
public record Thumbnail(byte[] data, String mimeType, String extension) {

    public static final String JPEG_MIME_TYPE = "image/jpeg";

    public static Thumbnail jpeg(byte[] data) {
        return new Thumbnail(data, JPEG_MIME_TYPE, "jpg");
    }
}
