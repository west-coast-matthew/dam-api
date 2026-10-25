package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.LightboxService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LightboxControllerTest {

    @Mock
    private LightboxService service;
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
                new LightboxInstanceDto(id, catalogId, userId, "Picks", null, true, 3)));

        mockMvc.perform(get("/lightboxes").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shared").value(true))
                .andExpect(jsonPath("$[0].itemCount").value(3));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(new LightboxInstanceDto(id, catalogId, userId, "Picks", null, false, 0));

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
}
