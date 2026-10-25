package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.CategoryService;
import com.twofourseven.dam.vo.CategoryInstanceDto;
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

/** Category management. Listing requires ?catalogId=. */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryInstanceDto> list(@RequestParam UUID catalogId) {
        return service.list(catalogId);
    }

    @GetMapping("/{id}")
    public CategoryInstanceDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<CategoryInstanceDto> create(@Valid @RequestBody CategoryInstanceDto dto) {
        CategoryInstanceDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/categories/" + created.id())).body(created);
    }

    /** Replaces the editable fields. */
    @PutMapping("/{id}")
    public CategoryInstanceDto update(@PathVariable UUID id, @Valid @RequestBody CategoryInstanceDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
