package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.services.AssetDownloadService;
import com.twofourseven.dam.services.AssetDownloadService.DownloadBundle;
import com.twofourseven.dam.services.LightboxService;
import com.twofourseven.dam.vo.AssetPageVO;
import com.twofourseven.dam.vo.LightboxInstanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LightboxControllerTest {

    @Mock
    private LightboxService service;
    @Mock
    private AssetDownloadService downloadService;
    @InjectMocks
    private LightboxController controller;

    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();
    private final UUID catalogId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listFiltersByUser() throws Exception {
        when(service.list(null, userId)).thenReturn(List.of(
                new LightboxInstanceDto(id, catalogId, userId, "Picks", null, true, 3, null)));

        mockMvc.perform(get("/lightboxes").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shared").value(true))
                .andExpect(jsonPath("$[0].itemCount").value(3));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(new LightboxInstanceDto(id, catalogId, userId, "Picks", null, false, 0, null));

        mockMvc.perform(post("/lightboxes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"catalogId\":\"" + catalogId + "\",\"userId\":\"" + userId + "\",\"title\":\"Picks\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Picks"));
    }

    @Test
    void createWithoutTitleIs400() throws Exception {
        mockMvc.perform(post("/lightboxes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"catalogId\":\"" + catalogId + "\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("title: Lightbox title is required"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/lightboxes/{id}", id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }

    // ---- items (spec 009) ----

    private final UUID assetId = UUID.randomUUID();
    private String body() {
        return "{\"assetIds\":[\"" + assetId + "\"]}";
    }

    @Test
    void listItemsReturnsAPage() throws Exception {
        when(service.items(id, 1, 12)).thenReturn(new AssetPageVO(List.of(), 1, 12, 0, 0));

        mockMvc.perform(get("/lightboxes/{id}/items", id).param("page", "1").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(12));
    }

    @Test
    void addItemsReturnsTheLightbox() throws Exception {
        when(service.addItems(id, List.of(assetId)))
                .thenReturn(new LightboxInstanceDto(id, catalogId, userId, "Picks", null, false, 1, null));

        mockMvc.perform(post("/lightboxes/{id}/items", id).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1));
    }

    @Test
    void addItemsFromAnotherCatalogIs400() throws Exception {
        when(service.addItems(id, List.of(assetId))).thenThrow(new BadRequestException("not in catalog"));

        mockMvc.perform(post("/lightboxes/{id}/items", id).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeItemReturns204Or404() throws Exception {
        mockMvc.perform(delete("/lightboxes/{id}/items/{assetId}", id, assetId))
                .andExpect(status().isNoContent());
        verify(service).removeItem(id, assetId);

        UUID other = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("not in lightbox")).when(service).removeItem(id, other);
        mockMvc.perform(delete("/lightboxes/{id}/items/{assetId}", id, other))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItemsReturnsTheLightbox() throws Exception {
        when(service.removeItems(id, List.of(assetId)))
                .thenReturn(new LightboxInstanceDto(id, catalogId, userId, "Picks", null, false, 0, null));

        mockMvc.perform(post("/lightboxes/{id}/items/remove", id).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void reorderWithWrongIdsIs400() throws Exception {
        when(service.reorder(id, List.of(assetId))).thenThrow(new BadRequestException("must list each"));

        mockMvc.perform(put("/lightboxes/{id}/items/order", id).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadStreamsAZipAttachment() throws Exception {
        DownloadBundle bundle = new DownloadBundle("picks.zip", List.of());
        when(service.download(id)).thenReturn(bundle);

        var started = mockMvc.perform(get("/lightboxes/{id}/download", id))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("picks.zip")));
        verify(downloadService).writeZip(any(), any());
    }

    @Test
    void downloadOfUnknownLightboxIs404() throws Exception {
        when(service.download(id)).thenThrow(new ResourceNotFoundException("Lightbox not found"));

        mockMvc.perform(get("/lightboxes/{id}/download", id)).andExpect(status().isNotFound());
    }
}
