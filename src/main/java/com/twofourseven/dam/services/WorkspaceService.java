package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.WorkspaceInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** CRUD for workspaces. Names are unique (case-insensitive). */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final CatalogRepository catalogRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository, CatalogRepository catalogRepository) {
        this.workspaceRepository = workspaceRepository;
        this.catalogRepository = catalogRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInstanceDto> list() {
        return workspaceRepository.findAllByOrderByNameAsc().stream().map(WorkspaceService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public WorkspaceInstanceDto create(WorkspaceInstanceDto dto) {
        String name = Strings.trim(dto.name());
        if (workspaceRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A workspace named '" + name + "' already exists");
        }
        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setDescription(Strings.trim(dto.description()));
        Workspace saved = workspaceRepository.save(workspace);
        log.info("Created workspace '{}' ({})", saved.getName(), saved.getId());
        return toDto(saved);
    }

    @Transactional
    public WorkspaceInstanceDto update(UUID id, WorkspaceInstanceDto dto) {
        Workspace workspace = find(id);
        String name = Strings.trim(dto.name());
        if (workspaceRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A workspace named '" + name + "' already exists");
        }
        workspace.setName(name);
        workspace.setDescription(Strings.trim(dto.description()));
        return toDto(workspaceRepository.save(workspace));
    }

    /** @throws ConflictException if the workspace still has catalogs */
    @Transactional
    public void delete(UUID id) {
        Workspace workspace = find(id);
        if (catalogRepository.existsByWorkspaceId(id)) {
            throw new ConflictException("Workspace '" + workspace.getName() + "' still has catalogs; delete them first");
        }
        workspaceRepository.delete(workspace);
        log.info("Deleted workspace '{}' ({})", workspace.getName(), id);
    }

    private Workspace find(UUID id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace " + id + " not found"));
    }

    static WorkspaceInstanceDto toDto(Workspace w) {
        return new WorkspaceInstanceDto(w.getId(), w.getName(), w.getDescription(), w.getCreatedAt());
    }
}
