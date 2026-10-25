package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.LightboxRepository;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.CatalogInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for catalogs. Names are unique (case-insensitive); a catalog never changes workspace.
 * A new catalog gets its default category (see {@link Category#createDefaultFor}).
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogRepository catalogRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AssetRepository assetRepository;
    private final LightboxRepository lightboxRepository;

    public CatalogService(CatalogRepository catalogRepository, WorkspaceRepository workspaceRepository,
                          AssetRepository assetRepository, LightboxRepository lightboxRepository) {
        this.catalogRepository = catalogRepository;
        this.workspaceRepository = workspaceRepository;
        this.assetRepository = assetRepository;
        this.lightboxRepository = lightboxRepository;
    }

    /** All catalogs, or only those in {@code workspaceId} when it is not null. */
    @Transactional(readOnly = true)
    public List<CatalogInstanceDto> list(UUID workspaceId) {
        List<Catalog> catalogs = workspaceId == null
                ? catalogRepository.findAllByOrderByNameAsc()
                : catalogRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
        return catalogs.stream().map(CatalogService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CatalogInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public CatalogInstanceDto create(CatalogInstanceDto dto) {
        if (dto.workspaceId() == null) {
            throw new BadRequestException("workspaceId is required");
        }
        Workspace workspace = workspaceRepository.findById(dto.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace " + dto.workspaceId() + " not found"));
        String name = Strings.trim(dto.name());
        if (catalogRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A catalog named '" + name + "' already exists");
        }
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setDescription(Strings.trim(dto.description()));
        workspace.addCatalog(catalog);
        Category.createDefaultFor(catalog);
        Catalog saved = catalogRepository.save(catalog);
        log.info("Created catalog '{}' ({}) in workspace {}", saved.getName(), saved.getId(), workspace.getId());
        return toDto(saved);
    }

    @Transactional
    public CatalogInstanceDto update(UUID id, CatalogInstanceDto dto) {
        Catalog catalog = find(id);
        if (dto.workspaceId() != null && !dto.workspaceId().equals(catalog.getWorkspace().getId())) {
            throw new BadRequestException("A catalog cannot be moved to another workspace");
        }
        String name = Strings.trim(dto.name());
        if (catalogRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A catalog named '" + name + "' already exists");
        }
        catalog.setName(name);
        catalog.setDescription(Strings.trim(dto.description()));
        return toDto(catalogRepository.save(catalog));
    }

    /** @throws ConflictException if the catalog still has assets or lightboxes */
    @Transactional
    public void delete(UUID id) {
        Catalog catalog = find(id);
        if (assetRepository.existsByCatalogId(id)) {
            throw new ConflictException("Catalog '" + catalog.getName() + "' still has assets; delete them first");
        }
        if (lightboxRepository.existsByCatalogId(id)) {
            throw new ConflictException("Catalog '" + catalog.getName() + "' still has lightboxes; delete them first");
        }
        // Categories and tags are removed by the cascade
        catalog.getWorkspace().getCatalogs().remove(catalog);
        catalogRepository.delete(catalog);
        log.info("Deleted catalog '{}' ({})", catalog.getName(), id);
    }

    private Catalog find(UUID id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + id + " not found"));
    }

    static CatalogInstanceDto toDto(Catalog c) {
        return new CatalogInstanceDto(c.getId(), c.getWorkspace().getId(), c.getName(), c.getDescription(), c.getCreatedAt());
    }
}
