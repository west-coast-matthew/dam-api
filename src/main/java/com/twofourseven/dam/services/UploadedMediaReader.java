package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.exceptions.BadRequestException;
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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Turns an uploaded file into an {@link UploadedMedia}. A plain file is used as is. A zip must
 * contain exactly one supported media file (folders, hidden files and macOS metadata are ignored);
 * that file becomes the asset.
 */
@Component
public class UploadedMediaReader {

    private static final Logger log = LoggerFactory.getLogger(UploadedMediaReader.class);

    private final long maxExtractedBytes;

    public UploadedMediaReader(StorageProperties properties) {
        this.maxExtractedBytes = properties.maxExtractedSize().toBytes();
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
            try {
                zip = new ZipFile(temp.toFile());
            } catch (ZipException e) {
                throw new BadRequestException("'" + zipName + "' is not a valid zip file");
            }
            List<? extends ZipEntry> media = zip.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> !e.getName().startsWith("__MACOSX/"))
                    .filter(e -> !MediaTypeResolver.baseName(e.getName()).startsWith("."))
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
