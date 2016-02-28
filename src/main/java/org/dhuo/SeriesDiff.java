package org.dhuo;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents aggregated diffs and stats across an entire timeseries.
 */
public class SeriesDiff {
  // White pixel changed to black pixel.
  public int[][] shrinkDiffs;
  public List<ConnectedComponent> shrinkScc;


  // Black pixel changed to white pixel.
  public int[][] growDiffs;
  public List<ConnectedComponent> growScc;
}
