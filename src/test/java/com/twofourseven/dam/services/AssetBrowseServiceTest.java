package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.services.AssetBrowseService.AssetContent;
import com.twofourseven.dam.services.AssetBrowseService.SearchQuery;
import com.twofourseven.dam.vo.AssetPageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetBrowseServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AssetStorageService storage;
    @InjectMocks
    private AssetBrowseService service;

    private final Catalog catalog = Fixtures.catalog("Photos");
    private final Category food = Fixtures.category(catalog, null, "Food");
    private final Category american = Fixtures.category(catalog, food, "American");
    private final Category bbq = Fixtures.category(catalog, american, "BBQ");
    private final Category art = Fixtures.category(catalog, null, "Art");

    @BeforeEach
    void setUp() {
        // Not every test reaches the catalog check.
        org.mockito.Mockito.lenient().when(catalogRepository.existsById(catalog.getId())).thenReturn(true);
    }

    private Asset asset(String name) {
        Asset a = new Asset();
        a.setCatalog(catalog);
        a.setOriginalFilename(name);
        a.setMediaType(MediaType.IMAGE);
        a.setMimeType("image/png");
        a.setFileSize(10L);
        a.setStatus(AssetStatus.READY);
        a.setStoragePath(AssetStorageService.assetDirectory(catalog.getId(), a.getId()) + "/original.png");
        return a;
    }

    private static SearchQuery query(UUID catalogId, UUID categoryId, boolean sub, Integer page, Integer size, String sort) {
        return new SearchQuery(catalogId, categoryId, sub, null, null, null, true, null, page, size, sort, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsAPageWithTotalsNewestFirstByDefault() {
        Asset one = asset("one.png");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(assetRepository.findAll(any(Specification.class), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(one), inv.getArgument(1), 49));

        AssetPageVO page = service.search(query(catalog.getId(), null, false, null, null, null));

        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AssetBrowseService.DEFAULT_PAGE_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(page.items()).extracting(a -> a.originalFilename()).containsExactly("one.png");
        assertThat(page.totalItems()).isEqualTo(49);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(24);
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesPagingAndSort() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(assetRepository.findAll(any(Specification.class), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        AssetPageVO page = service.search(query(catalog.getId(), null, false, 2, 50, "name"));

        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(2, 50,
                Sort.by(Sort.Order.asc("originalFilename"), Sort.Order.asc("id"))));
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalItems()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCategoryFromThisCatalogIsAccepted() {
        when(categoryRepository.findById(american.getId())).thenReturn(Optional.of(american));
        when(categoryRepository.findByCatalogIdOrderByPositionAscNameAsc(catalog.getId()))
                .thenReturn(List.of(food, american, bbq, art));
        when(assetRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        service.search(query(catalog.getId(), american.getId(), true, null, null, null));

        verify(categoryRepository).findByCatalogIdOrderByPositionAscNameAsc(catalog.getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutSubcategoriesTheTreeIsNotLoaded() {
        when(categoryRepository.findById(bbq.getId())).thenReturn(Optional.of(bbq));
        when(assetRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        service.search(query(catalog.getId(), bbq.getId(), false, null, null, null));

        verify(categoryRepository, never()).findByCatalogIdOrderByPositionAscNameAsc(any());
    }

    @Test
    void rejectsACategoryFromAnotherCatalog() {
        Category elsewhere = Fixtures.category(Fixtures.catalog("Other"), null, "Elsewhere");
        when(categoryRepository.findById(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.search(query(catalog.getId(), elsewhere.getId(), false, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unknownCatalogOrCategoryIsNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.search(query(unknown, null, false, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        when(categoryRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.search(query(catalog.getId(), unknown, false, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsBadPagingSortAndDates() {
        UUID id = catalog.getId();
        assertThatThrownBy(() -> service.search(query(null, null, false, null, null, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(query(id, null, false, -1, null, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(query(id, null, false, null, 0, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(query(id, null, false, null, 201, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(query(id, null, false, null, null, "random"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(new SearchQuery(id, null, false, LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 1), null, true, null, null, null, null, null))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(assetRepository);
    }

    @Test
    void cleansTags() {
        assertThat(AssetBrowseService.cleanTags(Arrays.asList("Brand, launch ", "brand", "", null, "2026")))
                .containsExactly("Brand", "launch", "2026");
        assertThat(AssetBrowseService.cleanTags(null)).isEmpty();
    }

    @Test
    void opensTheOriginalFile() {
        Asset a = asset("photo.png");
        when(assetRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(storage.open(catalog.getWorkspace().getId(), a.getStoragePath())).thenReturn(new ByteArrayInputStream(new byte[3]));

        AssetContent content = service.file(a.getId());

        assertThat(content.mimeType()).isEqualTo("image/png");
        assertThat(content.filename()).isEqualTo("photo.png");
        assertThat(content.size()).isEqualTo(10L);
    }

    @Test
    void opensTheThumbnailAndReportsAMissingOneAsNotFound() {
        Asset a = asset("photo.png");
        when(assetRepository.findById(a.getId())).thenReturn(Optional.of(a));
        String path = AssetStorageService.thumbnailPath(catalog.getId(), a.getId());
        when(storage.open(eq(catalog.getWorkspace().getId()), eq(path))).thenReturn(new ByteArrayInputStream(new byte[3]));

        AssetContent content = service.thumbnail(a.getId());
        assertThat(content.mimeType()).isEqualTo("image/jpeg");
        assertThat(content.filename()).isEqualTo("photo-thumbnail.jpg");

        when(storage.open(eq(catalog.getWorkspace().getId()), eq(path))).thenThrow(new UncheckedIOException(new IOException("gone")));
        assertThatThrownBy(() -> service.thumbnail(a.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unknownAssetIsNotFound() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.file(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "'', '', createdAt, DESC",
            "newest, '', createdAt, DESC",
            "oldest, '', createdAt, ASC",
            "added, asc, createdAt, ASC",
            "name, '', originalFilename, ASC",
            "name, desc, originalFilename, DESC",
            "size, '', fileSize, DESC",
            "SIZE, ASC, fileSize, ASC",
            "type, '', mediaType, ASC"})
    void parsesSortFieldsAndDirections(String sort, String direction, String property, Sort.Direction expected) {
        Sort result = AssetBrowseService.parseSort(sort, direction);
        assertThat(result.getOrderFor(property).getDirection()).isEqualTo(expected);
        assertThat(result.getOrderFor("id")).isNotNull();
    }

    @Test
    void typeSortThenSortsByName() {
        assertThat(AssetBrowseService.parseSort("type", "desc").getOrderFor("originalFilename").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> AssetBrowseService.parseSort("name", "up")).isInstanceOf(BadRequestException.class);
    }
}
