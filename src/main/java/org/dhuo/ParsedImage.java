package org.dhuo;

import java.awt.image.BufferedImage;

/**
 * Holds the parsed properties plus BufferedImage from a DICOM image.
 */
public class ParsedImage {
  public BufferedImage image;

  // Series number + slice location form a unique slice key (some series have multiple locations).
  public int seriesNumber;
  public double sliceLocation;

  // To avoid relying on image name, we can use triggerTime to sort the frames of a slice series.
  public double triggerTime;

  public double sliceThickness;
  public double pixelSpacingX;
  public double pixelSpacingY;
}
