package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.vo.SkippedFileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Turns an uploaded file into {@link UploadedMedia}. For a single upload a plain file is used as is,
 * and a zip must contain exactly one supported media file, which becomes the asset. For a bulk upload
 * ({@link #readEach}) every supported media file in the zip is used. Folders, hidden files and macOS
 * metadata inside zips are ignored.
 */
@Component
public class UploadedMediaReader {

    private static final Logger log = LoggerFactory.getLogger(UploadedMediaReader.class);

    private final long maxExtractedBytes;
    private final int maxBulkFiles;

    public UploadedMediaReader(StorageProperties properties) {
        this.maxExtractedBytes = properties.maxExtractedSize().toBytes();
        this.maxBulkFiles = properties.maxBulkFiles();
    }

    /**
     * Bulk upload: calls {@code handler} once for each supported media file in a zip, in zip order.
     * Each {@link UploadedMedia} is only valid during its call. Files that aren't supported media are
     * skipped and returned; folders, hidden files and macOS metadata are ignored silently.
     *
     * @throws BadRequestException if the upload is missing, not a valid zip, has no supported media
     *                             files, or has more than {@code dam.storage.max-bulk-files}
     */
    public List<SkippedFileVO> readEach(MultipartFile file, Consumer<UploadedMedia> handler) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty zip file is required");
        }
        String zipName = file.getOriginalFilename() == null ? "" : MediaTypeResolver.baseName(file.getOriginalFilename());
        if (!MediaTypeResolver.isZip(zipName)) {
            throw new BadRequestException("Bulk upload expects a .zip file; got '" + zipName + "'");
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("dam-bulk-", ".zip");
            file.transferTo(temp);
            try (ZipFile zip = openZip(temp, zipName)) {
                List<ZipEntry> media = new ArrayList<>();
                List<SkippedFileVO> skipped = new ArrayList<>();
                zip.stream().filter(e -> !isIgnored(e)).forEach(e -> {
                    if (MediaTypeResolver.isSupported(e.getName())) {
                        media.add(e);
                    } else {
                        skipped.add(new SkippedFileVO(e.getName(), skipReason(e.getName())));
                    }
                });
                if (media.isEmpty()) {
                    throw new BadRequestException("'" + zipName + "' does not contain a supported media file (image, video or PDF)");
                }
                if (media.size() > maxBulkFiles) {
                    throw new BadRequestException("'" + zipName + "' contains " + media.size()
                            + " media files; the limit is " + maxBulkFiles + " per upload");
                }
                log.debug("Bulk upload '{}': {} media files, {} skipped", zipName, media.size(), skipped.size());
                for (ZipEntry entry : media) {
                    String name = MediaTypeResolver.baseName(entry.getName());
                    try (InputStream in = new LimitedInputStream(zip.getInputStream(entry), maxExtractedBytes, entry.getName())) {
                        handler.accept(new UploadedMedia(name, MediaTypeResolver.resolve(name), in, null));
                    }
                }
                return skipped;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload '" + zipName + "'", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    log.warn("Could not delete temp file {}: {}", temp, e.getMessage());
                }
            }
        }
    }

    /** @throws BadRequestException if the file is missing, empty, unsupported, or a zip without exactly one media file */
    public UploadedMedia read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty file is required");
        }
        String filename = file.getOriginalFilename() == null ? "" : MediaTypeResolver.baseName(file.getOriginalFilename());
        try {
            if (MediaTypeResolver.isZip(filename)) {
                return readZip(file, filename);
            }
            return new UploadedMedia(filename, MediaTypeResolver.resolve(filename), file.getInputStream(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload '" + filename + "'", e);
        }
    }

    private UploadedMedia readZip(MultipartFile file, String zipName) throws IOException {
        Path temp = Files.createTempFile("dam-upload-", ".zip");
        ZipFile zip = null;
        boolean handedOver = false;
        try {
            file.transferTo(temp);
            zip = openZip(temp, zipName);
            List<? extends ZipEntry> media = zip.stream()
                    .filter(e -> !isIgnored(e))
                    .filter(e -> MediaTypeResolver.isSupported(e.getName()))
                    .toList();
            if (media.isEmpty()) {
                throw new BadRequestException("'" + zipName + "' does not contain a supported media file (image, video or PDF)");
            }
            if (media.size() > 1) {
                throw new BadRequestException("'" + zipName + "' must contain exactly one media file; found " + media.size());
            }
            ZipEntry entry = media.getFirst();
            String name = MediaTypeResolver.baseName(entry.getName());
            log.debug("Using '{}' from zip '{}'", name, zipName);
            ZipFile openZip = zip;
            InputStream content = new LimitedInputStream(zip.getInputStream(entry), maxExtractedBytes, name);
            UploadedMedia result = new UploadedMedia(name, MediaTypeResolver.resolve(name), content, () -> {
                openZip.close();
                Files.deleteIfExists(temp);
            });
            handedOver = true;
            return result;
        } finally {
            // On success the returned UploadedMedia closes the zip and deletes the temp file
            if (!handedOver) {
                if (zip != null) {
                    zip.close();
                }
                Files.deleteIfExists(temp);
            }
        }
    }

    private static ZipFile openZip(Path path, String zipName) throws IOException {
        try {
            return new ZipFile(path.toFile());
        } catch (ZipException e) {
            throw new BadRequestException("'" + zipName + "' is not a valid zip file");
        }
    }

    /** Folders, hidden files (".DS_Store") and macOS resource forks ("__MACOSX/") aren't content. */
    private static boolean isIgnored(ZipEntry entry) {
        return entry.isDirectory()
                || entry.getName().startsWith("__MACOSX/")
                || MediaTypeResolver.baseName(entry.getName()).startsWith(".");
    }

    private static String skipReason(String name) {
        try {
            MediaTypeResolver.resolve(name);
            return "Unsupported file";
        } catch (BadRequestException e) {
            return e.getMessage();
        }
    }

    /** Fails once more than {@code limit} bytes have been read, so a zip bomb can't fill the disk. */
    static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private final String name;
        private long count;

        LimitedInputStream(InputStream in, long limit, String name) {
            super(in);
            this.limit = limit;
            this.name = name;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) {
            count += n;
            if (count > limit) {
                throw new BadRequestException("'" + name + "' is larger than the " + limit + " byte limit for files inside a zip");
            }
        }
    }
}
