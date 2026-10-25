package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.LightboxService;
import com.twofourseven.dam.vo.LightboxInstanceDto;
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

/** Lightbox management. List with ?catalogId= and/or ?userId= to filter. */
@RestController
@RequestMapping("/lightboxes")
public class LightboxController {

    private final LightboxService service;

    public LightboxController(LightboxService service) {
        this.service = service;
    }

    @GetMapping
    public List<LightboxInstanceDto> list(@RequestParam(required = false) UUID catalogId, @RequestParam(required = false) UUID userId) {
        return service.list(catalogId, userId);
    }

    @GetMapping("/{id}")
    public LightboxInstanceDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<LightboxInstanceDto> create(@Valid @RequestBody LightboxInstanceDto dto) {
        LightboxInstanceDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/lightboxes/" + created.id())).body(created);
    }

    /** Replaces the editable fields. */
    @PutMapping("/{id}")
    public LightboxInstanceDto update(@PathVariable UUID id, @Valid @RequestBody LightboxInstanceDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
