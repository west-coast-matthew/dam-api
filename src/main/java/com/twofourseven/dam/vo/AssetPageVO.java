package com.twofourseven.dam.vo;

import java.util.List;

/**
 * One page of {@code GET /assets/search} (spec 022).
 *
 * @param page       0-based page number
 * @param size       requested page size
 * @param totalItems assets matching the filters, over all pages
 * @param totalPages pages at this size (0 when nothing matches)
 */
public record AssetPageVO(List<AssetInstanceDto> items, int page, int size, long totalItems, int totalPages) {
}
