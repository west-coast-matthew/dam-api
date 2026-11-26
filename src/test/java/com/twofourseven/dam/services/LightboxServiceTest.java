package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Lightbox;
import com.twofourseven.dam.entities.core.LightboxItem;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.LightboxItemRepository;
import com.twofourseven.dam.repositories.LightboxRepository;
import com.twofourseven.dam.vo.LightboxInstanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightboxServiceTest {

    @Mock
    private LightboxRepository lightboxRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private LightboxItemRepository itemRepository;
    @Mock
    private AssetDownloadService downloadService;
    @InjectMocks
    private LightboxService service;

    private final Catalog catalog = Fixtures.catalog("Photos");
    private final UUID userId = UUID.randomUUID();

    private Lightbox lightbox() {
        Lightbox l = new Lightbox();
        l.setCatalog(catalog);
        l.setUserId(userId);
        l.setTitle("Picks");
        return l;
    }

    @Test
    void createDefaultsToNotShared() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(lightboxRepository.save(any(Lightbox.class))).thenAnswer(inv -> inv.getArgument(0));

        LightboxInstanceDto result = service.create(new LightboxInstanceDto(null, catalog.getId(), userId, " Picks ", null, null, null, null));

        assertThat(result.title()).isEqualTo("Picks");
        assertThat(result.shared()).isFalse();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.itemCount()).isZero();
    }

    @Test
    void createRequiresCatalogAndUser() {
        assertThatThrownBy(() -> service.create(new LightboxInstanceDto(null, null, userId, "T", null, null, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("catalogId");
        assertThatThrownBy(() -> service.create(new LightboxInstanceDto(null, catalog.getId(), null, "T", null, null, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("userId");
    }

    @Test
    void createInUnknownCatalogIsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new LightboxInstanceDto(null, id, userId, "T", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesTitleAndSharing() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(lightboxRepository.save(existing)).thenReturn(existing);

        LightboxInstanceDto result = service.update(existing.getId(), new LightboxInstanceDto(null, null, null, "Final", "d", true, null, null));

        assertThat(result.title()).isEqualTo("Final");
        assertThat(result.shared()).isTrue();
    }

    @Test
    void updateCannotChangeOwner() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new LightboxInstanceDto(null, null, UUID.randomUUID(), "T", null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listFiltersByCatalogAndUser() {
        when(lightboxRepository.findByCatalogIdAndUserIdOrderByTitleAsc(catalog.getId(), userId)).thenReturn(List.of(lightbox()));

        assertThat(service.list(catalog.getId(), userId)).hasSize(1);
    }

    @Test
    void delete() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.delete(existing.getId());

        verify(lightboxRepository).delete(existing);
    }

    // ---- items (spec 009) ----

    private Asset asset(Catalog in, String name) {
        Asset a = new Asset();
        a.setCatalog(in);
        a.setOriginalFilename(name);
        a.setStoragePath("assets/" + name);
        return a;
    }

    private static List<UUID> order(Lightbox l) {
        return l.getItems().stream()
                .sorted(Comparator.comparing(LightboxItem::getSortOrder))
                .map(i -> i.getAsset().getId()).toList();
    }

    @Test
    void createWithAssetsAddsThemInOrder() {
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(assetRepository.findAllById(List.of(b.getId(), a.getId()))).thenReturn(List.of(a, b));
        when(lightboxRepository.save(any(Lightbox.class))).thenAnswer(inv -> inv.getArgument(0));

        LightboxInstanceDto result = service.create(new LightboxInstanceDto(null, catalog.getId(), userId, "Picks", null, null, null,
                List.of(b.getId(), a.getId(), b.getId())));

        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.assetIds()).isNull();
    }

    @Test
    void addItemsAppendsAndSkipsAssetsAlreadyPresent() {
        Lightbox existing = lightbox();
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(lightboxRepository.save(existing)).thenReturn(existing);
        when(assetRepository.findAllById(List.of(a.getId()))).thenReturn(List.of(a));
        when(assetRepository.findAllById(List.of(a.getId(), b.getId()))).thenReturn(List.of(a, b));

        service.addItems(existing.getId(), List.of(a.getId()));
        LightboxInstanceDto result = service.addItems(existing.getId(), List.of(a.getId(), b.getId()));

        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(order(existing)).containsExactly(a.getId(), b.getId());
    }

    @Test
    void addItemsRejectsAssetsFromAnotherCatalog() {
        Lightbox existing = lightbox();
        Asset other = asset(Fixtures.catalog("Other"), "x.jpg");
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(assetRepository.findAllById(List.of(other.getId()))).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.addItems(existing.getId(), List.of(other.getId())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not in catalog");
    }

    @Test
    void addItemsWithUnknownAssetIsNotFound() {
        Lightbox existing = lightbox();
        UUID missing = UUID.randomUUID();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(assetRepository.findAllById(List.of(missing))).thenReturn(List.of());

        assertThatThrownBy(() -> service.addItems(existing.getId(), List.of(missing)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Lightbox withItems(Asset... assets) {
        Lightbox l = lightbox();
        when(lightboxRepository.findById(l.getId())).thenReturn(Optional.of(l));
        List<UUID> ids = java.util.Arrays.stream(assets).map(Asset::getId).toList();
        when(assetRepository.findAllById(ids)).thenReturn(List.of(assets));
        when(lightboxRepository.save(l)).thenReturn(l);
        service.addItems(l.getId(), ids);
        return l;
    }

    @Test
    void removeItemRenumbersAndIsNotFoundWhenAbsent() {
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        Asset c = asset(catalog, "c.jpg");
        Lightbox l = withItems(a, b, c);

        service.removeItem(l.getId(), b.getId());

        assertThat(order(l)).containsExactly(a.getId(), c.getId());
        assertThat(l.getItems()).extracting(LightboxItem::getSortOrder).containsExactlyInAnyOrder(0, 1);
        assertThatThrownBy(() -> service.removeItem(l.getId(), b.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItemsIgnoresIdsNotInTheLightbox() {
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        Lightbox l = withItems(a, b);

        LightboxInstanceDto result = service.removeItems(l.getId(), List.of(a.getId(), UUID.randomUUID()));

        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(order(l)).containsExactly(b.getId());
    }

    @Test
    void reorderSetsTheOrder() {
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        Asset c = asset(catalog, "c.jpg");
        Lightbox l = withItems(a, b, c);

        service.reorder(l.getId(), List.of(c.getId(), a.getId(), b.getId()));

        assertThat(order(l)).containsExactly(c.getId(), a.getId(), b.getId());
    }

    @Test
    void reorderMustListExactlyTheItems() {
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        Lightbox l = withItems(a, b);

        assertThatThrownBy(() -> service.reorder(l.getId(), List.of(a.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reorder(l.getId(), List.of(a.getId(), a.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reorder(l.getId(), List.of(a.getId(), UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void itemsRejectsBadPaging() {
        assertThatThrownBy(() -> service.items(UUID.randomUUID(), -1, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.items(UUID.randomUUID(), 0, 201)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void downloadUsesLightboxOrderAndTitle() {
        Asset a = asset(catalog, "a.jpg");
        Asset b = asset(catalog, "b.jpg");
        Lightbox l = withItems(a, b);
        service.reorder(l.getId(), List.of(b.getId(), a.getId()));
        l.setTitle("Spring Picks!");

        service.download(l.getId());

        verify(downloadService).bundle(List.of(b, a), "spring-picks");
    }

    @Test
    void downloadOfEmptyLightboxIsBadRequest() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.download(existing.getId())).isInstanceOf(BadRequestException.class);
    }
}
