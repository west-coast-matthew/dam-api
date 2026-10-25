package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.CatalogService;
import com.twofourseven.dam.vo.CatalogInstanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private CatalogService service;
    @InjectMocks
    private CatalogController controller;

    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listFiltersByWorkspace() throws Exception {
        when(service.list(workspaceId)).thenReturn(List.of(
                new CatalogInstanceDto(id, workspaceId, "Photos", null, LocalDateTime.now())));

        mockMvc.perform(get("/catalogs").param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()));
    }

    @Test
    void listWithoutFilter() throws Exception {
        when(service.list(null)).thenReturn(List.of());

        mockMvc.perform(get("/catalogs")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createPassesPayloadToService() throws Exception {
        when(service.create(any())).thenReturn(new CatalogInstanceDto(id, workspaceId, "Photos", "d", LocalDateTime.now()));

        mockMvc.perform(post("/catalogs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":\"" + workspaceId + "\",\"name\":\"Photos\",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/catalogs/" + id));

        ArgumentCaptor<CatalogInstanceDto> captor = ArgumentCaptor.forClass(CatalogInstanceDto.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().workspaceId()).isEqualTo(workspaceId);
        assertThat(captor.getValue().name()).isEqualTo("Photos");
    }

    @Test
    void createWithoutWorkspaceIs400() throws Exception {
        when(service.create(any())).thenThrow(new BadRequestException("workspaceId is required"));

        mockMvc.perform(post("/catalogs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Photos\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("workspaceId is required"));
    }

    @Test
    void createWithMissingNameIs400() throws Exception {
        mockMvc.perform(post("/catalogs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":\"" + workspaceId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("name: Catalog name is required"));
    }
}
