package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.services.AssetActivityService;
import com.twofourseven.dam.services.AssetBrowseService;
import com.twofourseven.dam.services.AssetBrowseService.AssetContent;
import com.twofourseven.dam.services.AssetBrowseService.SearchQuery;
import com.twofourseven.dam.services.AssetDownloadService;
import com.twofourseven.dam.services.AssetService;
import com.twofourseven.dam.vo.AssetInstanceDto;
import com.twofourseven.dam.vo.AssetPageVO;
import com.twofourseven.dam.vo.AssetUploadActivityVO;
import com.twofourseven.dam.vo.AssetUploadSummaryVO;
import com.twofourseven.dam.vo.AssetVersionVO;
import com.twofourseven.dam.vo.BulkUploadResultVO;
import com.twofourseven.dam.vo.LightboxItemsVO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Asset management.
 * <ul>
 *   <li>{@code POST /assets} — multipart: {@code file} (media file or a zip holding one) and
 *       {@code asset} (JSON {@link AssetInstanceDto}, at least {@code catalogId}). 202 Accepted with a
 *       Location header: the asset and its file are saved, and its thumbnail is made in the background
 *       (status PENDING_PROCESSING until then).</li>
 *   <li>{@code POST /assets/bulk} — multipart: {@code file} (a zip of media files) and {@code asset}
 *       (JSON applied to every file, at least {@code catalogId}). One asset per supported file. 202
 *       Accepted, with thumbnails made in the background as for a single upload.</li>
 *   <li>{@code GET /assets/activity?categoryId=&period=&limit=} — recent upload activity for a category
 *       and its subcategories: {@code period} like {@code 15m}, {@code 8h} or {@code PT15M} (default 24h),
 *       {@code limit} recent assets (default 10, at most 100).</li>
 *   <li>{@code GET /assets/activity/summary?catalogId=&categoryId=&period=} — the same window broken
 *       down by category: each category with uploads, its path and its own upload events. Give
 *       {@code catalogId} for the whole catalog, or {@code categoryId} to narrow it to that subtree.</li>
 *   <li>{@code GET /assets/search?catalogId=&categoryId=&includeSubcategories=&addedFrom=&addedTo=&tags=&tagMatch=&q=&page=&size=&sort=&direction=}
 *       — spec 022: a page of assets ({@link AssetPageVO}). Dates are ISO days (both inclusive), {@code tags}
 *       repeats or is comma-separated, {@code tagMatch} is all (default) or any, {@code q} matches the file
 *       name, {@code size} is 1–200 (default 24), {@code sort} is added (default), name, size or type and {@code direction} asc or desc
 *       (default: newest and largest first, names and types A–Z).</li>
 *   <li>{@code GET /assets/{id}/file[?download=true]} and {@code GET /assets/{id}/thumbnail} — the original
 *       file and its JPEG thumbnail (404 until the thumbnail exists).</li>
 *   <li>{@code POST /assets/download} — spec 009: JSON {@link LightboxItemsVO}; the assets' original files
 *       as one zip ({@code assets-<yyyyMMdd-HHmmss>.zip}), at most {@code dam.storage.max-download-files}.</li>
 *   <li>{@code PUT /assets/{id}} — JSON metadata update; null fields are unchanged.</li>
 *   <li>{@code PUT /assets/{id}/file} — multipart {@code file}; replaces the file, keeping the old
 *       one as a version when the asset's versioning is enabled.</li>
 * </ul>
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService service;
    private final AssetActivityService activityService;
    private final AssetBrowseService browseService;
    private final AssetDownloadService downloadService;

    public AssetController(AssetService service, AssetActivityService activityService, AssetBrowseService browseService,
                           AssetDownloadService downloadService) {
        this.browseService = browseService;
        this.downloadService = downloadService;
        this.service = service;
        this.activityService = activityService;
    }

    @GetMapping
    public List<AssetInstanceDto> list(@RequestParam(required = false) UUID catalogId) {
        return service.list(catalogId);
    }

    @GetMapping("/activity")
    public AssetUploadActivityVO uploadActivity(@RequestParam UUID categoryId,
                                                @RequestParam(required = false) String period,
                                                @RequestParam(required = false) Integer limit) {
        return activityService.activity(categoryId, period, limit);
    }

    @GetMapping("/activity/summary")
    public AssetUploadSummaryVO uploadSummary(@RequestParam(required = false) UUID catalogId,
                                              @RequestParam(required = false) UUID categoryId,
                                              @RequestParam(required = false) String period) {
        return activityService.summary(catalogId, categoryId, period);
    }

    @GetMapping("/search")
    public AssetPageVO search(@RequestParam UUID catalogId,
                              @RequestParam(required = false) UUID categoryId,
                              @RequestParam(defaultValue = "false") boolean includeSubcategories,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedFrom,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedTo,
                              @RequestParam(required = false) List<String> tags,
                              @RequestParam(defaultValue = "all") String tagMatch,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size,
                              @RequestParam(required = false) String sort,
                              @RequestParam(required = false) String direction) {
        boolean matchAll = switch (tagMatch.strip().toLowerCase(Locale.ROOT)) {
            case "all" -> true;
            case "any" -> false;
            default -> throw new BadRequestException(
                    "Invalid tagMatch '" + tagMatch + "'; use all or any");
        };
        return browseService.search(new SearchQuery(catalogId, categoryId, includeSubcategories, addedFrom, addedTo,
                tags, matchAll, q, page, size, sort, direction));
    }

    /** Several assets' original files as one zip (spec 009). */
    @PostMapping(path = "/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> download(@RequestBody LightboxItemsVO body) {
        return ZipDownloads.response(downloadService, downloadService.prepare(body.assetIds()));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> file(@PathVariable UUID id,
                                                    @RequestParam(defaultValue = "false") boolean download) {
        return stream(browseService.file(id), download, CacheControl.noCache());
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<InputStreamResource> thumbnail(@PathVariable UUID id) {
        // Short-lived: a replaced file gets a new thumbnail under the same URL.
        return stream(browseService.thumbnail(id), false, CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate());
    }

    private static ResponseEntity<InputStreamResource> stream(AssetContent content, boolean download, CacheControl cache) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(content.filename(), StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(cache);
        if (content.size() != null) {
            response.contentLength(content.size());
        }
        return response.body(new InputStreamResource(content.content()));
    }

    @GetMapping("/{id}")
    public AssetInstanceDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<AssetVersionVO> versions(@PathVariable UUID id) {
        return service.versions(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetInstanceDto> create(@RequestPart("asset") AssetInstanceDto asset,
                                                   @RequestPart("file") MultipartFile file) {
        AssetInstanceDto created = service.create(asset, file);
        return ResponseEntity.accepted().location(URI.create("/assets/" + created.id())).body(created);
    }

    @PostMapping(path = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkUploadResultVO> createBulk(@RequestPart("asset") AssetInstanceDto asset,
                                                         @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(service.createBulk(asset, file));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssetInstanceDto update(@PathVariable UUID id, @RequestBody AssetInstanceDto asset) {
        return service.update(id, asset);
    }

    @PutMapping(path = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetInstanceDto replaceFile(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.replaceFile(id, file);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
