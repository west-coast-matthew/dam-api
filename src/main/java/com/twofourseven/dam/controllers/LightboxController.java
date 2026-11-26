package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.AssetDownloadService;
import com.twofourseven.dam.services.LightboxService;
import com.twofourseven.dam.vo.AssetPageVO;
import com.twofourseven.dam.vo.LightboxInstanceDto;
import com.twofourseven.dam.vo.LightboxItemsVO;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Lightbox management. List with ?catalogId= and/or ?userId= to filter. The assets inside a lightbox
 * are managed under {@code /lightboxes/{id}/items} (spec 009).
 */
@RestController
@RequestMapping("/lightboxes")
public class LightboxController {

    private final LightboxService service;
    private final AssetDownloadService downloadService;

    public LightboxController(LightboxService service, AssetDownloadService downloadService) {
        this.service = service;
        this.downloadService = downloadService;
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

    /** The lightbox's assets in lightbox order, paged like {@code GET /assets/search}. */
    @GetMapping("/{id}/items")
    public AssetPageVO items(@PathVariable UUID id,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        return service.items(id, page, size);
    }

    /** Appends assets; ones already in the lightbox are skipped. */
    @PostMapping("/{id}/items")
    public LightboxInstanceDto addItems(@PathVariable UUID id, @RequestBody LightboxItemsVO body) {
        return service.addItems(id, body.assetIds());
    }

    @DeleteMapping("/{id}/items/{assetId}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID id, @PathVariable UUID assetId) {
        service.removeItem(id, assetId);
        return ResponseEntity.noContent().build();
    }

    /** Removes several assets; ids not in the lightbox are ignored. */
    @PostMapping("/{id}/items/remove")
    public LightboxInstanceDto removeItems(@PathVariable UUID id, @RequestBody LightboxItemsVO body) {
        return service.removeItems(id, body.assetIds());
    }

    /** Sets the order; the list must hold exactly the lightbox's assets. */
    @PutMapping("/{id}/items/order")
    public LightboxInstanceDto reorder(@PathVariable UUID id, @RequestBody LightboxItemsVO body) {
        return service.reorder(id, body.assetIds());
    }

    /** The lightbox's items as one zip, named after its title. */
    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
        return ZipDownloads.response(downloadService, service.download(id));
    }
}
