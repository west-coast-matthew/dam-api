package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.CategoryChangeSetVO;
import com.twofourseven.dam.vo.CategoryInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD for categories. Top-level category names are unique (case-insensitive) within their catalog.
 * A category's parent must be in the same catalog and cannot be one of its own descendants.
 *
 * <p>Each catalog has a default category (see {@link #defaultCategory(UUID)}). It can be renamed but
 * not deleted or moved under another category.
 *
 * <p>{@link #applyChanges(UUID, CategoryChangeSetVO)} applies a whole editor session at once; it is
 * stricter, requiring names to be unique among all siblings, not just at the top level.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    /** Stands in for "no parent" when grouping siblings. */
    private static final Object TOP_LEVEL = new Object();

    private final CategoryRepository categoryRepository;
    private final CatalogRepository catalogRepository;

    public CategoryService(CategoryRepository categoryRepository, CatalogRepository catalogRepository) {
        this.categoryRepository = categoryRepository;
        this.catalogRepository = catalogRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryInstanceDto> list(UUID catalogId) {
        return categoryRepository.findByCatalogIdOrderByPositionAscNameAsc(catalogId).stream().map(CategoryService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CategoryInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    /**
     * The default category of a catalog. Created if missing (e.g. a catalog from before defaults existed).
     *
     * @throws ResourceNotFoundException if the catalog doesn't exist
     */
    @Transactional
    public CategoryInstanceDto defaultCategory(UUID catalogId) {
        Catalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + catalogId + " not found"));
        return toDto(defaultCategoryOf(catalog));
    }

    /** The default category entity of {@code catalog}, created and saved if missing. */
    @Transactional
    public Category defaultCategoryOf(Catalog catalog) {
        return categoryRepository.findFirstByCatalogIdAndIsDefaultTrue(catalog.getId()).orElseGet(() -> {
            log.info("Catalog {} has no default category; creating it", catalog.getId());
            return categoryRepository.save(Category.createDefaultFor(catalog));
        });
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
        category.setPosition(dto.position() != null ? dto.position() : (int) siblingCount(category));
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
        if (dto.position() != null) {
            category.setPosition(dto.position());
        }
        if (category.getParent() == null
                && categoryRepository.existsByCatalogIdAndParentIsNullAndNameIgnoreCaseAndIdNot(catalogId, category.getName(), id)) {
            throw duplicateTopLevel(category.getName());
        }
        return toDto(categoryRepository.save(category));
    }

    /**
     * Removes the category from its assets; assets left without a category move to the catalog's
     * default category. @throws ConflictException if it is the default or has subcategories
     */
    @Transactional
    public void delete(UUID id) {
        Category category = find(id);
        if (Boolean.TRUE.equals(category.getIsDefault())) {
            throw new ConflictException("The default category of a catalog cannot be deleted");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Category '" + category.getName() + "' has subcategories; delete or move them first");
        }
        // Asset owns the asset_categories join table, so unlink from that side
        Category fallback = null;
        for (Asset asset : category.getAssets()) {
            asset.getCategories().remove(category);
            if (asset.getCategories().isEmpty()) {
                if (fallback == null) {
                    fallback = defaultCategoryOf(category.getCatalog());
                }
                asset.getCategories().add(fallback);
            }
        }
        if (category.getParent() != null) {
            category.getParent().getChildren().remove(category);
        }
        categoryRepository.delete(category);
        log.info("Deleted category '{}' ({})", category.getName(), id);
    }

    /**
     * Applies a category editor's change set to one catalog in a single transaction and returns the
     * catalog's categories afterwards. Nothing is saved if any part is invalid.
     *
     * <p>Deleting a category deletes its descendants (as they are after the moves in this change set);
     * assets left without a category move to the catalog's default category. Parents may be existing
     * categories or ones created in the same change set (by {@code tempId}).
     *
     * @throws ResourceNotFoundException if the catalog, or a category being changed or deleted, doesn't exist
     * @throws BadRequestException       for invalid names, unknown parents, cycles, or moving the default category
     * @throws ConflictException         when deleting the default category or when two siblings share a name
     */
    @Transactional
    public List<CategoryInstanceDto> applyChanges(UUID catalogId, CategoryChangeSetVO changes) {
        Catalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + catalogId + " not found"));
        Map<UUID, Category> existing = new LinkedHashMap<>();
        categoryRepository.findByCatalogIdOrderByPositionAscNameAsc(catalogId).forEach(c -> existing.put(c.getId(), c));

        Set<Category> deleted = new HashSet<>();
        for (UUID id : changes.deleted()) {
            Category category = existing.get(id);
            if (category == null) {
                throw new ResourceNotFoundException("A category you deleted no longer exists. Reload and try again.");
            }
            if (Boolean.TRUE.equals(category.getIsDefault())) {
                throw new ConflictException("The default category of a catalog cannot be deleted");
            }
            deleted.add(category);
        }

        // Where each changed or new category should end up; resolved once every new category exists.
        Map<Category, String> parentRefs = new LinkedHashMap<>();
        List<Category> updated = new ArrayList<>();
        for (CategoryChangeSetVO.Updated u : changes.updated()) {
            Category category = u.id() == null ? null : existing.get(u.id());
            if (category == null) {
                throw new ResourceNotFoundException("A category you changed no longer exists. Reload and try again.");
            }
            if (deleted.contains(category)) {
                throw new BadRequestException("Category '" + category.getName() + "' can't be both changed and deleted");
            }
            String oldName = category.getName();
            setNameAndDescription(category, u.name(), u.description());
            if (!category.getName().equals(oldName)) {
                category.setSlug(Strings.slugify(category.getName()));
            }
            if (u.position() != null) {
                category.setPosition(u.position());
            }
            parentRefs.put(category, u.parentId());
            updated.add(category);
        }

        Map<String, Category> created = new LinkedHashMap<>();
        for (CategoryChangeSetVO.Created n : changes.created()) {
            String tempId = Strings.trim(n.tempId());
            if (tempId == null || tempId.isEmpty()) {
                throw new BadRequestException("Every new category needs a tempId");
            }
            if (created.containsKey(tempId)) {
                throw new BadRequestException("tempId '" + tempId + "' is used twice");
            }
            Category category = new Category();
            category.setCatalog(catalog);
            setNameAndDescription(category, n.name(), n.description());
            category.setSlug(Strings.slugify(category.getName()));
            category.setPosition(n.position() == null ? 0 : n.position());
            created.put(tempId, category);
            parentRefs.put(category, n.parentId());
        }

        for (Map.Entry<Category, String> entry : parentRefs.entrySet()) {
            Category category = entry.getKey();
            Category parent = resolveParent(entry.getValue(), category, created, existing);
            if (parent != null && Boolean.TRUE.equals(category.getIsDefault())) {
                throw new BadRequestException("The default category must stay top-level");
            }
            setParent(category, parent);
        }

        List<Category> all = new ArrayList<>(existing.values());
        all.addAll(created.values());
        Map<Category, Integer> depth = new HashMap<>();
        for (Category category : all) {
            depth.put(category, depthOf(category));
        }

        // A category goes when it, or any of its ancestors, is deleted.
        Set<Category> doomed = new HashSet<>();
        for (Category category : all) {
            for (Category c = category; c != null; c = c.getParent()) {
                if (deleted.contains(c)) {
                    doomed.add(category);
                    break;
                }
            }
        }
        List<Category> survivors = all.stream().filter(c -> !doomed.contains(c)).toList();
        assertUniqueSiblingNames(survivors);

        // Children first, so no row is deleted while another still points at it.
        List<Category> toDelete = existing.values().stream().filter(doomed::contains)
                .sorted(Comparator.comparing(depth::get).reversed()).toList();
        for (Category category : toDelete) {
            deleteWithAssetFallback(category, catalog);
        }
        // Parents first, so every new row's parent already exists.
        List<Category> toCreate = created.values().stream().filter(c -> !doomed.contains(c))
                .sorted(Comparator.comparing(depth::get)).toList();
        toCreate.forEach(categoryRepository::save);
        categoryRepository.saveAll(updated.stream().filter(c -> !doomed.contains(c)).toList());

        log.info("Applied category changes to catalog {}: {} created, {} updated, {} deleted",
                catalogId, toCreate.size(), updated.size(), toDelete.size());
        return survivors.stream()
                .sorted(Comparator.comparing(Category::getPosition).thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(CategoryService::toDto)
                .toList();
    }

    private static void setNameAndDescription(Category category, String rawName, String rawDescription) {
        String name = Strings.trim(rawName);
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("Category name is required");
        }
        if (name.length() > Category.NAME_MAX_LENGTH) {
            throw new BadRequestException("Category name must be at most " + Category.NAME_MAX_LENGTH + " characters");
        }
        String description = blankToNull(rawDescription);
        if (description != null && description.length() > Category.DESCRIPTION_MAX_LENGTH) {
            throw new BadRequestException("Category description must be at most " + Category.DESCRIPTION_MAX_LENGTH + " characters");
        }
        category.setName(name);
        category.setDescription(description);
    }

    /** A parent reference is a tempId from this change set, an existing category's id, or null for top level. */
    private static Category resolveParent(String ref, Category child, Map<String, Category> created, Map<UUID, Category> existing) {
        String key = Strings.trim(ref);
        if (key == null || key.isEmpty()) {
            return null;
        }
        Category parent = created.get(key);
        if (parent == null) {
            try {
                parent = existing.get(UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                parent = null;
            }
        }
        if (parent == null) {
            throw new BadRequestException("The parent of '" + child.getName() + "' no longer exists. Reload and try again.");
        }
        return parent;
    }

    /** Levels above the category (0 = top level). @throws BadRequestException if it is inside itself */
    private static int depthOf(Category category) {
        Set<Category> seen = new HashSet<>();
        int depth = 0;
        for (Category c = category.getParent(); c != null; c = c.getParent()) {
            if (c == category || !seen.add(c)) {
                throw new BadRequestException("'" + category.getName() + "' can't be moved inside itself");
            }
            depth++;
        }
        return depth;
    }

    private static void assertUniqueSiblingNames(List<Category> categories) {
        // Keyed by parent; the top level uses the catalog-wide key TOP_LEVEL.
        Map<Object, Set<String>> namesByParent = new HashMap<>();
        for (Category c : categories) {
            Category parent = c.getParent();
            Set<String> names = namesByParent.computeIfAbsent(parent == null ? TOP_LEVEL : parent, k -> new HashSet<>());
            if (!names.add(c.getName().toLowerCase(Locale.ROOT))) {
                String where = parent == null ? "the top level" : "'" + parent.getName() + "'";
                throw new ConflictException("There are two categories named '" + c.getName() + "' under " + where);
            }
        }
    }

    /** Unlinks the category's assets (moving any left without a category to the default) and deletes it. */
    private void deleteWithAssetFallback(Category category, Catalog catalog) {
        Category fallback = null;
        for (Asset asset : new ArrayList<>(category.getAssets())) {
            asset.getCategories().remove(category);
            if (asset.getCategories().isEmpty()) {
                if (fallback == null) {
                    fallback = defaultCategoryOf(catalog);
                }
                asset.getCategories().add(fallback);
                fallback.getAssets().add(asset);
            }
        }
        category.getAssets().clear();
        if (category.getParent() != null) {
            category.getParent().getChildren().remove(category);
        }
        categoryRepository.delete(category);
    }

    /** Copies name, slug, description and parent from the request onto the entity. */
    private void apply(Category category, CategoryInstanceDto dto) {
        String name = Strings.trim(dto.name());
        String slug = Strings.trim(dto.slug());
        category.setName(name);
        category.setSlug(slug == null || slug.isEmpty() ? Strings.slugify(name) : slug);
        category.setDescription(blankToNull(dto.description()));

        Category parent = dto.parentId() == null ? null : categoryRepository.findById(dto.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent category " + dto.parentId() + " not found"));
        if (parent != null) {
            if (Boolean.TRUE.equals(category.getIsDefault())) {
                throw new BadRequestException("The default category must stay top-level");
            }
            if (!parent.getCatalog().getId().equals(category.getCatalog().getId())) {
                throw new BadRequestException("Parent category must be in the same catalog");
            }
            for (Category c = parent; c != null; c = c.getParent()) {
                if (c.getId().equals(category.getId())) {
                    throw new BadRequestException("A category cannot be its own parent or descendant");
                }
            }
        }
        setParent(category, parent);
    }

    /** Moves {@code category} under {@code parent} (null = top level), keeping both sides of the relation in step. */
    private static void setParent(Category category, Category parent) {
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

    private long siblingCount(Category category) {
        return category.getParent() == null
                ? categoryRepository.countByCatalogIdAndParentIsNull(category.getCatalog().getId())
                : categoryRepository.countByParentId(category.getParent().getId());
    }

    private static String blankToNull(String value) {
        String trimmed = Strings.trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
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
        return new CategoryInstanceDto(c.getId(), c.getCatalog().getId(), parentId, c.getName(), c.getSlug(),
                c.getDescription(), c.getPosition(), Boolean.TRUE.equals(c.getIsDefault()), c.getAssets().size());
    }
}
