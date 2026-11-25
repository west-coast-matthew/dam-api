package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetActivityRepository;
import com.twofourseven.dam.repositories.AssetActivityRepository.RecentAsset;
import com.twofourseven.dam.repositories.AssetActivityRepository.SubCategoryCount;
import com.twofourseven.dam.repositories.AssetActivityRepository.UploadOperation;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.AssetUploadActivityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetActivityServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 12, 0);

    @Mock
    private AssetActivityRepository activityRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private AssetActivityService service;
    private final Catalog catalog = Fixtures.catalog("Photos");
    private final Category animals = Fixtures.category(catalog, null, "Animals");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new AssetActivityService(activityRepository, categoryRepository, clock);
    }

    private void categoryExists() {
        when(categoryRepository.findById(animals.getId())).thenReturn(Optional.of(animals));
    }

    @Test
    void summarisesOperationsRecentAssetsAndSubcategoriesForTheLast24HoursByDefault() {
        categoryExists();
        LocalDateTime from = NOW.minusHours(24);
        UUID dogs = UUID.randomUUID();
        UUID newest = UUID.randomUUID();
        when(activityRepository.uploadOperations(animals.getId(), from)).thenReturn(List.of(
                new UploadOperation(UUID.randomUUID(), NOW.minusMinutes(5), 3, 2, "IMAGE"),
                new UploadOperation(UUID.randomUUID(), NOW.minusHours(2), 1, 1, "DOCUMENT"),
                new UploadOperation(UUID.randomUUID(), NOW.minusHours(3), 4, 1, "VIDEO")));
        when(activityRepository.recentAssets(animals.getId(), from, 10)).thenReturn(List.of(
                new RecentAsset(newest, "dog.jpg", "IMAGE", NOW.minusMinutes(5))));
        when(activityRepository.subCategoryCounts(animals.getId(), from)).thenReturn(List.of(
                new SubCategoryCount(dogs, "Dogs", 2), new SubCategoryCount(UUID.randomUUID(), "Cats", 0)));

        AssetUploadActivityVO result = service.activity(animals.getId(), null, null);

        assertThat(result.lastSampleTimePeriod()).isEqualTo(Duration.ofHours(24).toMillis());
        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(NOW);
        assertThat(result.categoryId()).isEqualTo(animals.getId());
        assertThat(result.categoryName()).isEqualTo("Animals");
        assertThat(result.totalAssetUploadOperations()).isEqualTo(3);
        assertThat(result.totalAssetsUploaded()).isEqualTo(8);
        assertThat(result.uploadAttempts()).extracting(AssetUploadActivityVO.UploadAttempt::fileType)
                .containsExactly("mixed", "pdf", "video");
        assertThat(result.uploadAttempts()).extracting(AssetUploadActivityVO.UploadAttempt::uploadCount)
                .containsExactly(3L, 1L, 4L);
        assertThat(result.uploadAttempts().getFirst().dateTime()).isEqualTo(NOW.minusMinutes(5));
        assertThat(result.recentAssets()).containsExactly(
                new AssetUploadActivityVO.RecentAsset(newest, "dog.jpg", "image", NOW.minusMinutes(5)));
        assertThat(result.subCategories()).containsExactly(
                new AssetUploadActivityVO.SubCategory(dogs, "Dogs", 2),
                new AssetUploadActivityVO.SubCategory(result.subCategories().get(1).id(), "Cats", 0));
    }

    @Test
    void aCategoryWithoutChildrenHasNoSubcategoriesAndNoActivityGivesZeroes() {
        categoryExists();
        when(activityRepository.uploadOperations(any(), any())).thenReturn(List.of());
        when(activityRepository.recentAssets(any(), any(), anyInt())).thenReturn(List.of());
        when(activityRepository.subCategoryCounts(any(), any())).thenReturn(List.of());

        AssetUploadActivityVO result = service.activity(animals.getId(), "5m", 3);

        assertThat(result.lastSampleTimePeriod()).isEqualTo(Duration.ofMinutes(5).toMillis());
        assertThat(result.totalAssetUploadOperations()).isZero();
        assertThat(result.totalAssetsUploaded()).isZero();
        assertThat(result.uploadAttempts()).isEmpty();
        assertThat(result.subCategories()).isEmpty();
        verify(activityRepository).recentAssets(animals.getId(), NOW.minusMinutes(5), 3);
    }

    @ParameterizedTest
    @CsvSource({"5m, PT5M", "15m, PT15M", "1h, PT1H", "8h, PT8H", "24h, PT24H", "7d, PT168H", "PT15M, PT15M", "' 1h ', PT1H"})
    void acceptsCommonPeriods(String period, Duration expected) {
        assertThat(AssetActivityService.parsePeriod(period)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "0m", "-1h", "400d"})
    void rejectsBadPeriods(String period) {
        assertThatThrownBy(() -> AssetActivityService.parsePeriod(period)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsBadLimits(int limit) {
        assertThatThrownBy(() -> service.activity(animals.getId(), null, limit)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(activityRepository);
    }

    @Test
    void unknownCategoryIsNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activity(id, null, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(activityRepository);
    }

    @Test
    void mapsMediaTypesToFileTypes() {
        assertThat(AssetActivityService.fileType("IMAGE")).isEqualTo("image");
        assertThat(AssetActivityService.fileType("VIDEO")).isEqualTo("video");
        assertThat(AssetActivityService.fileType("DOCUMENT")).isEqualTo("pdf");
        assertThat(AssetActivityService.fileType("AUDIO")).isEqualTo("audio");
        assertThat(AssetActivityService.fileType(null)).isEqualTo("other");
    }
}
