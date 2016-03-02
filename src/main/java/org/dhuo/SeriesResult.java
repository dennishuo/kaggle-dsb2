package org.dhuo;

import java.io.Serializable;

/**
 * A POD struct which holds the computed results summary for a distinct
 * series/sliceLocation timeseries.
 */
public class SeriesResult implements Serializable {
  public int seriesNumber = 0;
  public double sliceLocation = 0;
  public double sliceThickness = 0;
  public double sysVolShrink = 0;
  public double diaVolShrink = 0;
  public double sysVolGrow = 0;
  public double diaVolGrow  = 0;
}
