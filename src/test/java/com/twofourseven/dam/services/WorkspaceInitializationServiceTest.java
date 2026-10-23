package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.CatalogVO;
import com.twofourseven.dam.vo.WorkspaceVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Repositories are mocked; a real Bean Validation validator checks the entity rules. */
@ExtendWith(MockitoExtension.class)
class WorkspaceInitializationServiceTest {

    private static ValidatorFactory validatorFactory;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private CatalogRepository catalogRepository;

    private WorkspaceInitializationService service;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        Validator validator = validatorFactory.getValidator();
        service = new WorkspaceInitializationService(workspaceRepository, catalogRepository, validator);
    }

    private void saveReturnsArgument() {
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void initializeDefaultWorkspaceCreatesDefaultWorkspaceAndCatalog() {
        saveReturnsArgument();

        WorkspaceVO result = service.initializeDefaultWorkspace();

        assertThat(result.name()).isEqualTo("Default Workspace");
        assertThat(result.id()).isNotNull();
        assertThat(result.catalogs()).extracting(CatalogVO::name).containsExactly("Default catalog");
        assertThat(result.catalogs().getFirst().workspaceId()).isEqualTo(result.id());
    }

    @Test
    void setupWorkspaceSavesWorkspaceWithLinkedCatalog() {
        saveReturnsArgument();

        service.setupWorkspace("Marketing", "Brand assets", "Campaigns", "2026 campaigns");

        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(captor.capture());
        Workspace saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Marketing");
        assertThat(saved.getDescription()).isEqualTo("Brand assets");
        assertThat(saved.getCatalogs()).hasSize(1);
        Catalog catalog = saved.getCatalogs().iterator().next();
        assertThat(catalog.getName()).isEqualTo("Campaigns");
        assertThat(catalog.getDescription()).isEqualTo("2026 campaigns");
        assertThat(catalog.getWorkspace()).isSameAs(saved);
    }

    @Test
    void setupWorkspaceTrimsNames() {
        saveReturnsArgument();

        WorkspaceVO result = service.setupWorkspace("  Marketing  ", "  Campaigns ");

        assertThat(result.name()).isEqualTo("Marketing");
        assertThat(result.catalogs().getFirst().name()).isEqualTo("Campaigns");
        assertThat(result.description()).isNull();
    }

    @Test
    void blankWorkspaceNameIsRejected() {
        assertThatThrownBy(() -> service.setupWorkspace("  ", "Campaigns"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Workspace name is required");
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void missingCatalogNameIsRejected() {
        assertThatThrownBy(() -> service.setupWorkspace("Marketing", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog name is required");
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void bothNamesMissingReportsBothErrors() {
        assertThatThrownBy(() -> service.setupWorkspace(null, ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Workspace name is required")
                .hasMessageContaining("Catalog name is required");
    }

    @Test
    void tooLongNameIsRejected() {
        String longName = "x".repeat(Workspace.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.setupWorkspace(longName, "Campaigns"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void duplicateWorkspaceNameIsConflict() {
        when(workspaceRepository.existsByNameIgnoreCase("Marketing")).thenReturn(true);

        assertThatThrownBy(() -> service.setupWorkspace("Marketing", "Campaigns"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("workspace named 'Marketing'");
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void duplicateCatalogNameIsConflict() {
        when(workspaceRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(catalogRepository.existsByNameIgnoreCase("Campaigns")).thenReturn(true);

        assertThatThrownBy(() -> service.setupWorkspace("Marketing", "Campaigns"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("catalog named 'Campaigns'");
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void initializingDefaultsTwiceIsConflict() {
        when(workspaceRepository.existsByNameIgnoreCase(WorkspaceInitializationService.DEFAULT_WORKSPACE_NAME))
                .thenReturn(true);

        assertThatThrownBy(() -> service.initializeDefaultWorkspace())
                .isInstanceOf(ConflictException.class);
    }
}
