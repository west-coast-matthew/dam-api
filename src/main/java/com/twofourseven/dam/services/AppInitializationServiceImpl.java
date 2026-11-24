package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.Workspace;
import com.twofourseven.dam.exceptions.ConflictException;
import com.twofourseven.dam.repositories.WorkspaceRepository;
import com.twofourseven.dam.vo.CatalogVO;
import com.twofourseven.dam.vo.WorkspaceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppInitializationServiceImpl implements AppInitializationService {

    private static final Logger log = LoggerFactory.getLogger(AppInitializationServiceImpl.class);

    private final WorkspaceRepository workspaceRepository;

    public AppInitializationServiceImpl(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return workspaceRepository.count() > 0;
    }

    @Override
    @Transactional
    public WorkspaceVO initialize() {
        // Checked again inside the transaction so a second caller can't stage a duplicate set
        if (workspaceRepository.count() > 0) {
            throw new ConflictException("The application is already initialized");
        }

        Workspace workspace = new Workspace();
        workspace.setName(DEFAULT_WORKSPACE_NAME);

        Catalog catalog = new Catalog();
        catalog.setName(DEFAULT_CATALOG_NAME);
        workspace.addCatalog(catalog);

        // The catalog's default category (isDefault = true) is the spec's "default category".
        // Workspaces and catalogs have no default flag.
        Category category = Category.createDefaultFor(catalog);
        category.setName(DEFAULT_CATEGORY_NAME);
        category.setSlug(Strings.slugify(DEFAULT_CATEGORY_NAME));

        // Catalog and category are saved through the cascades
        Workspace saved = workspaceRepository.save(workspace);
        log.info("Initialized application: workspace '{}' ({}), catalog '{}' ({}), default category '{}' ({})",
                saved.getName(), saved.getId(), catalog.getName(), catalog.getId(),
                category.getName(), category.getId());

        CatalogVO catalogVO = new CatalogVO(catalog.getId(), saved.getId(), catalog.getName(),
                catalog.getDescription(), catalog.getCreatedAt());
        return new WorkspaceVO(saved.getId(), saved.getName(), saved.getDescription(), saved.getCreatedAt(),
                List.of(catalogVO));
    }
}
