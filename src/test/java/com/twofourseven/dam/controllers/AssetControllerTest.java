package com.twofourseven.dam.controllers;

import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.AssetActivityService;
import com.twofourseven.dam.services.AssetBrowseService;
import com.twofourseven.dam.services.AssetDownloadService;
import com.twofourseven.dam.services.AssetDownloadService.DownloadBundle;
import com.twofourseven.dam.services.AssetService;
import com.twofourseven.dam.vo.AssetUploadActivityVO;
import com.twofourseven.dam.vo.AssetUploadSummaryVO;
import com.twofourseven.dam.vo.AssetInstanceDto;
import com.twofourseven.dam.vo.AssetPageVO;
import com.twofourseven.dam.vo.AssetVersionVO;
import com.twofourseven.dam.vo.BulkUploadResultVO;
import com.twofourseven.dam.vo.SkippedFileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    @Mock
    private AssetService service;
    @Mock
    private AssetActivityService activityService;
    @Mock
    private AssetBrowseService browseService;
    @Mock
    private AssetDownloadService downloadService;
    @InjectMocks
    private AssetController controller;

    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();
    private final UUID catalogId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AssetInstanceDto dto(int version) {
        return new AssetInstanceDto(id, catalogId, "Photos", UUID.randomUUID(), "sunset.jpg",
                com.twofourseven.dam.entities.core.MediaType.IMAGE, "image/jpeg", 5L, AssetStatus.PENDING_PROCESSING,
                true, version, Map.of("k", "v"), List.of(), List.of("Nature"), LocalDateTime.now());
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "sunset.jpg", "image/jpeg", "jpeg!".getBytes());
    }

    @Test
    void uploadIsAcceptedWithLocation() throws Exception {
        when(service.create(any(), any())).thenReturn(dto(1));
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\",\"versioningEnabled\":true,\"tags\":[\"Nature\"]}").getBytes());

        mockMvc.perform(multipart("/assets").file(file()).file(asset))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/assets/" + id))
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.tags[0]").value("Nature"));

        ArgumentCaptor<AssetInstanceDto> dtoCaptor = ArgumentCaptor.forClass(AssetInstanceDto.class);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(service).create(dtoCaptor.capture(), fileCaptor.capture());
        assertThat(dtoCaptor.getValue().catalogId()).isEqualTo(catalogId);
        assertThat(dtoCaptor.getValue().versioningEnabled()).isTrue();
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("sunset.jpg");
    }

    @Test
    void bulkUploadReturns202WithCreatedAndSkipped() throws Exception {
        when(service.createBulk(any(), any())).thenReturn(new BulkUploadResultVO(
                List.of(dto(1)), List.of(new SkippedFileVO("notes.txt", "Unsupported file type '.txt'"))));
        MockMultipartFile zip = new MockMultipartFile("file", "batch.zip", "application/zip", new byte[]{1});
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets/bulk").file(zip).file(asset))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created[0].originalFilename").value("sunset.jpg"))
                .andExpect(jsonPath("$.skipped[0].path").value("notes.txt"));

        ArgumentCaptor<AssetInstanceDto> captor = ArgumentCaptor.forClass(AssetInstanceDto.class);
        verify(service).createBulk(captor.capture(), any());
        assertThat(captor.getValue().catalogId()).isEqualTo(catalogId);
    }

    @Test
    void bulkUploadWithoutZipIs400() throws Exception {
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets/bulk").file(asset)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadWithoutFileIs400() throws Exception {
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets").file(asset)).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedFileIs400() throws Exception {
        when(service.create(any(), any())).thenThrow(new BadRequestException("Audio files are not supported yet ('a.mp3')"));
        MockMultipartFile asset = new MockMultipartFile("asset", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"catalogId\":\"" + catalogId + "\"}").getBytes());

        mockMvc.perform(multipart("/assets").file(file()).file(asset))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Audio files are not supported yet ('a.mp3')"));
    }

    @Test
    void replaceFileReturnsNewVersion() throws Exception {
        when(service.replaceFile(eq(id), any())).thenReturn(dto(2));

        mockMvc.perform(multipart(HttpMethod.PUT, "/assets/{id}/file", id).file(file()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    void updateMetadataWithJson() throws Exception {
        when(service.update(eq(id), any())).thenReturn(dto(1));

        mockMvc.perform(put("/assets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versioningEnabled\":false,\"customMetadata\":{\"k\":\"v\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customMetadata.k").value("v"));

        ArgumentCaptor<AssetInstanceDto> captor = ArgumentCaptor.forClass(AssetInstanceDto.class);
        verify(service).update(eq(id), captor.capture());
        assertThat(captor.getValue().versioningEnabled()).isFalse();
        assertThat(captor.getValue().tags()).isNull();
    }

    @Test
    void listVersions() throws Exception {
        when(service.versions(id)).thenReturn(List.of(new AssetVersionVO(UUID.randomUUID(), id, 1, 10L, LocalDateTime.now())));

        mockMvc.perform(get("/assets/{id}/versions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(1));
    }

    @Test
    void listByCatalog() throws Exception {
        when(service.list(catalogId)).thenReturn(List.of(dto(1)));

        mockMvc.perform(get("/assets").param("catalogId", catalogId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("sunset.jpg"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/assets/{id}", id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }

    @Test
    void activityPassesFiltersAndReturnsTheSummary() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        when(activityService.activity(categoryId, "15m", 5)).thenReturn(new AssetUploadActivityVO(
                900_000L, now.minusMinutes(15), now, 1, 3, categoryId, "Animals",
                List.of(new AssetUploadActivityVO.UploadAttempt(now.minusMinutes(2), 3, "mixed")),
                List.of(new AssetUploadActivityVO.RecentAsset(id, "dog.jpg", "image", now.minusMinutes(2))),
                List.of(new AssetUploadActivityVO.SubCategory(childId, "Dogs", 3))));

        mockMvc.perform(get("/assets/activity").param("categoryId", categoryId.toString())
                        .param("period", "15m").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSampleTimePeriod").value(900000))
                .andExpect(jsonPath("$.totalAssetUploadOperations").value(1))
                .andExpect(jsonPath("$.totalAssetsUploaded").value(3))
                .andExpect(jsonPath("$.categoryName").value("Animals"))
                .andExpect(jsonPath("$.uploadAttempts[0].uploadCount").value(3))
                .andExpect(jsonPath("$.uploadAttempts[0].fileType").value("mixed"))
                .andExpect(jsonPath("$.recentAssets[0].title").value("dog.jpg"))
                .andExpect(jsonPath("$.subCategories[0].title").value("Dogs"))
                .andExpect(jsonPath("$.subCategories[0].newAssetCount").value(3));
    }

    @Test
    void activityWithoutPeriodOrLimitPassesNulls() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(activityService.activity(categoryId, null, null)).thenReturn(new AssetUploadActivityVO(
                86_400_000L, null, null, 0, 0, categoryId, "Animals", List.of(), List.of(), List.of()));

        mockMvc.perform(get("/assets/activity").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSampleTimePeriod").value(86400000));
    }

    @Test
    void activityRequiresAValidCategoryId() throws Exception {
        mockMvc.perform(get("/assets/activity")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/assets/activity").param("categoryId", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void summaryPassesFiltersAndReturnsCategoriesWithTheirEvents() throws Exception {
        UUID catalogId = UUID.randomUUID();
        UUID bbq = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        when(activityService.summary(catalogId, null, "8h")).thenReturn(new AssetUploadSummaryVO(
                28_800_000L, now.minusHours(8), now, catalogId, null, 2, 500,
                List.of(new AssetUploadSummaryVO.CategoryActivity(bbq, "BBQ", List.of("Food", "American", "BBQ"),
                        List.of(UUID.randomUUID(), UUID.randomUUID(), bbq), 2, 500,
                        List.of(new AssetUploadSummaryVO.UploadEvent(operation, now.minusMinutes(5), 498, "mixed"),
                                new AssetUploadSummaryVO.UploadEvent(UUID.randomUUID(), now.minusHours(1), 2, "image"))))));

        mockMvc.perform(get("/assets/activity/summary").param("catalogId", catalogId.toString()).param("period", "8h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssetUploadOperations").value(2))
                .andExpect(jsonPath("$.totalAssetsUploaded").value(500))
                .andExpect(jsonPath("$.categories[0].path[2]").value("BBQ"))
                .andExpect(jsonPath("$.categories[0].assetsUploaded").value(500))
                .andExpect(jsonPath("$.categories[0].uploadEvents[0].operationId").value(operation.toString()))
                .andExpect(jsonPath("$.categories[0].uploadEvents[0].fileType").value("mixed"));
    }

    @Test
    void summaryByCategoryPassesNullCatalog() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(activityService.summary(null, categoryId, null)).thenReturn(new AssetUploadSummaryVO(
                86_400_000L, null, null, UUID.randomUUID(), categoryId, 0, 0, List.of()));

        mockMvc.perform(get("/assets/activity/summary").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.categories").isEmpty());
    }

    @Test
    void searchPassesEveryFilterAndReturnsThePage() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(browseService.search(any())).thenReturn(new AssetPageVO(List.of(dto(1)), 1, 10, 25, 3));

        mockMvc.perform(get("/assets/search").param("catalogId", catalogId.toString())
                        .param("categoryId", categoryId.toString()).param("includeSubcategories", "true")
                        .param("addedFrom", "2026-11-01").param("addedTo", "2026-11-24")
                        .param("tags", "brand,launch").param("tags", "2026").param("tagMatch", "any")
                        .param("q", "sun").param("page", "1").param("size", "10").param("sort", "size").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].originalFilename").value("sunset.jpg"))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<AssetBrowseService.SearchQuery> query = ArgumentCaptor.forClass(AssetBrowseService.SearchQuery.class);
        verify(browseService).search(query.capture());
        AssetBrowseService.SearchQuery q = query.getValue();
        org.assertj.core.api.Assertions.assertThat(q.catalogId()).isEqualTo(catalogId);
        org.assertj.core.api.Assertions.assertThat(q.categoryId()).isEqualTo(categoryId);
        org.assertj.core.api.Assertions.assertThat(q.includeSubcategories()).isTrue();
        org.assertj.core.api.Assertions.assertThat(q.addedFrom()).isEqualTo(java.time.LocalDate.of(2026, 11, 1));
        org.assertj.core.api.Assertions.assertThat(q.addedTo()).isEqualTo(java.time.LocalDate.of(2026, 11, 24));
        org.assertj.core.api.Assertions.assertThat(q.tags()).containsExactly("brand,launch", "2026");
        org.assertj.core.api.Assertions.assertThat(q.matchAllTags()).isFalse();
        org.assertj.core.api.Assertions.assertThat(q.text()).isEqualTo("sun");
        org.assertj.core.api.Assertions.assertThat(q.page()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(q.size()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(q.sort()).isEqualTo("size");
        org.assertj.core.api.Assertions.assertThat(q.direction()).isEqualTo("asc");
    }

    @Test
    void searchNeedsACatalogAndAValidTagMatchAndDates() throws Exception {
        mockMvc.perform(get("/assets/search")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/assets/search").param("catalogId", catalogId.toString()).param("tagMatch", "some"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/assets/search").param("catalogId", catalogId.toString()).param("addedFrom", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileIsStreamedInlineOrAsADownload() throws Exception {
        when(browseService.file(id)).thenAnswer(inv -> new AssetBrowseService.AssetContent(
                new java.io.ByteArrayInputStream("jpeg!".getBytes()), "image/jpeg", "sunset.jpg", 5L));

        mockMvc.perform(get("/assets/{id}/file", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes("jpeg!".getBytes()))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        mockMvc.perform(get("/assets/{id}/file", id).param("download", "true"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void missingThumbnailIs404() throws Exception {
        when(browseService.thumbnail(id)).thenThrow(new com.twofourseven.dam.exceptions.ResourceNotFoundException("no thumbnail"));
        mockMvc.perform(get("/assets/{id}/thumbnail", id)).andExpect(status().isNotFound());
    }

    @Test
    void downloadStreamsAZip() throws Exception {
        when(downloadService.prepare(List.of(id))).thenReturn(new DownloadBundle("assets-20261125-101500.zip", List.of()));

        var started = mockMvc.perform(post("/assets/download").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\":[\"" + id + "\"]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment; filename=\"assets-20261125-101500.zip\"")));
    }

    @Test
    void downloadOfNothingIs400() throws Exception {
        when(downloadService.prepare(List.of())).thenThrow(new BadRequestException("assetIds must not be empty"));

        mockMvc.perform(post("/assets/download").contentType(MediaType.APPLICATION_JSON).content("{\"assetIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
