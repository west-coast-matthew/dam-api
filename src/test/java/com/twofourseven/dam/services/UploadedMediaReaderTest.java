package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.vo.SkippedFileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadedMediaReaderTest {

    private final UploadedMediaReader reader = new UploadedMediaReader(new StorageProperties("/unused", DataSize.ofBytes(100), 1000));

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static String readAll(UploadedMedia media) throws Exception {
        return new String(media.content().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void plainFileIsUsedAsIs() throws Exception {
        try (UploadedMedia media = reader.read(file("photo.JPG", "jpeg-bytes".getBytes()))) {
            assertThat(media.filename()).isEqualTo("photo.JPG");
            assertThat(media.type().mediaType()).isEqualTo(MediaType.IMAGE);
            assertThat(readAll(media)).isEqualTo("jpeg-bytes");
        }
    }

    @Test
    void pathInOriginalFilenameIsStripped() throws Exception {
        try (UploadedMedia media = reader.read(file("C:\\Users\\me\\clip.mp4", "x".getBytes()))) {
            assertThat(media.filename()).isEqualTo("clip.mp4");
        }
    }

    @Test
    void zipWithOneMediaFileUsesThatFile() throws Exception {
        byte[] zip = ZipFixtures.zip(
                "docs/", "",
                "docs/brochure.pdf", "pdf-bytes",
                "readme.txt", "ignored",
                "__MACOSX/docs/._brochure.pdf", "junk",
                "docs/.DS_Store", "junk");

        try (UploadedMedia media = reader.read(file("bundle.zip", zip))) {
            assertThat(media.filename()).isEqualTo("brochure.pdf");
            assertThat(media.type().mediaType()).isEqualTo(MediaType.DOCUMENT);
            assertThat(readAll(media)).isEqualTo("pdf-bytes");
        }
    }

    @Test
    void zipWithSeveralMediaFilesIsRejected() {
        byte[] zip = ZipFixtures.zip("a.jpg", "a", "b.png", "b");

        assertThatThrownBy(() -> reader.read(file("bundle.zip", zip)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void zipWithoutMediaIsRejected() {
        byte[] zip = ZipFixtures.zip("song.mp3", "a", "notes.txt", "b");

        assertThatThrownBy(() -> reader.read(file("bundle.zip", zip)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not contain");
    }

    @Test
    void corruptZipIsRejected() {
        assertThatThrownBy(() -> reader.read(file("bundle.zip", "not a zip".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a valid zip");
    }

    @Test
    void oversizedZipEntryIsRejectedWhileReading() throws Exception {
        byte[] zip = ZipFixtures.zip("big.png", "x".repeat(101));

        try (UploadedMedia media = reader.read(file("bundle.zip", zip))) {
            assertThatThrownBy(() -> media.content().readAllBytes())
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("limit");
        }
    }

    @Test
    void emptyOrMissingFileIsRejected() {
        assertThatThrownBy(() -> reader.read(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reader.read(file("photo.jpg", new byte[0]))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void audioIsRejected() {
        assertThatThrownBy(() -> reader.read(file("song.mp3", "x".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Audio");
    }

    // ---- bulk ----

    private final UploadedMediaReader bulkReader = new UploadedMediaReader(new StorageProperties("/unused", DataSize.ofBytes(100), 3));

    private List<String> readEach(byte[] zip, List<SkippedFileVO> skippedOut) {
        List<String> seen = new ArrayList<>();
        skippedOut.addAll(bulkReader.readEach(file("batch.zip", zip), media -> {
            try {
                seen.add(media.filename() + "=" + media.type().mediaType() + "=" + new String(media.content().readAllBytes()));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }));
        return seen;
    }

    @Test
    void bulkReadsEveryMediaFileIncludingSubfolders() {
        byte[] zip = ZipFixtures.zip(
                "a.jpg", "A",
                "videos/", "",
                "videos/b.mp4", "B",
                "docs/deep/c.pdf", "C",
                "__MACOSX/._a.jpg", "junk",
                ".DS_Store", "junk");
        List<SkippedFileVO> skipped = new ArrayList<>();

        assertThat(readEach(zip, skipped)).containsExactly("a.jpg=IMAGE=A", "b.mp4=VIDEO=B", "c.pdf=DOCUMENT=C");
        assertThat(skipped).isEmpty();
    }

    @Test
    void bulkSkipsAndReportsUnsupportedFiles() {
        byte[] zip = ZipFixtures.zip("a.png", "A", "notes/readme.txt", "x", "music/song.mp3", "y");
        List<SkippedFileVO> skipped = new ArrayList<>();

        assertThat(readEach(zip, skipped)).containsExactly("a.png=IMAGE=A");
        assertThat(skipped).extracting(SkippedFileVO::path).containsExactly("notes/readme.txt", "music/song.mp3");
        assertThat(skipped.get(0).reason()).contains("Unsupported file type");
        assertThat(skipped.get(1).reason()).contains("Audio");
    }

    @Test
    void bulkWithoutMediaIsRejected() {
        assertThatThrownBy(() -> readEach(ZipFixtures.zip("readme.txt", "x"), new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not contain");
    }

    @Test
    void bulkOverFileLimitIsRejectedBeforeAnythingIsRead() {
        byte[] zip = ZipFixtures.zip("1.png", "1", "2.png", "2", "3.png", "3", "4.png", "4");
        List<String> seen = new ArrayList<>();

        assertThatThrownBy(() -> bulkReader.readEach(file("batch.zip", zip), media -> seen.add(media.filename())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit is 3");
        assertThat(seen).isEmpty();
    }

    @Test
    void bulkRequiresAZip() {
        assertThatThrownBy(() -> bulkReader.readEach(file("photo.jpg", "x".getBytes()), media -> { }))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(".zip");
        assertThatThrownBy(() -> bulkReader.readEach(file("batch.zip", "not a zip".getBytes()), media -> { }))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a valid zip");
        assertThatThrownBy(() -> bulkReader.readEach(null, media -> { }))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void bulkOversizedEntryFailsWhileReading() {
        byte[] zip = ZipFixtures.zip("big.png", "x".repeat(101));

        assertThatThrownBy(() -> readEach(zip, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit");
    }
}
