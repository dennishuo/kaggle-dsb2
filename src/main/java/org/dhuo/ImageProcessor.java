package org.dhuo;

import ij.plugin.DICOM;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.RankFilters;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;


public class ImageProcessor {

  /**
   * Basic checks for "circle-like" properties.
   */
  public static boolean componentIsCircular(ConnectedComponent cc) {
    int width = cc.xmax - cc.xmin + 1;
    int height = cc.ymax - cc.ymin + 1;
    boolean[][] componentPoints = new boolean[width][height];
    for (Point p : cc.points) {
      componentPoints[p.x - cc.xmin][p.y - cc.ymin] = true;
    }
    int xc = (cc.xmin + cc.xmax) / 2 - cc.xmin;
    int yc = (cc.ymin + cc.ymax) / 2 - cc.ymin;

    // For now, reject if the center point is on the component.
    // TODO(dhuo): Maybe need to relax this for oddly shapred LVs.
    if (componentPoints[xc][yc]) return false;

    // Go left.
    boolean leftOkay = false;
    for (int x = xc; x >= 0; --x) {
      if (componentPoints[x][yc]) {
        leftOkay = true;
        break;
      }
    }
    if (!leftOkay) {
      return false;
    }

    boolean rightOkay = false;
    for (int x = xc; x < width; ++x) {
      if (componentPoints[x][yc]) {
        rightOkay = true;
        break;
      }
    }
    if (!rightOkay) {
      return false;
    }

    boolean upOkay = false;
    for (int y = yc; y >= 0; --y) {
      if (componentPoints[xc][y]) {
        upOkay = true;
        break;
      }
    }
    if (!upOkay) {
      return false;
    }

    boolean downOkay = false;
    for (int y = yc; y < height; ++y) {
      if (componentPoints[xc][y]) {
        downOkay = true;
        break;
      }
    }
    if (!downOkay) {
      return false;
    }
    return true;
  }

  /**
   * For non-zero values in {@code diffs}, computes connected components.
   */
  public static List<ConnectedComponent> getScc(int[][] diffs) {
    List<ConnectedComponent> scc = new ArrayList<ConnectedComponent>();

    int width = diffs.length;
    int height = diffs[0].length;
    boolean[][] visited = new boolean[width][height];
    for (int x = 1; x < width - 1; ++x) {
      for (int y = 1; y < height - 1; ++y) {
        if (visited[x][y]) continue;
        if (diffs[x][y] != 0) {
          // Start a BFS.
          ConnectedComponent newComponent = new ConnectedComponent();
          Point first = new Point(x, y);
          LinkedList<Point> queue = new LinkedList<Point>();
          queue.add(first);
          newComponent.points.add(first);
          visited[x][y] = true;
          while (!queue.isEmpty()) {
            Point cur = queue.removeFirst();
            for (int dx = cur.x - 1; dx <= cur.x + 1; ++dx) {
              for (int dy = cur.y - 1; dy <= cur.y + 1; ++dy) {
                if (visited[dx][dy]) continue;
                if (diffs[dx][dy] != 0) {
                  Point neigh = new Point(dx, dy);
                  queue.addLast(neigh);
                  newComponent.points.add(neigh);
                  visited[dx][dy] = true;
                }
              }
            }
          }
          newComponent.computeStats();
          if (componentIsCircular(newComponent)) {
            scc.add(newComponent);
          }
        }
      }
    }
    System.out.println("Found " + scc.size() + " connected components.");
    return scc;
  }

  /**
   * Given {@code images} in chronological order, does color-clipping and then adjacent-image
   * diffing, differentiating between 0 to 1 and 1 to 0 types. Computes union of all the
   * adjacency diffs.
   */
  public static SeriesDiff getCombinedDiffs(List<ParsedImage> images) {
    int width = images.get(0).image.getWidth();
    int height = images.get(0).image.getHeight();

    SeriesDiff ret = new SeriesDiff();
    ret.shrinkDiffs = new int[width][height];
    ret.growDiffs = new int[width][height];

    int threshold = 256 / 4;
    for (int i = 1; i < images.size(); ++i) {
      BufferedImage cur = images.get(i).image;
      BufferedImage prev = images.get(i - 1).image;
      for (int x = 1; x < width - 1; ++x) {
        for (int y = 1; y < height - 1; ++y) {
          int curClipped = getClipped(cur.getRGB(x, y), threshold);
          int prevClipped = getClipped(prev.getRGB(x, y), threshold);
          if (curClipped == 0 && prevClipped != 0) {
            ret.shrinkDiffs[x][y] |= 0xffff0000;
          } else if (curClipped != 0 && prevClipped == 0) {
            ret.growDiffs[x][y] |= 0xff00ff00;
          }
        }
      }
    }

    boolean removed = true;
    while (removed) {
      removed = filterOutliers(ret.shrinkDiffs, 5);
    }
    removed = true;
    while (removed) {
      removed = filterOutliers(ret.growDiffs, 5);
    }

    ret.shrinkScc = getScc(ret.shrinkDiffs);
    ret.growScc = getScc(ret.growDiffs);
    return ret;
  }

  /**
   * Clips to 0 or 255 based on threshold.
   */
  public static int getClipped(int rgbCur, int threshold) {
    int rc = (rgbCur >> 16) & 0x000000ff;
    int gc = (rgbCur >> 8) & 0x000000ff;
    int bc = rgbCur & 0x000000ff;
    int avg = (int) Math.sqrt((rc * rc + gc * gc + bc * bc) / 3);
    if (avg < threshold) return 0;
    return 255;
  }

  /**
   * {@code diffs} is expeced to be binary; 0 or some other RGB value, so that we can efficiently
   * compute "percentile" with a simple count of zeros. Also, doesn't ever go from off to on, only
   * goes from on to off. The numRequiredNeighbors includes the central pixel being filtered.
   *
   * @return true if anything was removed at all.
   */
  public static boolean filterOutliers(int[][] diffs, int numRequiredNeighbors) {
    int width = diffs.length;
    int height = diffs[0].length;
    boolean[][] toRemove = new boolean[width][height];
    boolean removedAnything = false;
    for (int x = 1; x < width - 1; ++x) {
      for (int y = 1; y < height - 1; ++y) {
        // Skip non-active pixels.
        if (diffs[x][y] == 0) continue;
        int count = 0;
        for (int dx = x - 1; dx <= x + 1; ++dx) {
          for (int dy = y - 1; dy <= y + 1; ++dy) {
            if (diffs[dx][dy] != 0) ++count;
          }
        }
        if (count < numRequiredNeighbors) {
          toRemove[x][y] = true;
          removedAnything = true;
        }
      }
    }
    for (int x = 1; x < width - 1; ++x) {
      for (int y = 1; y < height - 1; ++y) {
        if (toRemove[x][y]) {
          diffs[x][y] = 0;
        }
      }
    }
    return removedAnything;
  }

  public static ParsedImage getProcessedImage(DICOM dicom) throws Exception {
   /* new GaussianBlur().blur(dicom.getProcessor(), 10.0);
    BackgroundSubtracter subtractor = new BackgroundSubtracter();
    subtractor.rollingBallBackground(
        dicom.getProcessor(),
        20, false, false, false, false, true);*/
    RankFilters filter = new RankFilters();
    filter.rank(dicom.getProcessor(), 5, RankFilters.MEDIAN);
    
    BufferedImage baseImage = dicom.getProcessor().getBufferedImage();
    int width = baseImage.getWidth();
    int height = baseImage.getHeight();

    String metadataString = dicom.getInfoProperty();
    Scanner scan = new Scanner(metadataString);
    double sliceLocation = 0;
    while (scan.hasNextLine()) {
      String line = scan.nextLine();
      if (line.indexOf("Slice Location") != -1) {
        sliceLocation = Double.parseDouble(line.split(":")[1].trim());
      }
    }

    BufferedImage display = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    display.getGraphics().drawImage(baseImage, 0, 0, null);

    ParsedImage toReturn = new ParsedImage();
    toReturn.sliceLocation = sliceLocation;
    toReturn.image = display;
    return toReturn;
  }
}
