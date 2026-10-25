package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.CatalogVO;
import com.twofourseven.dam.vo.WorkspaceVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets up a new instance of the application: creates the initial workspace, its first catalog and
 * that catalog's default category.
 */
@Service
public class WorkspaceInitializationService {

    public static final String DEFAULT_WORKSPACE_NAME = "Default Workspace";
    public static final String DEFAULT_CATALOG_NAME = "Default catalog";

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitializationService.class);

    private final WorkspaceRepository workspaceRepository;
    private final CatalogRepository catalogRepository;
    private final Validator validator;

    public WorkspaceInitializationService(WorkspaceRepository workspaceRepository,
                                          CatalogRepository catalogRepository,
                                          Validator validator) {
        this.workspaceRepository = workspaceRepository;
        this.catalogRepository = catalogRepository;
        this.validator = validator;
    }

    /**
     * Default setup: creates "Default Workspace" containing "Default catalog".
     *
     * @throws ConflictException if either name is already in use
     */
    @Transactional
    public WorkspaceVO initializeDefaultWorkspace() {
        log.info("Initializing default workspace");
        return setupWorkspace(DEFAULT_WORKSPACE_NAME, DEFAULT_CATALOG_NAME);
    }

    /**
     * Creates a workspace with the given name and an initial catalog inside it.
     *
     * @throws BadRequestException if a name is blank or too long
     * @throws ConflictException   if either name is already in use
     */
    @Transactional
    public WorkspaceVO setupWorkspace(String workspaceName, String catalogName) {
        return setupWorkspace(workspaceName, null, catalogName, null);
    }

    /**
     * Creates a workspace and an initial catalog, each with an optional description.
     *
     * @throws BadRequestException if a name is blank, or a name or description is too long
     * @throws ConflictException   if either name is already in use
     */
    @Transactional
    public WorkspaceVO setupWorkspace(String workspaceName, String workspaceDescription,
                                      String catalogName, String catalogDescription) {
        Workspace workspace = new Workspace();
        workspace.setName(trim(workspaceName));
        workspace.setDescription(trim(workspaceDescription));

        Catalog catalog = new Catalog();
        catalog.setName(trim(catalogName));
        catalog.setDescription(trim(catalogDescription));
        workspace.addCatalog(catalog);
        Category.createDefaultFor(catalog);

        validate(workspace, catalog);

        if (workspaceRepository.existsByNameIgnoreCase(workspace.getName())) {
            throw new ConflictException("A workspace named '" + workspace.getName() + "' already exists");
        }
        if (catalogRepository.existsByNameIgnoreCase(catalog.getName())) {
            throw new ConflictException("A catalog named '" + catalog.getName() + "' already exists");
        }

        // Catalog and its default category are saved through the cascades
        Workspace saved = workspaceRepository.save(workspace);
        log.info("Created workspace '{}' ({}) with catalog '{}'", saved.getName(), saved.getId(), catalog.getName());
        return toVO(saved);
    }

    private void validate(Workspace workspace, Catalog catalog) {
        Set<ConstraintViolation<Workspace>> workspaceErrors = validator.validate(workspace);
        Set<ConstraintViolation<Catalog>> catalogErrors = validator.validate(catalog);
        List<String> messages = Stream.concat(workspaceErrors.stream(), catalogErrors.stream())
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
        if (!messages.isEmpty()) {
            throw new BadRequestException(String.join("; ", messages));
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static WorkspaceVO toVO(Workspace workspace) {
        List<CatalogVO> catalogs = workspace.getCatalogs().stream()
                .map(c -> new CatalogVO(c.getId(), workspace.getId(), c.getName(), c.getDescription(), c.getCreatedAt()))
                .sorted(Comparator.comparing(CatalogVO::name))
                .toList();
        return new WorkspaceVO(workspace.getId(), workspace.getName(), workspace.getDescription(),
                workspace.getCreatedAt(), catalogs);
    }
}
