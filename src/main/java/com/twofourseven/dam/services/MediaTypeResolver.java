package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Works out an asset's {@link MediaType} and MIME type from its file extension.
 * Supported: common image formats, video and PDF. Audio is not supported yet.
 */
public final class MediaTypeResolver {

    /** The result of resolving a file name. {@code extension} is lower case, without the dot. */
    public record Resolved(MediaType mediaType, String mimeType, String extension) {
    }

    private static final Map<String, Resolved> BY_EXTENSION = Map.ofEntries(
            image("jpg", "image/jpeg"), image("jpeg", "image/jpeg"), image("png", "image/png"),
            image("gif", "image/gif"), image("webp", "image/webp"), image("svg", "image/svg+xml"),
            image("tif", "image/tiff"), image("tiff", "image/tiff"), image("bmp", "image/bmp"),
            image("heic", "image/heic"), image("heif", "image/heif"), image("avif", "image/avif"),
            video("mp4", "video/mp4"), video("m4v", "video/x-m4v"), video("mov", "video/quicktime"),
            video("avi", "video/x-msvideo"), video("mkv", "video/x-matroska"), video("webm", "video/webm"),
            video("wmv", "video/x-ms-wmv"), video("mpg", "video/mpeg"), video("mpeg", "video/mpeg"),
            Map.entry("pdf", new Resolved(MediaType.DOCUMENT, "application/pdf", "pdf")));

    private static final Set<String> AUDIO = Set.of("mp3", "wav", "aac", "m4a", "flac", "ogg", "oga", "wma", "aif", "aiff");

    private MediaTypeResolver() {
    }

    /** @throws BadRequestException if the extension is missing, audio, or not supported */
    public static Resolved resolve(String filename) {
        String ext = extension(filename);
        if (ext.isEmpty()) {
            throw new BadRequestException("Cannot determine the media type of '" + filename + "': it has no file extension");
        }
        if (AUDIO.contains(ext)) {
            throw new BadRequestException("Audio files are not supported yet ('" + filename + "')");
        }
        Resolved resolved = BY_EXTENSION.get(ext);
        if (resolved == null) {
            throw new BadRequestException("Unsupported file type '." + ext + "'; expected an image, video or PDF");
        }
        return resolved;
    }

    public static boolean isSupported(String filename) {
        return BY_EXTENSION.containsKey(extension(filename));
    }

    public static boolean isZip(String filename) {
        return "zip".equals(extension(filename));
    }

    /** Lower case extension without the dot, or "" if there is none. */
    static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String base = baseName(filename);
        int dot = base.lastIndexOf('.');
        return dot <= 0 || dot == base.length() - 1 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The last path segment, for names like "folder/photo.jpg" or "C:\\photos\\photo.jpg". */
    static String baseName(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return filename.substring(slash + 1);
    }

    private static Map.Entry<String, Resolved> image(String ext, String mime) {
        return Map.entry(ext, new Resolved(MediaType.IMAGE, mime, ext));
    }

    private static Map.Entry<String, Resolved> video(String ext, String mime) {
        return Map.entry(ext, new Resolved(MediaType.VIDEO, mime, ext));
    }
}
