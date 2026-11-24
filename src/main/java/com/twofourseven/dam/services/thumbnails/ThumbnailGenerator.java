package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.entities.core.MediaType;

import java.nio.file.Path;

/**
 * One pluggable way of making thumbnails, e.g. a local command line tool or a call to a remote API.
 * Every implementation that is a Spring bean is offered to {@link ThumbnailGenerationService}, which
 * uses the first one (in {@link org.springframework.core.annotation.Order} order) that
 * {@link #supports} the file. The built-in generators have the default, lowest precedence, so a
 * deployment replaces one by adding a bean with a higher precedence (e.g. {@code @Order(0)}).
 */
public interface ThumbnailGenerator {

    /**
     * @param mediaType the file's media type
     * @param extension lower case file extension without the dot
     */
    boolean supports(MediaType mediaType, String extension);

    /**
     * Makes a thumbnail of {@code source}, a temporary file that is deleted after this call returns.
     *
     * @param extension lower case file extension of {@code source}, without the dot
     * @throws ThumbnailGenerationException if the thumbnail can't be made
     */
    Thumbnail generate(Path source, String extension, ThumbnailOptions options);
}
