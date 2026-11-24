package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.WorkspaceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppInitializationServiceImplTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private AppInitializationServiceImpl service;

    @Test
    void isInitializedWhenAnyWorkspaceExists() {
        when(workspaceRepository.count()).thenReturn(1L);

        assertThat(service.isInitialized()).isTrue();
    }

    @Test
    void isNotInitializedWhenNoWorkspaceExists() {
        when(workspaceRepository.count()).thenReturn(0L);

        assertThat(service.isInitialized()).isFalse();
    }

    @Test
    void initializeCreatesWorkspaceCatalogAndDefaultCategory() {
        when(workspaceRepository.count()).thenReturn(0L);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceVO result = service.initialize();

        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(captor.capture());
        Workspace workspace = captor.getValue();
        assertThat(workspace.getName()).isEqualTo("default");
        assertThat(workspace.getCatalogs()).hasSize(1);

        Catalog catalog = workspace.getCatalogs().iterator().next();
        assertThat(catalog.getName()).isEqualTo("default catalog");
        assertThat(catalog.getWorkspace()).isSameAs(workspace);
        assertThat(catalog.getCategories()).hasSize(1);

        Category category = catalog.getCategories().iterator().next();
        assertThat(category.getName()).isEqualTo("default category");
        assertThat(category.getSlug()).isEqualTo("default-category");
        assertThat(category.getIsDefault()).isTrue();
        assertThat(category.getParent()).isNull();
        assertThat(category.getCatalog()).isSameAs(catalog);

        assertThat(result.name()).isEqualTo("default");
        assertThat(result.catalogs()).singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).isEqualTo("default catalog");
                    assertThat(c.id()).isEqualTo(catalog.getId());
                });
    }

    @Test
    void initializeRefusesWhenAWorkspaceAlreadyExists() {
        when(workspaceRepository.count()).thenReturn(2L);

        assertThatThrownBy(() -> service.initialize()).isInstanceOf(ConflictException.class);
        verify(workspaceRepository, never()).save(any());
    }
}
