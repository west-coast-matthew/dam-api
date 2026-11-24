package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.entities.core.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Every audio file gets the same bundled image ({@code thumbnails/audio.jpg}); options are ignored. */
@Component
public class StaticAudioThumbnailGenerator implements ThumbnailGenerator {

    static final String RESOURCE = "/thumbnails/audio.jpg";

    private final byte[] image;

    public StaticAudioThumbnailGenerator() {
        try (InputStream in = StaticAudioThumbnailGenerator.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            this.image = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    @Override
    public boolean supports(MediaType mediaType, String extension) {
        return mediaType == MediaType.AUDIO;
    }

    @Override
    public Thumbnail generate(Path source, String extension, ThumbnailOptions options) {
        return Thumbnail.jpeg(image.clone());
    }
}
