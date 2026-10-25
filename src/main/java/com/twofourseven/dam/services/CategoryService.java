package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.CategoryInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for categories. Top-level category names are unique (case-insensitive) within their catalog.
 * A category's parent must be in the same catalog and cannot be one of its own descendants.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final CatalogRepository catalogRepository;

    public CategoryService(CategoryRepository categoryRepository, CatalogRepository catalogRepository) {
        this.categoryRepository = categoryRepository;
        this.catalogRepository = catalogRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryInstanceDto> list(UUID catalogId) {
        return categoryRepository.findByCatalogIdOrderByNameAsc(catalogId).stream().map(CategoryService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CategoryInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public CategoryInstanceDto create(CategoryInstanceDto dto) {
        if (dto.catalogId() == null) {
            throw new BadRequestException("catalogId is required");
        }
        Catalog catalog = catalogRepository.findById(dto.catalogId())
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + dto.catalogId() + " not found"));
        Category category = new Category();
        category.setCatalog(catalog);
        apply(category, dto);
        if (category.getParent() == null
                && categoryRepository.existsByCatalogIdAndParentIsNullAndNameIgnoreCase(catalog.getId(), category.getName())) {
            throw duplicateTopLevel(category.getName());
        }
        Category saved = categoryRepository.save(category);
        log.info("Created category '{}' ({}) in catalog {}", saved.getName(), saved.getId(), catalog.getId());
        return toDto(saved);
    }

    @Transactional
    public CategoryInstanceDto update(UUID id, CategoryInstanceDto dto) {
        Category category = find(id);
        UUID catalogId = category.getCatalog().getId();
        if (dto.catalogId() != null && !dto.catalogId().equals(catalogId)) {
            throw new BadRequestException("A category cannot be moved to another catalog");
        }
        apply(category, dto);
        if (category.getParent() == null
                && categoryRepository.existsByCatalogIdAndParentIsNullAndNameIgnoreCaseAndIdNot(catalogId, category.getName(), id)) {
            throw duplicateTopLevel(category.getName());
        }
        return toDto(categoryRepository.save(category));
    }

    /** Removes the category from any assets. @throws ConflictException if it has subcategories */
    @Transactional
    public void delete(UUID id) {
        Category category = find(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Category '" + category.getName() + "' has subcategories; delete or move them first");
        }
        // Asset owns the asset_categories join table, so unlink from that side
        for (Asset asset : category.getAssets()) {
            asset.getCategories().remove(category);
        }
        if (category.getParent() != null) {
            category.getParent().getChildren().remove(category);
        }
        categoryRepository.delete(category);
        log.info("Deleted category '{}' ({})", category.getName(), id);
    }

    /** Copies name, slug and parent from the request onto the entity. */
    private void apply(Category category, CategoryInstanceDto dto) {
        String name = Strings.trim(dto.name());
        String slug = Strings.trim(dto.slug());
        category.setName(name);
        category.setSlug(slug == null || slug.isEmpty() ? Strings.slugify(name) : slug);

        Category parent = dto.parentId() == null ? null : categoryRepository.findById(dto.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent category " + dto.parentId() + " not found"));
        if (parent != null) {
            if (!parent.getCatalog().getId().equals(category.getCatalog().getId())) {
                throw new BadRequestException("Parent category must be in the same catalog");
            }
            for (Category c = parent; c != null; c = c.getParent()) {
                if (c.getId().equals(category.getId())) {
                    throw new BadRequestException("A category cannot be its own parent or descendant");
                }
            }
        }
        if (category.getParent() != parent) {
            if (category.getParent() != null) {
                category.getParent().getChildren().remove(category);
            }
            if (parent != null) {
                parent.getChildren().add(category);
            }
            category.setParent(parent);
        }
    }

    private Category find(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    private static ConflictException duplicateTopLevel(String name) {
        return new ConflictException("A top-level category named '" + name + "' already exists in this catalog");
    }

    static CategoryInstanceDto toDto(Category c) {
        UUID parentId = c.getParent() == null ? null : c.getParent().getId();
        return new CategoryInstanceDto(c.getId(), c.getCatalog().getId(), parentId, c.getName(), c.getSlug());
    }
}
