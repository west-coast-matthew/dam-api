package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.CategoryService;
import com.twofourseven.dam.vo.CategoryInstanceDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService service;
    @InjectMocks
    private CategoryController controller;

    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();
    private final UUID catalogId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listByCatalog() throws Exception {
        when(service.list(catalogId)).thenReturn(List.of(new CategoryInstanceDto(id, catalogId, null, "Animals", "animals", false)));

        mockMvc.perform(get("/categories").param("catalogId", catalogId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("animals"));
    }

    @Test
    void defaultCategoryForCatalog() throws Exception {
        when(service.defaultCategory(catalogId)).thenReturn(new CategoryInstanceDto(id, catalogId, null, "Default", "default", true));

        mockMvc.perform(get("/categories/default").param("catalogId", catalogId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.defaultCategory").value(true));
    }

    @Test
    void defaultCategoryRequiresCatalogId() throws Exception {
        mockMvc.perform(get("/categories/default")).andExpect(status().isBadRequest());
    }

    @Test
    void listWithoutCatalogIs400() throws Exception {
        mockMvc.perform(get("/categories")).andExpect(status().isBadRequest());
    }

    @Test
    void createDuplicateTopLevelIs409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("A top-level category named 'Animals' already exists"));

        mockMvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"catalogId\":\"" + catalogId + "\",\"name\":\"Animals\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A top-level category named 'Animals' already exists"));
    }

    @Test
    void createWithBlankNameIs400() throws Exception {
        mockMvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"catalogId\":\"" + catalogId + "\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMovesUnderParent() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(new CategoryInstanceDto(id, catalogId, parentId, "Dogs", "dogs", false));

        mockMvc.perform(put("/categories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + parentId + "\",\"name\":\"Dogs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/categories/{id}", id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }
}
