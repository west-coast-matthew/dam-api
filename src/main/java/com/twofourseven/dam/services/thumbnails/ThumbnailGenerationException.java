package com.twofourseven.dam.services.thumbnails;

/**
 * A thumbnail couldn't be made from a file the service supports, e.g. the tool failed, timed out or
 * isn't installed. Left to {@code GlobalExceptionHandler}, it becomes a 500 with a generic message.
 */
public class ThumbnailGenerationException extends RuntimeException {

    public ThumbnailGenerationException(String message) {
        super(message);
    }

    public ThumbnailGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
