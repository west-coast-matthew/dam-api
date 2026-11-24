package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.MediaType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Scales images down to a JPEG with ImageMagick:
 * {@code magick PNG:in.png[0] -auto-orient -thumbnail 320x320> -background white -alpha remove -alpha off -strip -quality 85 JPEG:-}.
 * Transparent areas become white, since JPEG has no transparency. The input format is named explicitly so ImageMagick never guesses it from the content, and
 * {@code [0]} takes the first frame of a GIF or the flattened composite of a PSD. SVG needs an SVG
 * delegate (librsvg, or ImageMagick's built-in MSVG).
 */
@Component
public class ImageMagickImageThumbnailGenerator implements ThumbnailGenerator {

    /** Supported extensions and their ImageMagick coder names. */
    static final Map<String, String> CODERS = Map.of(
            "jpg", "JPEG", "jpeg", "JPEG", "gif", "GIF", "png", "PNG",
            "svg", "SVG", "bmp", "BMP", "psd", "PSD");

    static final int JPEG_QUALITY = 85;

    private final CommandRunner runner;
    private final ThumbnailProperties properties;

    public ImageMagickImageThumbnailGenerator(CommandRunner runner, ThumbnailProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public boolean supports(MediaType mediaType, String extension) {
        return mediaType == MediaType.IMAGE && CODERS.containsKey(extension);
    }

    @Override
    public Thumbnail generate(Path source, String extension, ThumbnailOptions options) {
        List<String> command = List.of(
                properties.magickCommand(),
                CODERS.get(extension) + ":" + source + "[0]",
                "-auto-orient",
                "-thumbnail", fitWithin(options));
        return jpeg(runner.run(withJpegOutput(command), properties.commandTimeout()), source);
    }

    /** ImageMagick geometry that fits inside the box, keeps the aspect ratio and never enlarges. */
    static String fitWithin(ThumbnailOptions options) {
        return options.maxWidth() + "x" + options.maxHeight() + ">";
    }

    /** Appends the arguments that flatten onto white and write a JPEG to standard output. */
    static List<String> withJpegOutput(List<String> command) {
        List<String> full = new java.util.ArrayList<>(command);
        full.addAll(List.of("-background", "white", "-alpha", "remove", "-alpha", "off",
                "-strip", "-quality", String.valueOf(JPEG_QUALITY), "JPEG:-"));
        return full;
    }

    static Thumbnail jpeg(byte[] data, Path source) {
        if (data.length == 0) {
            throw new ThumbnailGenerationException("No thumbnail was produced for " + source.getFileName());
        }
        return Thumbnail.jpeg(data);
    }
}
