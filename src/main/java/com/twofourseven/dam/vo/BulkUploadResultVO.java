package com.twofourseven.dam.vo;

import java.util.List;

/**
 * Result of {@code POST /assets/bulk}.
 *
 * @param created one asset per supported media file in the zip
 * @param skipped files in the zip that were not imported, with the reason
 */
public record BulkUploadResultVO(List<AssetInstanceDto> created, List<SkippedFileVO> skipped) {
}
