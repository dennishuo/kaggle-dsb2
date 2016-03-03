package org.dhuo;

/**
 * Represents a filtered, averaged result for a single series.
 */
public class AggregatedSeriesResult {
  public double sliceLocation;
  public double sliceThickness;
  // Actually "area".
  public double volumeSys;
  public double volumeDia;
  public int validCount;
}
