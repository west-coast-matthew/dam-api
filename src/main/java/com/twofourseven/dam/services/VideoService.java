package com.twofourseven.dam.services;

import java.util.List;

/**
 * VideoService.java
 *
 * <p>Responsible for performing video processing operations. Scope of responsibility is limited to
 * conversation operations, abstracted from where videos originate from, and where they may be
 * persisted to. Abstracts the callers from the specifics of how videos are converted.
 *
 * <p>Common operations include generating a thumbnail or series of thumbnails for a given video,
 * and creating a low quality sub clip of a video for preview purposes.
 */
public interface VideoService {

  // Defins default max width for thumbnail image and video content
  public static final int TARGET_MAX_WIDTH = 1920;
  // Defins default max height for thumbnail image and video content
  public static final int TARGET_MAX_HEIGHT = 1920;
  // Indicates default target video quality
  public static final int TARGET_VIDEO_QUALITY = 75;
  // Indicates default period at which to generate a thumbnail image
  public static final int THUMB_START_MS = 3;
  // Indicates default duration for a thumbnail image
  public static final int THUMB_DURATION_MS = 3;
  // When an video is provided, we optionally choose a frame to use as the point in time to generate
  // a video thumbnail (in milliseconds), however if not provided, we default to this value.
  public static final long DEFAULT_FRAME_SELECTION_OFFSET_MS = 2;

  /**
   * Given a byte array representing an video asset, generate a thumbnail image at the specified
   * time offset. Output dimensions will default to {@link #TARGET_MAX_WIDTH} × {@link
   * #TARGET_MAX_HEIGHT}.
   *
   * @param rawVideo The raw byte array data for the video asset
   * @param offset The point in time, in milliseconds, from which to capture the thumbnail frame
   * @return A byte array representing the generated thumbnail image
   */
  public byte[] generateThumbnail(byte[] rawVideo, long offset);

  /**
   * Given a byte array representing a video asset, generate a thumbnail image at the specified time
   * offset using the provided output dimensions.
   *
   * @param rawVideo The raw byte array data for the video asset
   * @param offset The point in time, in milliseconds, from which to capture the thumbnail frame
   * @param width The desired output width in pixels
   * @param height The desired output height in pixels
   * @return A byte array representing the generated thumbnail image
   */
  public byte[] generateThumbnail(byte[] rawVideo, long offset, int width, int height);

  /**
   * Given raw byte array data for a video asset, return a collection of thumbnail images. A
   * convenience wrapper around {@link #generateThumbnail(byte[], long)}; output dimensions default
   * to {@link #TARGET_MAX_WIDTH} × {@link #TARGET_MAX_HEIGHT}.
   *
   * @param rawVideo The raw byte array data for the video asset
   * @param offset The point in time, in milliseconds, from which to begin capturing frames
   * @return An ordered list of byte arrays, each representing a generated thumbnail image
   */
  public List<byte[]> generateThumbnails(byte[] rawVideo, long offset);

  /**
   * Given raw byte array data for a video asset, return a collection of thumbnail images at the
   * specified output dimensions.
   *
   * @param rawVideo The raw byte array data for the video asset
   * @param offset The point in time, in milliseconds, from which to begin capturing frames
   * @param width The desired output width in pixels
   * @param height The desired output height in pixels
   * @return An ordered list of byte arrays, each representing a generated thumbnail image
   */
  public List<byte[]> generateThumbnails(byte[] rawVideo, long offset, int width, int height);

  /**
   * Given a byte array representing a video asset, create a low-quality sub-clip for preview
   * purposes. Output dimensions default to {@link #TARGET_MAX_WIDTH} × {@link #TARGET_MAX_HEIGHT}.
   *
   * @param rawVideo The raw byte array data for the video asset
   * @param offset The start point of the clip, in milliseconds
   * @param duration The length of the clip, in milliseconds
   * @return A byte array representing the generated preview clip
   */
  public byte[] generatePreviewClip(byte[] rawVideo, long offset, long duration);

  /**
   * Given a byte array representing a video asset, create a low-quality sub-clip for preview
   * purposes at the specified output dimensions.
   *
   * @param rawVideo The raw byte array data for the video asset
   * @param offset The start point of the clip, in milliseconds
   * @param duration The length of the clip, in milliseconds
   * @param width The desired output width in pixels
   * @param height The desired output height in pixels
   * @return A byte array representing the generated preview clip
   */
  public byte[] generatePreviewClip(
      byte[] rawVideo, long offset, long duration, int width, int height);
}
