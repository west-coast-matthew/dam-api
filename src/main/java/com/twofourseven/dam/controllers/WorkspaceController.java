package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.WorkspaceService;
import com.twofourseven.dam.vo.WorkspaceInstanceDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Workspace management. */
@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkspaceInstanceDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public WorkspaceInstanceDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<WorkspaceInstanceDto> create(@Valid @RequestBody WorkspaceInstanceDto dto) {
        WorkspaceInstanceDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/workspaces/" + created.id())).body(created);
    }

    /** Replaces the editable fields. */
    @PutMapping("/{id}")
    public WorkspaceInstanceDto update(@PathVariable UUID id, @Valid @RequestBody WorkspaceInstanceDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
