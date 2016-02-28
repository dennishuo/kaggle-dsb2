package org.dhuo;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a set of connected points.
 */
public class ConnectedComponent {
  public List<Point> points = new ArrayList<Point>();
  int xmin, xmax, ymin, ymax;

  /**
   * Fills in things like centroid, min/max, etc.
   */
  public void computeStats() {
    xmin = Integer.MAX_VALUE;
    xmax = 0;
    ymin = Integer.MAX_VALUE;
    ymax = 0;
    for (Point p : points) {
      if (p.x < xmin) xmin = p.x;
      if (p.x > xmax) xmax = p.x;
      if (p.y < ymin) ymin = p.y;
      if (p.y > ymax) ymax = p.y;
    }
  }
}
