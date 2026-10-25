package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.WorkspaceInstanceDto;
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
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @InjectMocks
    private WorkspaceService service;

    @Test
    void createTrimsAndSaves() {
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceInstanceDto result = service.create(new WorkspaceInstanceDto(null, "  Marketing ", " Brand ", null));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("Marketing");
        assertThat(result.description()).isEqualTo("Brand");
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void createIgnoresClientSuppliedId() {
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID clientId = UUID.randomUUID();

        WorkspaceInstanceDto result = service.create(new WorkspaceInstanceDto(clientId, "Marketing", null, null));

        assertThat(result.id()).isNotEqualTo(clientId);
    }

    @Test
    void createDuplicateNameIsConflict() {
        when(workspaceRepository.existsByNameIgnoreCase("Marketing")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new WorkspaceInstanceDto(null, "Marketing", null, null)))
                .isInstanceOf(ConflictException.class);
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void updateChangesNameAndDescription() {
        Workspace existing = Fixtures.workspace("Old");
        when(workspaceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(existing)).thenReturn(existing);

        WorkspaceInstanceDto result = service.update(existing.getId(), new WorkspaceInstanceDto(null, "New", "Desc", null));

        assertThat(result.name()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("Desc");
    }

    @Test
    void updateToAnotherWorkspacesNameIsConflict() {
        Workspace existing = Fixtures.workspace("Old");
        when(workspaceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(workspaceRepository.existsByNameIgnoreCaseAndIdNot("Taken", existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(existing.getId(), new WorkspaceInstanceDto(null, "Taken", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getUnknownIsNotFound() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listMapsEveryWorkspace() {
        when(workspaceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(Fixtures.workspace("A"), Fixtures.workspace("B")));

        assertThat(service.list()).extracting(WorkspaceInstanceDto::name).containsExactly("A", "B");
    }

    @Test
    void deleteWithCatalogsIsConflict() {
        Workspace existing = Fixtures.workspace("W");
        when(workspaceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(catalogRepository.existsByWorkspaceId(existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(existing.getId())).isInstanceOf(ConflictException.class);
        verify(workspaceRepository, never()).delete(any());
    }

    @Test
    void deleteEmptyWorkspace() {
        Workspace existing = Fixtures.workspace("W");
        when(workspaceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.delete(existing.getId());

        verify(workspaceRepository).delete(existing);
    }
}
