package org.dhuo;

import ij.plugin.DICOM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.*;

class ImagePanel extends JPanel {
  private BufferedImage display = null;

  @Override
  public void paintComponent(Graphics g2) {
    if (g2 != null && display != null) {
      g2.drawImage(display, 0, 0, null);
    }
  }

  public void setDisplay(BufferedImage display) {
    this.display = display;
    paintComponent(getGraphics());
  }
}

public class LocalTool {
  static ImagePanel panel = null;

  /**
   * Initializes {@code panel} in a new popup jframe if it hasn't already been done. Otherwise,
   * this is a no-op when already initialized.
   */
  public static void lazyInit(int width, int height) throws Exception {
    if (panel != null) return;

    JFrame frame = new JFrame("DICOM Tool");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    panel = new ImagePanel();
    panel.setPreferredSize(new Dimension(width, height));
    frame.getContentPane().add(panel);
    frame.pack();
    frame.setVisible(true);
  }

  public static void main(String[] args) throws Exception {
    File inputFile = new File(args[0]);
    if (inputFile.isDirectory()) {
      File[] files = inputFile.listFiles();
      Arrays.sort(files);
      List<ParsedImage> parsedList = new ArrayList<ParsedImage>();
      List<BufferedImage> imageList = new ArrayList<BufferedImage>();
      for (File input : files) {
        DICOM dicom = new DICOM(new BufferedInputStream(new FileInputStream(input)));
        dicom.run(input.getName());
        ParsedImage parsed = ImageProcessor.getProcessedImage(dicom);
        parsedList.add(parsed);
        BufferedImage display = parsed.image;
        imageList.add(display);
      }

      SeriesDiff combinedDiffs = ImageProcessor.getCombinedDiffs(parsedList);
      int[][] diffsToDraw = combinedDiffs.growDiffs;
      List<ConnectedComponent> sccToDraw = combinedDiffs.growScc;
      if (args.length > 1 && "shrink".equals(args[1])){
        diffsToDraw = combinedDiffs.shrinkDiffs;
        sccToDraw = combinedDiffs.shrinkScc;
      }

      // Clip to 4 colors
      for (int i = 0; i < imageList.size(); ++i) {
        BufferedImage cur = imageList.get(i);
        int width = cur.getWidth();
        int height = cur.getHeight();
        for (int x = 0; x < width; ++x) {
          for (int y = 0; y < height; ++y) {
            int rgbCur = cur.getRGB(x, y);
            int rc = (rgbCur >> 16) & 0x000000ff;
            int gc = (rgbCur >> 8) & 0x000000ff;
            int bc = rgbCur & 0x000000ff;
            int avg = (int) Math.sqrt((rc * rc + gc * gc + bc * bc) / 3);

            int numColors = 4;
            avg /= (256 / numColors);
            avg *= 256 / numColors;
            if (avg > 255) avg = 255;
            if (diffsToDraw[x][y] != 0) {
              cur.setRGB(x, y, diffsToDraw[x][y]);
            } else {
              cur.setRGB(x, y, 0xff000000 | (avg) | (avg << 8) | (avg << 16));
            }
          }
        }

        for (int j = 0; j < sccToDraw.size(); ++j) {
          ConnectedComponent cc = sccToDraw.get(j);
          int rgb = (((j + 1) * 1234567) & 0x00ffffff) | 0xff000000;
          for (Point p : cc.points) {
            cur.setRGB(p.x, p.y, rgb);
          }
          cur.getGraphics().setColor(Color.WHITE);
          cur.getGraphics().drawLine(
              (cc.xmin + cc.xmax) / 2, cc.ymin, 
              (cc.xmin + cc.xmax) / 2, cc.ymax);
          cur.getGraphics().drawLine(
              cc.xmin, (cc.ymin + cc.ymax) / 2,
              cc.xmax, (cc.ymin + cc.ymax) / 2);
        }
      }

      // Try to diff consecutive images.
      /*List<int[][]> diffs = new ArrayList<int[][]>();
      for (int i = 1; i < imageList.size(); ++i) {
        BufferedImage cur = imageList.get(i);
        BufferedImage prev = imageList.get(i - 1);
        int width = cur.getWidth();
        int height = cur.getHeight();
        int[][] diff = new int[width][height];
        for (int x = 0; x < width; ++x) {
          for (int y = 0; y < height; ++y) {
            int rgbCur = cur.getRGB(x, y);
            int rc = (rgbCur >> 16) & 0x000000ff;
            int gc = (rgbCur >> 8) & 0x000000ff;
            int bc = rgbCur & 0x000000ff;

            int rgbPrev = prev.getRGB(x, y);
            int rp = (rgbPrev >> 16) & 0x000000ff;
            int gp = (rgbPrev >> 8) & 0x000000ff;
            int bp = rgbPrev & 0x000000ff;

            int dr = rc - rp;
            int dg = gc - gp;
            int db = bc - bp;
            dr *= dr;
            dg *= dg;
            db *= db;
            //int avgDiff = (int)(Math.sqrt(dr + dg + db));
            int avgDiff = dr + dg + db;
            if (avgDiff < 0) avgDiff = 0;
            if (avgDiff > 255) avgDiff = 255;
            if ((rgbCur & 0x00ffffff) == 0 &&
                (rgbPrev & 0x00ffffff) != 0 ) {
              diff[x][y] = 0xffff0000;
            } else if ((rgbCur & 0x00ffffff) != 0 &&
                (rgbPrev & 0x00ffffff) == 0 ) {
              diff[x][y] = 0xff00ff00;
            }
          }
        }
        diffs.add(diff);
      }

      for (int i = 1; i < imageList.size(); ++i) {
        int[][] diff = diffs.get(i - 1);
        BufferedImage cur = imageList.get(i);
        int width = cur.getWidth();
        int height = cur.getHeight();
        long avgDiff = 0;
        for (int x = 0; x < width; ++x) {
          for (int y = 0; y < height; ++y) {
            avgDiff += diff[x][y];
          }
        }
        avgDiff /= (width * height);
        //System.out.println("avgDiff: " + avgDiff);
        for (int x = 0; x < width; ++x) {
          for (int y = 0; y < height; ++y) {
            if (diff[x][y] != 0) {
              //cur.setRGB(x, y, 0xff00ff00 | (int)(avgDiff << 8));
              cur.setRGB(x, y, diff[x][y]);
            }
          }
        }
      }*/
      while (true) {
        for (BufferedImage img : imageList) {
          lazyInit(img.getWidth(), img.getHeight());
          panel.setDisplay(img);
          Thread.sleep(100);
        }
      }
    } else {
      DICOM dicom = new DICOM(new BufferedInputStream(new FileInputStream(args[0])));
      dicom.run(inputFile.getName());
      System.out.println("Title: " + dicom.getTitle());
      System.out.println(dicom.getInfoProperty());
      BufferedImage display = ImageProcessor.getProcessedImage(dicom).image;
      int width = display.getWidth();
      int height = display.getHeight();
      System.out.println("width: " + width);
      System.out.println("height: " + height);
      lazyInit(width, height);
      panel.setDisplay(display);
    }
  }
}
