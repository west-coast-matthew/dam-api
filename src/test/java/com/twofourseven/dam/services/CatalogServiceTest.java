package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.LightboxRepository;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.CatalogInstanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CatalogRepository catalogRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private LightboxRepository lightboxRepository;
    @InjectMocks
    private CatalogService service;

    @Test
    void createAddsCatalogToWorkspace() {
        Workspace workspace = Fixtures.workspace("W");
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));
        when(catalogRepository.save(any(Catalog.class))).thenAnswer(inv -> inv.getArgument(0));

        CatalogInstanceDto result = service.create(new CatalogInstanceDto(null, workspace.getId(), " Photos ", null, null));

        assertThat(result.name()).isEqualTo("Photos");
        assertThat(result.workspaceId()).isEqualTo(workspace.getId());
        assertThat(workspace.getCatalogs()).extracting(Catalog::getName).containsExactly("Photos");
    }

    @Test
    void createWithoutWorkspaceIsBadRequest() {
        assertThatThrownBy(() -> service.create(new CatalogInstanceDto(null, null, "Photos", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("workspaceId");
    }

    @Test
    void createInUnknownWorkspaceIsNotFound() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CatalogInstanceDto(null, id, "Photos", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createDuplicateNameIsConflict() {
        Workspace workspace = Fixtures.workspace("W");
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));
        when(catalogRepository.existsByNameIgnoreCase("Photos")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CatalogInstanceDto(null, workspace.getId(), "Photos", null, null)))
                .isInstanceOf(ConflictException.class);
        verify(catalogRepository, never()).save(any());
    }

    @Test
    void updateCannotMoveWorkspace() {
        Catalog catalog = Fixtures.catalog("Photos");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        assertThatThrownBy(() -> service.update(catalog.getId(),
                new CatalogInstanceDto(null, UUID.randomUUID(), "Photos", null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateRenames() {
        Catalog catalog = Fixtures.catalog("Photos");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(catalogRepository.save(catalog)).thenReturn(catalog);

        CatalogInstanceDto result = service.update(catalog.getId(), new CatalogInstanceDto(null, null, "Images", "d", null));

        assertThat(result.name()).isEqualTo("Images");
        assertThat(result.description()).isEqualTo("d");
    }

    @Test
    void listFiltersByWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        when(catalogRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)).thenReturn(List.of(Fixtures.catalog("A")));

        assertThat(service.list(workspaceId)).extracting(CatalogInstanceDto::name).containsExactly("A");
    }

    @Test
    void deleteWithAssetsIsConflict() {
        Catalog catalog = Fixtures.catalog("Photos");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(assetRepository.existsByCatalogId(catalog.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(catalog.getId())).isInstanceOf(ConflictException.class);
        verify(catalogRepository, never()).delete(any());
    }

    @Test
    void deleteWithLightboxesIsConflict() {
        Catalog catalog = Fixtures.catalog("Photos");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(lightboxRepository.existsByCatalogId(catalog.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(catalog.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteEmptyCatalogRemovesItFromWorkspace() {
        Catalog catalog = Fixtures.catalog("Photos");
        Workspace workspace = catalog.getWorkspace();
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        service.delete(catalog.getId());

        verify(catalogRepository).delete(catalog);
        assertThat(workspace.getCatalogs()).isEmpty();
    }
}
