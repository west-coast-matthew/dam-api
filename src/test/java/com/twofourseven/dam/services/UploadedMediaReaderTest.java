package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadedMediaReaderTest {

    private final UploadedMediaReader reader = new UploadedMediaReader(new StorageProperties("/unused", DataSize.ofBytes(100)));

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
}
