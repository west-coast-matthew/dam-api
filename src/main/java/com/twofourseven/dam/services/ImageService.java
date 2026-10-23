package com.twofourseven.dam.services;

/**
 * ImageService.java
 *
 * <p>Responsible for performing image processing operations. Scope of responsibility is limited to
 * conversation operations, abstracted from where images originate from, and where they may be
 * persisted to. Abstracts the callers from the specifics of how images are transfored.
 *
 * <p>Scale operations are performed in a manner that maintains the aspect ratio of the image. So a
 * screen responsible for displaying a series of thumbnails will present them in a similar fashion
 * to pintrest.
 */
public interface ImageService {

  public static final int TARGET_MAX_WIDTH = 1920;
  public static final int TARGET_MAX_HEIGHT = 1080;

  /**
   * Accepts raw byte array representing an image, converts it into a thumbnail for brose
   * operations. Scale operations preserve aspect ratio.
   */
  public byte[] convertToThumbnail(byte[] rawImageBytes) throws ImageProcessingException;

  /** Accepts a raw byte array representing an image, and converts it in a similar manner than */
  public byte[] convertToPreview(byte[] rawImageBytes) throws ImageProcessingException;
}
