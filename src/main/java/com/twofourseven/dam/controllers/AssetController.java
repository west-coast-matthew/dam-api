package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.AssetService;
import com.twofourseven.dam.vo.AssetInstanceDto;
import com.twofourseven.dam.vo.AssetVersionVO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Asset management.
 * <ul>
 *   <li>{@code POST /assets} — multipart: {@code file} (media file or a zip holding one) and
 *       {@code asset} (JSON {@link AssetInstanceDto}, at least {@code catalogId}).</li>
 *   <li>{@code PUT /assets/{id}} — JSON metadata update; null fields are unchanged.</li>
 *   <li>{@code PUT /assets/{id}/file} — multipart {@code file}; replaces the file, keeping the old
 *       one as a version when the asset's versioning is enabled.</li>
 * </ul>
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public List<AssetInstanceDto> list(@RequestParam(required = false) UUID catalogId) {
        return service.list(catalogId);
    }

    @GetMapping("/{id}")
    public AssetInstanceDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<AssetVersionVO> versions(@PathVariable UUID id) {
        return service.versions(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetInstanceDto> create(@RequestPart("asset") AssetInstanceDto asset,
                                                   @RequestPart("file") MultipartFile file) {
        AssetInstanceDto created = service.create(asset, file);
        return ResponseEntity.created(URI.create("/assets/" + created.id())).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssetInstanceDto update(@PathVariable UUID id, @RequestBody AssetInstanceDto asset) {
        return service.update(id, asset);
    }

    @PutMapping(path = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetInstanceDto replaceFile(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.replaceFile(id, file);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
