/**
 * Thumbnail generation (spec 001). {@link com.twofourseven.dam.services.thumbnails.ThumbnailGenerationService}
 * is the entry point; the work is done by pluggable
 * {@link com.twofourseven.dam.services.thumbnails.ThumbnailGenerator}s that shell out to ImageMagick and ffmpeg.
 */
package com.twofourseven.dam.services.thumbnails;
