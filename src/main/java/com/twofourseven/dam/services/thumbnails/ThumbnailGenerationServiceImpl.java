package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.services.MediaTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Picks the first {@link ThumbnailGenerator} that supports the file (Spring orders the injected list
 * by {@code @Order}) and hands it the data as a temporary file, which is deleted afterwards.
 */
@Service
public class ThumbnailGenerationServiceImpl implements ThumbnailGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailGenerationServiceImpl.class);

    private final List<ThumbnailGenerator> generators;
    private final ThumbnailOptions defaultOptions;

    public ThumbnailGenerationServiceImpl(List<ThumbnailGenerator> generators, ThumbnailProperties properties) {
        this.generators = List.copyOf(generators);
        this.defaultOptions = ThumbnailOptions.from(properties);
    }

    @Override
    public boolean supports(String filename) {
        return generatorFor(filename).isPresent();
    }

    @Override
    public Thumbnail generate(byte[] data, String filename) {
        return generate(data, filename, defaultOptions);
    }

    @Override
    public Thumbnail generate(byte[] data, String filename, ThumbnailOptions options) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("Cannot make a thumbnail of '" + filename + "': the file is empty");
        }
        return generate(new ByteArrayInputStream(data), filename, options);
    }

    @Override
    public Thumbnail generate(InputStream data, String filename) {
        return generate(data, filename, defaultOptions);
    }

    @Override
    public Thumbnail generate(InputStream data, String filename, ThumbnailOptions options) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(options, "options");
        ThumbnailGenerator generator = generatorFor(filename).orElseThrow(() -> new BadRequestException(
                "Thumbnails are not supported for '" + filename + "'"));
        String extension = MediaTypeResolver.extension(filename);

        Path temp = null;
        try {
            temp = Files.createTempFile("dam-thumb-", "." + extension);
            if (Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING) == 0) {
                throw new BadRequestException("Cannot make a thumbnail of '" + filename + "': the file is empty");
            }
            log.debug("Making a thumbnail of '{}' with {}", filename, generator.getClass().getSimpleName());
            return generator.generate(temp, extension, options);
        } catch (IOException e) {
            throw new ThumbnailGenerationException("Could not buffer '" + filename + "' for thumbnail generation", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public ThumbnailOptions defaultOptions() {
        return defaultOptions;
    }

    private Optional<ThumbnailGenerator> generatorFor(String filename) {
        Optional<MediaType> mediaType = MediaTypeResolver.classify(filename);
        if (mediaType.isEmpty()) {
            return Optional.empty();
        }
        String extension = MediaTypeResolver.extension(filename);
        return generators.stream().filter(g -> g.supports(mediaType.get(), extension)).findFirst();
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("Could not delete temporary thumbnail source {}", temp, e);
        }
    }
}
