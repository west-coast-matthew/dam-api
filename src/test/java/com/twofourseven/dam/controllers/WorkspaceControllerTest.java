package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.services.WorkspaceService;
import com.twofourseven.dam.vo.WorkspaceInstanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    @Mock
    private WorkspaceService service;
    @InjectMocks
    private WorkspaceController controller;

    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();
    private final WorkspaceInstanceDto dto = new WorkspaceInstanceDto(id, "Marketing", "Brand", LocalDateTime.now());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsWorkspaces() throws Exception {
        when(service.list()).thenReturn(List.of(dto));

        mockMvc.perform(get("/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Marketing"))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void getUnknownIs404() throws Exception {
        when(service.get(id)).thenThrow(new ResourceNotFoundException("Workspace " + id + " not found"));

        mockMvc.perform(get("/workspaces/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workspace " + id + " not found"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Marketing\",\"description\":\"Brand\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/workspaces/" + id))
                .andExpect(jsonPath("$.description").value("Brand"));
    }

    @Test
    void createWithBlankNameIs400() throws Exception {
        mockMvc.perform(post("/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("name: Workspace name is required"));
        verify(service, never()).create(any());
    }

    @Test
    void createDuplicateIs409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("A workspace named 'Marketing' already exists"));

        mockMvc.perform(post("/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Marketing\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturnsUpdated() throws Exception {
        when(service.update(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/workspaces/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Marketing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Marketing"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/workspaces/{id}", id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }

    @Test
    void deleteNonEmptyIs409() throws Exception {
        doThrow(new ConflictException("still has catalogs")).when(service).delete(id);

        mockMvc.perform(delete("/workspaces/{id}", id)).andExpect(status().isConflict());
    }

    @Test
    void databaseDownIs503() throws Exception {
        when(service.list()).thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager"));

        mockMvc.perform(get("/workspaces"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Database is unavailable"));
    }

    @Test
    void invalidIdIs400() throws Exception {
        mockMvc.perform(get("/workspaces/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
