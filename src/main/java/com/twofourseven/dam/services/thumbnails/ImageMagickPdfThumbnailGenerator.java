package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.MediaType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Renders the first page of a PDF with ImageMagick (which uses Ghostscript for PDFs):
 * {@code magick -density 150 PDF:in.pdf[0] -background white -alpha remove -alpha off -thumbnail 320x320> -strip PNG:-}.
 * Transparent pages get a white background.
 */
@Component
public class ImageMagickPdfThumbnailGenerator implements ThumbnailGenerator {

    private final CommandRunner runner;
    private final ThumbnailProperties properties;

    public ImageMagickPdfThumbnailGenerator(CommandRunner runner, ThumbnailProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public boolean supports(MediaType mediaType, String extension) {
        return mediaType == MediaType.DOCUMENT && "pdf".equals(extension);
    }

    @Override
    public Thumbnail generate(Path source, String extension, ThumbnailOptions options) {
        List<String> command = List.of(
                properties.magickCommand(),
                "-density", String.valueOf(properties.pdfDensity()),
                "PDF:" + source + "[0]",
                "-background", "white",
                "-alpha", "remove",
                "-alpha", "off",
                "-thumbnail", ImageMagickImageThumbnailGenerator.fitWithin(options),
                "-strip",
                "PNG:-");
        return ImageMagickImageThumbnailGenerator.png(runner.run(command, properties.commandTimeout()), source);
    }
}
