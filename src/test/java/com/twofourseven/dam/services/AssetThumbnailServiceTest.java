package com.twofourseven.dam.services;

import com.twofourseven.dam.config.ThumbnailProperties;
import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.repositories.AssetFileRef;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.services.thumbnails.Thumbnail;
import com.twofourseven.dam.services.thumbnails.ThumbnailGenerationException;
import com.twofourseven.dam.services.thumbnails.ThumbnailGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** No transaction is active here, so generateAfterCommit queues immediately. */
@ExtendWith(MockitoExtension.class)
class AssetThumbnailServiceTest {

    private static final Executor SAME_THREAD = Runnable::run;

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetStorageService storage;
    @Mock
    private ThumbnailGenerationService thumbnails;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID catalogId = UUID.randomUUID();

    private AssetThumbnailService service(Executor executor) {
        return new AssetThumbnailService(assetRepository, storage, thumbnails, executor);
    }

    private AssetFileRef asset(String filename) {
        UUID id = UUID.randomUUID();
        String path = AssetStorageService.assetDirectory(catalogId, id) + "/original." + filename.substring(filename.lastIndexOf('.') + 1);
        AssetFileRef ref = new AssetFileRef(id, catalogId, workspaceId, path, filename);
        when(assetRepository.findFileRefById(id)).thenReturn(Optional.of(ref));
        return ref;
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes());
    }

    @Test
    void storesTheThumbnailNextToTheOriginalAndMarksTheAssetReady() throws Exception {
        AssetFileRef asset = asset("sunset.jpg");
        when(thumbnails.supports("sunset.jpg")).thenReturn(true);
        when(storage.open(workspaceId, asset.storagePath())).thenReturn(stream("original"));
        when(thumbnails.generate(any(InputStream.class), eq("sunset.jpg"))).thenAnswer(inv -> {
            assertThat(new String(inv.<InputStream>getArgument(0).readAllBytes())).isEqualTo("original");
            return Thumbnail.jpeg("thumb".getBytes());
        });
        when(assetRepository.updateStatusIfFileUnchanged(asset.assetId(), asset.storagePath(), AssetStatus.READY)).thenReturn(1);

        service(SAME_THREAD).generateAfterCommit(List.of(asset.assetId()));

        verify(storage).store(eq(workspaceId), eq(AssetStorageService.thumbnailPath(catalogId, asset.assetId())), any());
        verify(assetRepository).updateStatusIfFileUnchanged(asset.assetId(), asset.storagePath(), AssetStatus.READY);
        verify(storage, never()).deleteDirectory(any(), anyString());
    }

    @Test
    void fileTypesWithoutThumbnailSupportAreReadyWithoutAThumbnail() {
        AssetFileRef asset = asset("banner.webp");
        when(thumbnails.supports("banner.webp")).thenReturn(false);
        when(assetRepository.updateStatusIfFileUnchanged(any(), anyString(), any())).thenReturn(1);

        service(SAME_THREAD).generateAfterCommit(List.of(asset.assetId()));

        verify(assetRepository).updateStatusIfFileUnchanged(asset.assetId(), asset.storagePath(), AssetStatus.READY);
        verifyNoInteractions(storage);
    }

    @Test
    void aFailedGenerationMarksTheAssetFailedAndCarriesOn() {
        AssetFileRef broken = asset("broken.pdf");
        AssetFileRef fine = asset("fine.png");
        when(thumbnails.supports(anyString())).thenReturn(true);
        when(storage.open(eq(workspaceId), anyString())).thenAnswer(inv -> stream("x"));
        when(thumbnails.generate(any(InputStream.class), eq("broken.pdf"))).thenThrow(new ThumbnailGenerationException("gs failed"));
        when(thumbnails.generate(any(InputStream.class), eq("fine.png"))).thenReturn(Thumbnail.jpeg(new byte[]{1}));
        when(assetRepository.updateStatusIfFileUnchanged(any(), anyString(), any())).thenReturn(1);

        service(SAME_THREAD).generateAfterCommit(List.of(broken.assetId(), fine.assetId()));

        verify(assetRepository).updateStatusIfFileUnchanged(broken.assetId(), broken.storagePath(), AssetStatus.FAILED);
        verify(assetRepository).updateStatusIfFileUnchanged(fine.assetId(), fine.storagePath(), AssetStatus.READY);
        verify(storage, never()).store(eq(workspaceId), eq(AssetStorageService.thumbnailPath(catalogId, broken.assetId())), any());
    }

    @Test
    void aMissingOriginalMarksTheAssetFailed() {
        AssetFileRef asset = asset("gone.jpg");
        when(thumbnails.supports("gone.jpg")).thenReturn(true);
        when(storage.open(workspaceId, asset.storagePath())).thenThrow(new java.io.UncheckedIOException(new java.io.IOException("missing")));
        when(assetRepository.updateStatusIfFileUnchanged(any(), anyString(), any())).thenReturn(1);

        service(SAME_THREAD).generateAfterCommit(List.of(asset.assetId()));

        verify(assetRepository).updateStatusIfFileUnchanged(asset.assetId(), asset.storagePath(), AssetStatus.FAILED);
    }

    @Test
    void assetsDeletedBeforeTheWorkerRunsAreSkipped() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findFileRefById(id)).thenReturn(Optional.empty());

        service(SAME_THREAD).generateAfterCommit(List.of(id));

        verifyNoInteractions(storage, thumbnails);
        verify(assetRepository, never()).updateStatusIfFileUnchanged(any(), anyString(), any());
    }

    @Test
    void removesTheRecreatedFolderWhenTheAssetWasDeletedMeanwhile() {
        AssetFileRef asset = asset("a.jpg");
        when(thumbnails.supports("a.jpg")).thenReturn(true);
        when(storage.open(any(), anyString())).thenReturn(stream("x"));
        when(thumbnails.generate(any(InputStream.class), anyString())).thenReturn(Thumbnail.jpeg(new byte[]{1}));
        when(assetRepository.updateStatusIfFileUnchanged(any(), anyString(), any())).thenReturn(0);
        when(assetRepository.existsById(asset.assetId())).thenReturn(false);

        service(SAME_THREAD).generateAfterCommit(List.of(asset.assetId()));

        verify(storage).deleteDirectory(workspaceId, AssetStorageService.assetDirectory(catalogId, asset.assetId()));
    }

    @Test
    void leavesTheAssetAloneWhenItsFileWasReplacedMeanwhile() {
        AssetFileRef asset = asset("a.jpg");
        when(thumbnails.supports("a.jpg")).thenReturn(true);
        when(storage.open(any(), anyString())).thenReturn(stream("x"));
        when(thumbnails.generate(any(InputStream.class), anyString())).thenReturn(Thumbnail.jpeg(new byte[]{1}));
        when(assetRepository.updateStatusIfFileUnchanged(any(), anyString(), any())).thenReturn(0);
        when(assetRepository.existsById(asset.assetId())).thenReturn(true);

        service(SAME_THREAD).generateAfterCommit(List.of(asset.assetId()));

        verify(storage, never()).deleteDirectory(any(), anyString());
    }

    @Test
    void nothingIsQueuedForAnEmptyList() {
        service(SAME_THREAD).generateAfterCommit(List.of());

        verifyNoInteractions(assetRepository, storage, thumbnails);
    }

    @Test
    void aFullQueueDoesNotFailTheUpload() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };

        assertThatCode(() -> service(rejecting).generateAfterCommit(List.of(UUID.randomUUID()))).doesNotThrowAnyException();
        verifyNoInteractions(assetRepository);
    }

    @Test
    void workersRunInParallel() throws Exception {
        ThumbnailProperties properties = new ThumbnailProperties(320, 320, Duration.ofSeconds(1), 150,
                Duration.ofSeconds(60), "magick", "ffmpeg", 2);
        AssetThumbnailService service = new AssetThumbnailService(assetRepository, storage, thumbnails, properties);
        try {
            AssetFileRef first = asset("a.jpg");
            AssetFileRef second = asset("b.jpg");
            when(thumbnails.supports(anyString())).thenReturn(true);
            when(storage.open(any(), anyString())).thenAnswer(inv -> stream("x"));
            // Each generation waits for the other one to start, so this only finishes if both run at once
            CyclicBarrier bothRunning = new CyclicBarrier(2);
            when(thumbnails.generate(any(InputStream.class), anyString())).thenAnswer(inv -> {
                bothRunning.await(5, TimeUnit.SECONDS);
                return Thumbnail.jpeg(new byte[]{1});
            });
            when(assetRepository.updateStatusIfFileUnchanged(any(), anyString(), any())).thenReturn(1);

            service.generateAfterCommit(List.of(first.assetId(), second.assetId()));

            verify(assetRepository, timeout(5000)).updateStatusIfFileUnchanged(first.assetId(), first.storagePath(), AssetStatus.READY);
            verify(assetRepository, timeout(5000)).updateStatusIfFileUnchanged(second.assetId(), second.storagePath(), AssetStatus.READY);
        } finally {
            service.shutdown();
        }
    }
}
