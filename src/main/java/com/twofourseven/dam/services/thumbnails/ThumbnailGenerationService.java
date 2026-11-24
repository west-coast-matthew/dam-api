package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.exceptions.BadRequestException;

import java.io.InputStream;

/**
 * Makes thumbnails for uploaded files without exposing how: images are scaled down, videos give a
 * frame from a point in time, PDFs give their first page and audio gets a fixed image. The work is
 * done by a pluggable {@link ThumbnailGenerator}. Storing the result is the caller's job.
 *
 * <p>{@code filename} is only used for its extension, which decides the media type.
 *
 * @throws BadRequestException          if the data is empty or the file type has no thumbnail support
 * @throws ThumbnailGenerationException if the generator fails
 */
public interface ThumbnailGenerationService {

    /** True if a thumbnail can be made for files with this name's extension. */
    boolean supports(String filename);

    /** A thumbnail using the configured default options ({@code dam.thumbnails.*}). */
    Thumbnail generate(byte[] data, String filename);

    Thumbnail generate(byte[] data, String filename, ThumbnailOptions options);

    /** A thumbnail using the configured default options. The stream is read but not closed. */
    Thumbnail generate(InputStream data, String filename);

    /** The stream is read but not closed. */
    Thumbnail generate(InputStream data, String filename, ThumbnailOptions options);

    /** The configured default options, as a starting point for custom ones. */
    ThumbnailOptions defaultOptions();
}
