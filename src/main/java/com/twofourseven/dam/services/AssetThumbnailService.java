package com.twofourseven.dam.services;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.repositories.AssetFileRef;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.services.thumbnails.Thumbnail;
import com.twofourseven.dam.services.thumbnails.ThumbnailGenerationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Makes and stores asset thumbnails in the background (spec 019). Uploads return as soon as the
 * asset is saved; once the transaction commits, each asset is queued and handled by a pool of
 * {@code dam.thumbnails.threads} workers. A worker reads the stored original, writes
 * {@code thumbnail.jpg} next to it and moves the asset from PENDING_PROCESSING to READY, or to
 * FAILED if generation fails. File types without thumbnail support go straight to READY.
 *
 * <p>The queue is in memory: assets still PENDING_PROCESSING when the app stops are not retried.
 */
@Service
public class AssetThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(AssetThumbnailService.class);

    private final AssetRepository assetRepository;
    private final AssetStorageService storage;
    private final ThumbnailGenerationService thumbnails;
    private final Executor executor;
    /** Set when this service created the pool, so it shuts it down. */
    private final ThreadPoolTaskExecutor ownedPool;

    @Autowired
    public AssetThumbnailService(AssetRepository assetRepository, AssetStorageService storage,
                                 ThumbnailGenerationService thumbnails, ThumbnailProperties properties) {
        // Owned rather than a bean: an Executor bean would replace Spring Boot's default task executor
        this(assetRepository, storage, thumbnails, pool(properties.threads()));
    }

    /** For tests: runs work on {@code executor}. */
    AssetThumbnailService(AssetRepository assetRepository, AssetStorageService storage,
                          ThumbnailGenerationService thumbnails, Executor executor) {
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.thumbnails = thumbnails;
        this.executor = executor;
        this.ownedPool = executor instanceof ThreadPoolTaskExecutor pool ? pool : null;
    }

    private static ThreadPoolTaskExecutor pool(int threads) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(threads);
        pool.setMaxPoolSize(threads);
        pool.setThreadNamePrefix("thumbnail-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(30);
        pool.initialize();
        return pool;
    }

    /**
     * Queues thumbnail generation for these assets once the current transaction commits (so the
     * workers can see them); nothing is queued if it rolls back.
     */
    public void generateAfterCommit(Collection<UUID> assetIds) {
        List<UUID> ids = List.copyOf(assetIds);
        if (!ids.isEmpty()) {
            TransactionCallbacks.afterCommit(() -> ids.forEach(this::submit));
        }
    }

    private void submit(UUID assetId) {
        try {
            executor.execute(() -> generate(assetId));
        } catch (RejectedExecutionException e) {
            log.warn("Thumbnail queue rejected asset {}; it stays PENDING_PROCESSING", assetId);
        }
    }

    /** Makes and stores one asset's thumbnail and records the outcome. Runs on a worker; never throws. */
    void generate(UUID assetId) {
        try {
            Optional<AssetFileRef> found = assetRepository.findFileRefById(assetId);
            if (found.isEmpty()) {
                log.debug("Asset {} was deleted before its thumbnail was made", assetId);
                return;
            }
            AssetFileRef asset = found.get();
            AssetStatus outcome = makeThumbnail(asset);
            if (assetRepository.updateStatusIfFileUnchanged(assetId, asset.storagePath(), outcome) == 0) {
                discardIfDeleted(asset);
            }
        } catch (RuntimeException e) {
            log.error("Thumbnail processing failed for asset {}", assetId, e);
        }
    }

    private AssetStatus makeThumbnail(AssetFileRef asset) {
        if (!thumbnails.supports(asset.originalFilename())) {
            log.debug("No thumbnail support for '{}' (asset {})", asset.originalFilename(), asset.assetId());
            return AssetStatus.READY;
        }
        try (InputStream original = storage.open(asset.workspaceId(), asset.storagePath())) {
            Thumbnail thumbnail = thumbnails.generate(original, asset.originalFilename());
            String path = AssetStorageService.thumbnailPath(asset.catalogId(), asset.assetId());
            storage.store(asset.workspaceId(), path, new ByteArrayInputStream(thumbnail.data()));
            log.debug("Stored thumbnail for asset {} ({} bytes)", asset.assetId(), thumbnail.data().length);
            return AssetStatus.READY;
        } catch (Exception e) {
            log.warn("Could not make a thumbnail for asset {} ('{}'): {}", asset.assetId(), asset.originalFilename(),
                    e.getMessage(), e);
            return AssetStatus.FAILED;
        }
    }

    /** The asset was deleted while its thumbnail was being made: remove the folder the thumbnail recreated. */
    private void discardIfDeleted(AssetFileRef asset) {
        if (assetRepository.existsById(asset.assetId())) {
            log.debug("Asset {} got a new file while its thumbnail was made; leaving its status to the newer run",
                    asset.assetId());
            return;
        }
        storage.deleteDirectory(asset.workspaceId(), AssetStorageService.assetDirectory(asset.catalogId(), asset.assetId()));
    }

    @PreDestroy
    void shutdown() {
        if (ownedPool != null) {
            ownedPool.shutdown();
        }
    }
}
