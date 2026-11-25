package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.services.TagService;
import com.twofourseven.dam.vo.TagVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock
    private TagService service;
    @InjectMocks
    private TagController controller;

    private MockMvc mockMvc;
    private final UUID catalogId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsTagsForACatalog() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.list(catalogId, "br", 10)).thenReturn(List.of(new TagVO(id, "brand", 4)));

        mockMvc.perform(get("/tags").param("catalogId", catalogId.toString()).param("q", "br").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("brand"))
                .andExpect(jsonPath("$[0].assetCount").value(4));
    }

    @Test
    void catalogIdIsRequired() throws Exception {
        mockMvc.perform(get("/tags")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownCatalogIs404AndBadLimitIs400() throws Exception {
        when(service.list(catalogId, null, null)).thenThrow(new ResourceNotFoundException("Catalog not found"));
        mockMvc.perform(get("/tags").param("catalogId", catalogId.toString())).andExpect(status().isNotFound());

        when(service.list(catalogId, null, 500)).thenThrow(new BadRequestException("limit must be between 1 and 200"));
        mockMvc.perform(get("/tags").param("catalogId", catalogId.toString()).param("limit", "500"))
                .andExpect(status().isBadRequest());
    }
}
