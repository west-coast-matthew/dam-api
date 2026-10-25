package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.CategoryInstanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @InjectMocks
    private CategoryService service;

    private final Catalog catalog = Fixtures.catalog("Photos");

    private void saveReturnsArgument() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTopLevelDerivesSlug() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        saveReturnsArgument();

        CategoryInstanceDto result = service.create(CategoryInstanceDto.request(catalog.getId(), null, " Café Photos! ", null));

        assertThat(result.name()).isEqualTo("Café Photos!");
        assertThat(result.slug()).isEqualTo("cafe-photos");
        assertThat(result.parentId()).isNull();
        assertThat(result.catalogId()).isEqualTo(catalog.getId());
    }

    @Test
    void createKeepsSuppliedSlug() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        saveReturnsArgument();

        CategoryInstanceDto result = service.create(CategoryInstanceDto.request(catalog.getId(), null, "Dogs", "k9"));

        assertThat(result.slug()).isEqualTo("k9");
    }

    @Test
    void duplicateTopLevelNameInCatalogIsConflict() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.existsByCatalogIdAndParentIsNullAndNameIgnoreCase(catalog.getId(), "Animals")).thenReturn(true);

        assertThatThrownBy(() -> service.create(CategoryInstanceDto.request(catalog.getId(), null, "Animals", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("top-level");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void childNameIsNotCheckedAgainstTopLevelNames() {
        Category parent = Fixtures.category(catalog, null, "Animals");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        saveReturnsArgument();

        CategoryInstanceDto result = service.create(CategoryInstanceDto.request(catalog.getId(), parent.getId(), "Animals", null));

        assertThat(result.parentId()).isEqualTo(parent.getId());
        assertThat(parent.getChildren()).extracting(Category::getName).containsExactly("Animals");
        verify(categoryRepository, never()).existsByCatalogIdAndParentIsNullAndNameIgnoreCase(any(), anyString());
    }

    @Test
    void parentFromAnotherCatalogIsBadRequest() {
        Category foreignParent = Fixtures.category(Fixtures.catalog("Other"), null, "X");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findById(foreignParent.getId())).thenReturn(Optional.of(foreignParent));

        assertThatThrownBy(() -> service.create(CategoryInstanceDto.request(catalog.getId(), foreignParent.getId(), "Dogs", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("same catalog");
    }

    @Test
    void cannotMoveUnderOwnDescendant() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category dogs = Fixtures.category(catalog, animals, "Dogs");
        when(categoryRepository.findById(animals.getId())).thenReturn(Optional.of(animals));
        when(categoryRepository.findById(dogs.getId())).thenReturn(Optional.of(dogs));

        assertThatThrownBy(() -> service.update(animals.getId(), CategoryInstanceDto.request(null, dogs.getId(), "Animals", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("descendant");
    }

    @Test
    void movingToTopLevelChecksUniqueness() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category dogs = Fixtures.category(catalog, animals, "Dogs");
        when(categoryRepository.findById(dogs.getId())).thenReturn(Optional.of(dogs));
        when(categoryRepository.existsByCatalogIdAndParentIsNullAndNameIgnoreCaseAndIdNot(catalog.getId(), "Dogs", dogs.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(dogs.getId(), CategoryInstanceDto.request(null, null, "Dogs", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateMovesBetweenParents() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category pets = Fixtures.category(catalog, null, "Pets");
        Category dogs = Fixtures.category(catalog, animals, "Dogs");
        when(categoryRepository.findById(dogs.getId())).thenReturn(Optional.of(dogs));
        when(categoryRepository.findById(pets.getId())).thenReturn(Optional.of(pets));
        saveReturnsArgument();

        CategoryInstanceDto result = service.update(dogs.getId(), CategoryInstanceDto.request(catalog.getId(), pets.getId(), "Dogs", null));

        assertThat(result.parentId()).isEqualTo(pets.getId());
        assertThat(animals.getChildren()).isEmpty();
        assertThat(pets.getChildren()).containsExactly(dogs);
    }

    @Test
    void updateCannotChangeCatalog() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        when(categoryRepository.findById(animals.getId())).thenReturn(Optional.of(animals));

        assertThatThrownBy(() -> service.update(animals.getId(),
                CategoryInstanceDto.request(Fixtures.catalog("Other").getId(), null, "Animals", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteWithChildrenIsConflict() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        when(categoryRepository.findById(animals.getId())).thenReturn(Optional.of(animals));
        when(categoryRepository.existsByParentId(animals.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(animals.getId())).isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteMovesAssetsLeftWithoutCategoryToDefault() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category pets = Fixtures.category(catalog, null, "Pets");
        Asset onlyAnimals = new Asset();
        Asset animalsAndPets = new Asset();
        onlyAnimals.getCategories().add(animals);
        animalsAndPets.getCategories().addAll(java.util.List.of(animals, pets));
        animals.getAssets().addAll(java.util.List.of(onlyAnimals, animalsAndPets));
        when(categoryRepository.findById(animals.getId())).thenReturn(Optional.of(animals));
        when(categoryRepository.findFirstByCatalogIdAndIsDefaultTrue(catalog.getId())).thenReturn(Optional.of(defaultCategory));

        service.delete(animals.getId());

        assertThat(onlyAnimals.getCategories()).containsExactly(defaultCategory);
        assertThat(animalsAndPets.getCategories()).containsExactly(pets);
        verify(categoryRepository).delete(eq(animals));
    }

    @Test
    void defaultCategoryCannotBeDeleted() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        when(categoryRepository.findById(defaultCategory.getId())).thenReturn(Optional.of(defaultCategory));

        assertThatThrownBy(() -> service.delete(defaultCategory.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("default category");
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void defaultCategoryCannotBeNested() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        Category animals = Fixtures.category(catalog, null, "Animals");
        when(categoryRepository.findById(defaultCategory.getId())).thenReturn(Optional.of(defaultCategory));
        when(categoryRepository.findById(animals.getId())).thenReturn(Optional.of(animals));

        assertThatThrownBy(() -> service.update(defaultCategory.getId(),
                CategoryInstanceDto.request(null, animals.getId(), "Default", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("top-level");
    }

    @Test
    void defaultCategoryCanBeRenamed() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        when(categoryRepository.findById(defaultCategory.getId())).thenReturn(Optional.of(defaultCategory));
        saveReturnsArgument();

        CategoryInstanceDto result = service.update(defaultCategory.getId(), CategoryInstanceDto.request(null, null, "Inbox", null));

        assertThat(result.name()).isEqualTo("Inbox");
        assertThat(result.defaultCategory()).isTrue();
    }

    @Test
    void defaultCategoryReturnsExisting() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findFirstByCatalogIdAndIsDefaultTrue(catalog.getId())).thenReturn(Optional.of(defaultCategory));

        CategoryInstanceDto result = service.defaultCategory(catalog.getId());

        assertThat(result.id()).isEqualTo(defaultCategory.getId());
        assertThat(result.name()).isEqualTo(Category.DEFAULT_NAME);
        assertThat(result.defaultCategory()).isTrue();
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void defaultCategoryIsCreatedWhenMissing() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findFirstByCatalogIdAndIsDefaultTrue(catalog.getId())).thenReturn(Optional.empty());
        saveReturnsArgument();

        CategoryInstanceDto result = service.defaultCategory(catalog.getId());

        assertThat(result.defaultCategory()).isTrue();
        assertThat(result.parentId()).isNull();
        assertThat(result.slug()).isEqualTo(Category.DEFAULT_SLUG);
        assertThat(catalog.getCategories()).extracting(Category::getIsDefault).containsExactly(true);
    }

    @Test
    void defaultCategoryOfUnknownCatalogIsNotFound() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(catalogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.defaultCategory(id)).isInstanceOf(com.twofourseven.dam.exceptions.ResourceNotFoundException.class);
    }

    @Test
    void ordinaryCategoriesAreNotDefault() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        saveReturnsArgument();

        assertThat(service.create(CategoryInstanceDto.request(catalog.getId(), null, "Dogs", null)).defaultCategory()).isFalse();
    }
}
