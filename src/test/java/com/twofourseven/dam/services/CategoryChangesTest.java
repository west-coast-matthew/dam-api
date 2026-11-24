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
import com.twofourseven.dam.vo.CategoryChangeSetVO.Created;
import com.twofourseven.dam.vo.CategoryChangeSetVO.Updated;
import com.twofourseven.dam.vo.CategoryInstanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link CategoryService#applyChanges}: the category editor's bulk save. */
@ExtendWith(MockitoExtension.class)
class CategoryChangesTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @InjectMocks
    private CategoryService service;

    private final Catalog catalog = Fixtures.catalog("Photos");

    private void catalogHas(Category... categories) {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findByCatalogIdOrderByPositionAscNameAsc(catalog.getId())).thenReturn(new ArrayList<>(List.of(categories)));
    }

    private static CategoryChangeSetVO changes(List<Created> created, List<Updated> updated, List<UUID> deleted) {
        return new CategoryChangeSetVO(created, updated, deleted);
    }

    private static CategoryInstanceDto named(List<CategoryInstanceDto> result, String name) {
        return result.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void createsNestedCategoriesUsingTempIds() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        catalogHas(animals);

        List<CategoryInstanceDto> result = service.applyChanges(catalog.getId(), changes(List.of(
                new Created("t1", animals.getId().toString(), " Pets ", " Furry friends ", 0),
                new Created("t2", "t1", "Cats", null, 0)), null, null));

        CategoryInstanceDto pets = named(result, "Pets");
        assertThat(pets.parentId()).isEqualTo(animals.getId());
        assertThat(pets.description()).isEqualTo("Furry friends");
        assertThat(pets.slug()).isEqualTo("pets");
        assertThat(named(result, "Cats").parentId()).isEqualTo(pets.id());
        assertThat(result).hasSize(3);

        // Parents are inserted before their children.
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Category::getName).containsExactly("Pets", "Cats");
    }

    @Test
    void updatesNameDescriptionPositionAndParent() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category dogs = Fixtures.category(catalog, null, "Dogs");
        catalogHas(animals, dogs);

        List<CategoryInstanceDto> result = service.applyChanges(catalog.getId(), changes(null,
                List.of(new Updated(dogs.getId(), animals.getId().toString(), "Hounds", "Good boys", 2)), null));

        CategoryInstanceDto hounds = named(result, "Hounds");
        assertThat(hounds.parentId()).isEqualTo(animals.getId());
        assertThat(hounds.slug()).isEqualTo("hounds");
        assertThat(hounds.description()).isEqualTo("Good boys");
        assertThat(hounds.position()).isEqualTo(2);
        assertThat(animals.getChildren()).containsExactly(dogs);
    }

    @Test
    void existingCategoryCanMoveUnderANewOne() {
        Category dogs = Fixtures.category(catalog, null, "Dogs");
        catalogHas(dogs);

        List<CategoryInstanceDto> result = service.applyChanges(catalog.getId(), changes(
                List.of(new Created("t1", null, "Animals", null, 0)),
                List.of(new Updated(dogs.getId(), "t1", "Dogs", null, 0)), null));

        assertThat(named(result, "Dogs").parentId()).isEqualTo(named(result, "Animals").id());
    }

    @Test
    void deleteTakesDescendantsAndMovesOrphanedAssetsToDefault() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category dogs = Fixtures.category(catalog, animals, "Dogs");
        Category pets = Fixtures.category(catalog, null, "Pets");
        Asset onlyDogs = new Asset();
        Asset dogsAndPets = new Asset();
        onlyDogs.getCategories().add(dogs);
        dogsAndPets.getCategories().addAll(List.of(dogs, pets));
        dogs.getAssets().addAll(List.of(onlyDogs, dogsAndPets));
        catalogHas(defaultCategory, animals, dogs, pets);
        when(categoryRepository.findFirstByCatalogIdAndIsDefaultTrue(catalog.getId())).thenReturn(Optional.of(defaultCategory));

        List<CategoryInstanceDto> result = service.applyChanges(catalog.getId(), changes(null, null, List.of(animals.getId())));

        assertThat(result).extracting(CategoryInstanceDto::name).containsExactlyInAnyOrder("Default", "Pets");
        assertThat(onlyDogs.getCategories()).containsExactly(defaultCategory);
        assertThat(dogsAndPets.getCategories()).containsExactly(pets);
        assertThat(named(result, "Default").assetCount()).isEqualTo(1);
        // Children before parents.
        var order = inOrder(categoryRepository);
        order.verify(categoryRepository).delete(dogs);
        order.verify(categoryRepository).delete(animals);
    }

    @Test
    void childMovedOutBeforeItsParentIsDeletedSurvives() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category dogs = Fixtures.category(catalog, animals, "Dogs");
        catalogHas(animals, dogs);

        List<CategoryInstanceDto> result = service.applyChanges(catalog.getId(), changes(null,
                List.of(new Updated(dogs.getId(), null, "Dogs", null, 0)), List.of(animals.getId())));

        assertThat(result).extracting(CategoryInstanceDto::name).containsExactly("Dogs");
        verify(categoryRepository, never()).delete(dogs);
    }

    @Test
    void defaultCategoryCannotBeDeleted() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        catalogHas(defaultCategory);

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(null, null, List.of(defaultCategory.getId()))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("default category");
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void defaultCategoryMustStayTopLevel() {
        Category defaultCategory = Category.createDefaultFor(catalog);
        Category animals = Fixtures.category(catalog, null, "Animals");
        catalogHas(defaultCategory, animals);

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(null,
                List.of(new Updated(defaultCategory.getId(), animals.getId().toString(), "Default", null, 0)), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("top-level");
    }

    @Test
    void cyclesAreRejected() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category dogs = Fixtures.category(catalog, animals, "Dogs");
        catalogHas(animals, dogs);

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(null,
                List.of(new Updated(animals.getId(), dogs.getId().toString(), "Animals", null, 0)), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inside itself");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void siblingNamesMustBeUnique() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        catalogHas(animals, Fixtures.category(catalog, animals, "Dogs"));

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(
                List.of(new Created("t1", animals.getId().toString(), "DOGS", null, 1)), null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("'Animals'");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void sameNameUnderDifferentParentsIsFine() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Category plants = Fixtures.category(catalog, null, "Plants");
        catalogHas(animals, plants, Fixtures.category(catalog, animals, "Misc"));

        List<CategoryInstanceDto> result = service.applyChanges(catalog.getId(), changes(
                List.of(new Created("t1", plants.getId().toString(), "Misc", null, 0)), null, null));

        assertThat(result).hasSize(4);
    }

    @Test
    void unknownParentIsRejected() {
        catalogHas();

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(
                List.of(new Created("t1", UUID.randomUUID().toString(), "Dogs", null, 0)), null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent of 'Dogs'");
    }

    @Test
    void blankNameIsRejected() {
        catalogHas();

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(
                List.of(new Created("t1", null, "  ", null, 0)), null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void changingAMissingCategoryIsNotFound() {
        catalogHas();

        assertThatThrownBy(() -> service.applyChanges(catalog.getId(), changes(null,
                List.of(new Updated(UUID.randomUUID(), null, "Dogs", null, 0)), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unknownCatalogIsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyChanges(id, changes(null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
