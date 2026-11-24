package com.twofourseven.dam.services.thumbnails;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Takes one frame of a video as a JPEG with ffmpeg at {@link ThumbnailOptions#videoOffset()} and scales it to
 * fit the thumbnail box (aspect ratio kept, never enlarged). ffmpeg writes nothing when the offset is
 * past the end of the video, so it then tries again with the first frame.
 */
@Component
public class FfmpegVideoThumbnailGenerator implements ThumbnailGenerator {

    private static final Logger log = LoggerFactory.getLogger(FfmpegVideoThumbnailGenerator.class);

    static final Set<String> EXTENSIONS = Set.of("avi", "mkv", "mp4", "mov");

    private final CommandRunner runner;
    private final ThumbnailProperties properties;

    public FfmpegVideoThumbnailGenerator(CommandRunner runner, ThumbnailProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public boolean supports(MediaType mediaType, String extension) {
        return mediaType == MediaType.VIDEO && EXTENSIONS.contains(extension);
    }

    @Override
    public Thumbnail generate(Path source, String extension, ThumbnailOptions options) {
        byte[] frame = frameAt(source, options.videoOffset(), options);
        if (frame.length == 0 && !options.videoOffset().isZero()) {
            log.debug("{} is shorter than {}; using its first frame", source.getFileName(), options.videoOffset());
            frame = frameAt(source, Duration.ZERO, options);
        }
        return ImageMagickImageThumbnailGenerator.jpeg(frame, source);
    }

    private byte[] frameAt(Path source, Duration offset, ThumbnailOptions options) {
        List<String> command = List.of(
                properties.ffmpegCommand(),
                "-hide_banner", "-loglevel", "error", "-nostdin",
                "-ss", seconds(offset),
                "-i", source.toString(),
                "-frames:v", "1",
                "-vf", scaleFilter(options),
                "-f", "image2pipe", "-c:v", "mjpeg", "-q:v", "3",
                "pipe:1");
        return runner.run(command, properties.commandTimeout());
    }

    static String seconds(Duration offset) {
        return String.format(Locale.ROOT, "%.3f", offset.toMillis() / 1000.0);
    }

    /** Fits inside the box, keeps the aspect ratio and never enlarges. */
    static String scaleFilter(ThumbnailOptions options) {
        return "scale='min(" + options.maxWidth() + ",iw)':'min(" + options.maxHeight() + ",ih)'"
                + ":force_original_aspect_ratio=decrease";
    }
}
