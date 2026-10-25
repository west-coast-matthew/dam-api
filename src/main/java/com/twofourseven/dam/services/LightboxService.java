package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Lightbox;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.LightboxRepository;
import com.twofourseven.dam.vo.LightboxInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for lightboxes. Managing the assets inside a lightbox is covered by spec 009.
 * Until user accounts exist (spec 006) the owner's {@code userId} is supplied by the client.
 */
@Service
public class LightboxService {

    private static final Logger log = LoggerFactory.getLogger(LightboxService.class);

    private final LightboxRepository lightboxRepository;
    private final CatalogRepository catalogRepository;

    public LightboxService(LightboxRepository lightboxRepository, CatalogRepository catalogRepository) {
        this.lightboxRepository = lightboxRepository;
        this.catalogRepository = catalogRepository;
    }

    /** Lightboxes, optionally filtered by catalog and/or owner. */
    @Transactional(readOnly = true)
    public List<LightboxInstanceDto> list(UUID catalogId, UUID userId) {
        List<Lightbox> lightboxes;
        if (catalogId != null && userId != null) {
            lightboxes = lightboxRepository.findByCatalogIdAndUserIdOrderByTitleAsc(catalogId, userId);
        } else if (catalogId != null) {
            lightboxes = lightboxRepository.findByCatalogIdOrderByTitleAsc(catalogId);
        } else if (userId != null) {
            lightboxes = lightboxRepository.findByUserIdOrderByTitleAsc(userId);
        } else {
            lightboxes = lightboxRepository.findAllByOrderByTitleAsc();
        }
        return lightboxes.stream().map(LightboxService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LightboxInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public LightboxInstanceDto create(LightboxInstanceDto dto) {
        if (dto.catalogId() == null) {
            throw new BadRequestException("catalogId is required");
        }
        if (dto.userId() == null) {
            throw new BadRequestException("userId is required");
        }
        Catalog catalog = catalogRepository.findById(dto.catalogId())
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + dto.catalogId() + " not found"));
        Lightbox lightbox = new Lightbox();
        lightbox.setCatalog(catalog);
        lightbox.setUserId(dto.userId());
        apply(lightbox, dto);
        Lightbox saved = lightboxRepository.save(lightbox);
        log.info("Created lightbox '{}' ({}) in catalog {}", saved.getTitle(), saved.getId(), catalog.getId());
        return toDto(saved);
    }

    @Transactional
    public LightboxInstanceDto update(UUID id, LightboxInstanceDto dto) {
        Lightbox lightbox = find(id);
        if (dto.catalogId() != null && !dto.catalogId().equals(lightbox.getCatalog().getId())) {
            throw new BadRequestException("A lightbox cannot be moved to another catalog");
        }
        if (dto.userId() != null && !dto.userId().equals(lightbox.getUserId())) {
            throw new BadRequestException("A lightbox's owner cannot be changed");
        }
        apply(lightbox, dto);
        return toDto(lightboxRepository.save(lightbox));
    }

    /** Deletes the lightbox and its items; the assets themselves are untouched. */
    @Transactional
    public void delete(UUID id) {
        Lightbox lightbox = find(id);
        lightboxRepository.delete(lightbox);
        log.info("Deleted lightbox '{}' ({})", lightbox.getTitle(), id);
    }

    private static void apply(Lightbox lightbox, LightboxInstanceDto dto) {
        lightbox.setTitle(Strings.trim(dto.title()));
        lightbox.setDescription(Strings.trim(dto.description()));
        lightbox.setIsShared(Boolean.TRUE.equals(dto.shared()));
    }

    private Lightbox find(UUID id) {
        return lightboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lightbox " + id + " not found"));
    }

    static LightboxInstanceDto toDto(Lightbox l) {
        return new LightboxInstanceDto(l.getId(), l.getCatalog().getId(), l.getUserId(), l.getTitle(),
                l.getDescription(), l.getIsShared(), l.getItems().size());
    }
}
