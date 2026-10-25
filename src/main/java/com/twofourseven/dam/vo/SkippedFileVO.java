package com.twofourseven.dam.vo;

/**
 * A file in a bulk upload that was not turned into an asset.
 *
 * @param path   the file's path inside the zip
 * @param reason why it was skipped (e.g. unsupported type)
 */
public record SkippedFileVO(String path, String reason) {
}
