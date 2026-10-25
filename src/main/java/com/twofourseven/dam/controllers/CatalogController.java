package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.CatalogService;
import com.twofourseven.dam.vo.CatalogInstanceDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Catalog management. List with ?workspaceId= to filter. */
@RestController
@RequestMapping("/catalogs")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<CatalogInstanceDto> list(@RequestParam(required = false) UUID workspaceId) {
        return service.list(workspaceId);
    }

    @GetMapping("/{id}")
    public CatalogInstanceDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<CatalogInstanceDto> create(@Valid @RequestBody CatalogInstanceDto dto) {
        CatalogInstanceDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/catalogs/" + created.id())).body(created);
    }

    /** Replaces the editable fields. */
    @PutMapping("/{id}")
    public CatalogInstanceDto update(@PathVariable UUID id, @Valid @RequestBody CatalogInstanceDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
