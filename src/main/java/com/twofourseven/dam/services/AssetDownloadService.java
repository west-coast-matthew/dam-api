package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Group downloads (spec 009): several assets' original files as one zip. {@link #prepare} checks the
 * request and resolves everything the zip needs inside a transaction, so {@link #writeZip} can stream
 * it afterwards without touching the database.
 */
@Service
public class AssetDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AssetDownloadService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** One file in the zip: where it is stored and the (unique) name it gets in the zip. */
    public record DownloadEntry(UUID workspaceId, String storagePath, String name) {
    }

    /** A zip ready to stream. */
    public record DownloadBundle(String filename, List<DownloadEntry> entries) {
    }

    private final AssetRepository assetRepository;
    private final AssetStorageService storage;
    private final StorageProperties properties;

    public AssetDownloadService(AssetRepository assetRepository, AssetStorageService storage, StorageProperties properties) {
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * The assets, in the order given (duplicates dropped), as {@code assets-<yyyyMMdd-HHmmss>.zip}.
     *
     * @throws BadRequestException       for an empty list or more than {@code dam.storage.max-download-files}
     * @throws ResourceNotFoundException if an asset doesn't exist
     */
    @Transactional(readOnly = true)
    public DownloadBundle prepare(List<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new BadRequestException("assetIds must not be empty");
        }
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(assetIds));
        if (ids.contains(null)) {
            throw new BadRequestException("assetIds must not contain null");
        }
        checkSize(ids.size());
        Map<UUID, Asset> found = assetRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        List<Asset> assets = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Asset asset = found.get(id);
            if (asset == null) {
                throw new ResourceNotFoundException("Asset " + id + " not found");
            }
            assets.add(asset);
        }
        return bundle(assets, "assets-" + LocalDateTime.now().format(STAMP));
    }

    /** Builds the bundle for assets already loaded (in a transaction). {@code baseName} has no extension. */
    DownloadBundle bundle(List<Asset> assets, String baseName) {
        if (assets.isEmpty()) {
            throw new BadRequestException("There is nothing to download");
        }
        checkSize(assets.size());
        List<String> names = uniqueNames(assets.stream().map(Asset::getOriginalFilename).toList());
        List<DownloadEntry> entries = new ArrayList<>(assets.size());
        for (int i = 0; i < assets.size(); i++) {
            Asset a = assets.get(i);
            entries.add(new DownloadEntry(a.getCatalog().getWorkspace().getId(), a.getStoragePath(), names.get(i)));
        }
        return new DownloadBundle(baseName + ".zip", entries);
    }

    /** Streams the zip. A file that can't be read is logged and left out rather than failing the rest. */
    public void writeZip(DownloadBundle bundle, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (DownloadEntry entry : bundle.entries()) {
                InputStream in;
                try {
                    in = storage.open(entry.workspaceId(), entry.storagePath());
                } catch (UncheckedIOException e) {
                    log.warn("Leaving {} out of {}: {}", entry.storagePath(), bundle.filename(), e.getMessage());
                    continue;
                }
                try (in) {
                    zip.putNextEntry(new ZipEntry(entry.name()));
                    in.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void checkSize(int count) {
        if (count > properties.maxDownloadFiles()) {
            throw new BadRequestException("At most " + properties.maxDownloadFiles() + " assets can be downloaded at once");
        }
    }

    /**
     * Makes file names unique (ignoring case, since zips are often opened on case-insensitive file
     * systems) by adding " (2)", " (3)"... before the extension. Path separators are replaced.
     */
    static List<String> uniqueNames(List<String> names) {
        Set<String> used = new HashSet<>();
        List<String> out = new ArrayList<>(names.size());
        for (String raw : names) {
            String name = raw == null || raw.isBlank() ? "file" : raw.strip().replaceAll("[/\\\\]", "_");
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            String candidate = name;
            for (int n = 2; !used.add(candidate.toLowerCase(Locale.ROOT)); n++) {
                candidate = stem + " (" + n + ")" + ext;
            }
            out.add(candidate);
        }
        return out;
    }
}
