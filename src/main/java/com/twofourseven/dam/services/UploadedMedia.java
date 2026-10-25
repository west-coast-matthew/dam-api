package com.twofourseven.dam.services;

import java.io.IOException;
import java.io.InputStream;

/**
 * A media file taken from an upload, ready to be written to storage. When the upload was a zip,
 * this is the single media file inside it. Close it to release any temporary files.
 *
 * @param filename the media file's own name (not the zip's)
 */
public record UploadedMedia(String filename, MediaTypeResolver.Resolved type, InputStream content,
                            AutoCloseable cleanup) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        try {
            content.close();
            if (cleanup != null) {
                cleanup.close();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
