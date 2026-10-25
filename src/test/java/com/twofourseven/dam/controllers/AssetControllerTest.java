package com.twofourseven.dam.controllers;

import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.AssetService;
import com.twofourseven.dam.vo.AssetInstanceDto;
import com.twofourseven.dam.vo.AssetVersionVO;
import com.twofourseven.dam.vo.BulkUploadResultVO;
import com.twofourseven.dam.vo.SkippedFileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    @Mock
    private AssetService service;
    @InjectMocks
    private AssetController controller;

    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();
    private final UUID catalogId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AssetInstanceDto dto(int version) {
        return new AssetInstanceDto(id, catalogId, "Photos", UUID.randomUUID(), "sunset.jpg",
                com.twofourseven.dam.entities.core.MediaType.IMAGE, "image/jpeg", 5L, AssetStatus.PENDING_PROCESSING,
                true, version, Map.of("k", "v"), List.of(), List.of("Nature"), LocalDateTime.now());
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "sunset.jpg", "image/jpeg", "jpeg!".getBytes());
    }

    @Test
    void uploadCreatesAsset() throws Exception {
        when(service.create(any(), any())).thenReturn(dto(1));
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\",\"versioningEnabled\":true,\"tags\":[\"Nature\"]}").getBytes());

        mockMvc.perform(multipart("/assets").file(file()).file(asset))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/assets/" + id))
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.tags[0]").value("Nature"));

        ArgumentCaptor<AssetInstanceDto> dtoCaptor = ArgumentCaptor.forClass(AssetInstanceDto.class);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(service).create(dtoCaptor.capture(), fileCaptor.capture());
        assertThat(dtoCaptor.getValue().catalogId()).isEqualTo(catalogId);
        assertThat(dtoCaptor.getValue().versioningEnabled()).isTrue();
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("sunset.jpg");
    }

    @Test
    void bulkUploadReturns201WithCreatedAndSkipped() throws Exception {
        when(service.createBulk(any(), any())).thenReturn(new BulkUploadResultVO(
                List.of(dto(1)), List.of(new SkippedFileVO("notes.txt", "Unsupported file type '.txt'"))));
        MockMultipartFile zip = new MockMultipartFile("file", "batch.zip", "application/zip", new byte[]{1});
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets/bulk").file(zip).file(asset))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].originalFilename").value("sunset.jpg"))
                .andExpect(jsonPath("$.skipped[0].path").value("notes.txt"));

        ArgumentCaptor<AssetInstanceDto> captor = ArgumentCaptor.forClass(AssetInstanceDto.class);
        verify(service).createBulk(captor.capture(), any());
        assertThat(captor.getValue().catalogId()).isEqualTo(catalogId);
    }

    @Test
    void bulkUploadWithoutZipIs400() throws Exception {
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets/bulk").file(asset)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadWithoutFileIs400() throws Exception {
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets").file(asset)).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedFileIs400() throws Exception {
        when(service.create(any(), any())).thenThrow(new BadRequestException("Audio files are not supported yet ('a.mp3')"));
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets").file(file()).file(asset))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Audio files are not supported yet ('a.mp3')"));
    }

    @Test
    void replaceFileReturnsNewVersion() throws Exception {
        when(service.replaceFile(eq(id), any())).thenReturn(dto(2));

        mockMvc.perform(multipart(HttpMethod.PUT, "/assets/{id}/file", id).file(file()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    void updateMetadataWithJson() throws Exception {
        when(service.update(eq(id), any())).thenReturn(dto(1));

        mockMvc.perform(put("/assets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versioningEnabled\":false,\"customMetadata\":{\"k\":\"v\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customMetadata.k").value("v"));

        ArgumentCaptor<AssetInstanceDto> captor = ArgumentCaptor.forClass(AssetInstanceDto.class);
        verify(service).update(eq(id), captor.capture());
        assertThat(captor.getValue().versioningEnabled()).isFalse();
        assertThat(captor.getValue().tags()).isNull();
    }

    @Test
    void listVersions() throws Exception {
        when(service.versions(id)).thenReturn(List.of(new AssetVersionVO(UUID.randomUUID(), id, 1, 10L, LocalDateTime.now())));

        mockMvc.perform(get("/assets/{id}/versions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(1));
    }

    @Test
    void listByCatalog() throws Exception {
        when(service.list(catalogId)).thenReturn(List.of(dto(1)));

        mockMvc.perform(get("/assets").param("catalogId", catalogId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("sunset.jpg"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/assets/{id}", id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }
}
