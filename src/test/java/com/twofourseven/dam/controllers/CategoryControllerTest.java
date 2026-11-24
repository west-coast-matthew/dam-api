package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.CategoryService;
import com.twofourseven.dam.vo.CategoryChangeSetVO;
import com.twofourseven.dam.vo.CategoryInstanceDto;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(service.list(catalogId)).thenReturn(List.of(new CategoryInstanceDto(id, catalogId, null, "Animals", "animals", null, 0, false, 0)));

        mockMvc.perform(get("/categories").param("catalogId", catalogId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("animals"));
    }

    @Test
    void defaultCategoryForCatalog() throws Exception {
        when(service.defaultCategory(catalogId)).thenReturn(new CategoryInstanceDto(id, catalogId, null, "Default", "default", null, 0, true, 0));

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
        when(service.update(eq(id), any())).thenReturn(new CategoryInstanceDto(id, catalogId, parentId, "Dogs", "dogs", null, 0, false, 0));

        mockMvc.perform(put("/categories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + parentId + "\",\"name\":\"Dogs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));
    }

    @Test
    void applyChangesPassesTheChangeSetThrough() throws Exception {
        when(service.applyChanges(eq(catalogId), any())).thenReturn(
                List.of(new CategoryInstanceDto(id, catalogId, null, "Animals", "animals", "All animals", 0, false, 3)));

        mockMvc.perform(post("/categories/changes").param("catalogId", catalogId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"created\":[{\"tempId\":\"t1\",\"parentId\":null,\"name\":\"Animals\",\"position\":0}],"
                                + "\"updated\":[],\"deleted\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("All animals"))
                .andExpect(jsonPath("$[0].assetCount").value(3));

        ArgumentCaptor<CategoryChangeSetVO> sent = ArgumentCaptor.forClass(CategoryChangeSetVO.class);
        verify(service).applyChanges(eq(catalogId), sent.capture());
        assertThat(sent.getValue().created()).extracting(CategoryChangeSetVO.Created::tempId).containsExactly("t1");
    }

    @Test
    void applyChangesReportsConflicts() throws Exception {
        when(service.applyChanges(eq(catalogId), any())).thenThrow(new ConflictException("There are two categories named 'Dogs' under the top level"));

        mockMvc.perform(post("/categories/changes").param("catalogId", catalogId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("There are two categories named 'Dogs' under the top level"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/categories/{id}", id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }
}
