package org.dhuo;

import java.io.Serializable;

/**
 * A POD struct which holds the computed results summary for a distinct
 * series/sliceLocation timeseries.
 */
public class SeriesResult implements Serializable {
  public int seriesNumber;
  public double sliceLocation;
  public double sliceThickness;
  public double sysVolShrink;
  public double diaVolShrink;
  // Actually "area".
  public double sysVolGrow;
  public double diaVolGrow;
}
