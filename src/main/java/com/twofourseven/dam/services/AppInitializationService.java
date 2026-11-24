package com.twofourseven.dam.services;

import com.twofourseven.dam.vo.WorkspaceVO;

/**
 * Stages the initial data for a brand-new installation (spec 018): a workspace, its catalog and
 * that catalog's default category. Run at startup by {@code config.AppInitializer} when no
 * workspace exists yet.
 */
public interface AppInitializationService {

    String DEFAULT_WORKSPACE_NAME = "default";
    String DEFAULT_CATALOG_NAME = "default catalog";
    String DEFAULT_CATEGORY_NAME = "default category";

    /** @return true when at least one workspace exists, i.e. the application has been set up */
    boolean isInitialized();

    /**
     * Creates workspace {@value #DEFAULT_WORKSPACE_NAME} with catalog {@value #DEFAULT_CATALOG_NAME},
     * whose default category is named {@value #DEFAULT_CATEGORY_NAME}.
     *
     * @throws com.twofourseven.dam.exceptions.ConflictException if a workspace already exists
     */
    WorkspaceVO initialize();
}
