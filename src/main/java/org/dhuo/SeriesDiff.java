package org.dhuo;

/**
 * Represents aggregated diffs and stats across an entire timeseries.
 */
public class SeriesDiff {
  // White pixel changed to black pixel.
  public int[][] shrinkDiffs;

  // Black pixel changed to white pixel.
  public int[][] growDiffs;
}
