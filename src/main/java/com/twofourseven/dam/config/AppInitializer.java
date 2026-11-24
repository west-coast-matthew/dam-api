package com.twofourseven.dam.config;

import com.twofourseven.dam.services.AppInitializationService;
import com.twofourseven.dam.vo.WorkspaceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup hook (spec 018): once the application is ready, stages the initial data if no workspace
 * exists. Failures are logged, never thrown, so the app still starts when the database is down
 * (the next startup tries again). Turn off with {@code dam.init.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "dam.init.enabled", havingValue = "true", matchIfMissing = true)
public class AppInitializer {

    private static final Logger log = LoggerFactory.getLogger(AppInitializer.class);

    private final AppInitializationService initializationService;

    public AppInitializer(AppInitializationService initializationService) {
        this.initializationService = initializationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            if (initializationService.isInitialized()) {
                log.debug("Workspaces exist; skipping initial data");
                return;
            }
            WorkspaceVO workspace = initializationService.initialize();
            log.info("Staged initial data in workspace '{}' ({})", workspace.name(), workspace.id());
        } catch (RuntimeException e) {
            log.warn("Could not stage initial data; will try again on next startup: {}", e.getMessage());
            log.debug("Initial data failure", e);
        }
    }
}
