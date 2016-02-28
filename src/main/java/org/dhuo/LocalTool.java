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

  public static BufferedImage getProcessedImage(DICOM dicom) throws Exception {
    ParsedImage parsed = ImageProcessor.getProcessedImage(dicom);
    BufferedImage baseImage = parsed.image;
    int width  = baseImage.getWidth();
    int height  = baseImage.getHeight();
    BufferedImage display = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics g = display.getGraphics();
    g.drawImage(baseImage, 0, 0, null);
    g.setColor(Color.GREEN);
    g.drawString(dicom.getTitle(), 10, 20);
    g.drawString("SliceLocation: " + parsed.sliceLocation, 10, 35);
    return display;
  }

  public static void main(String[] args) throws Exception {
    File inputFile = new File(args[0]);
    if (inputFile.isDirectory()) {
      File[] files = inputFile.listFiles();
      Arrays.sort(files);
      List<BufferedImage> imageList = new ArrayList<BufferedImage>();
      for (File input : files) {
        DICOM dicom = new DICOM(new BufferedInputStream(new FileInputStream(input)));
        dicom.run(input.getName());
        BufferedImage display = getProcessedImage(dicom);
        imageList.add(display);
      }

      // Try to diff consecutive images.
      List<int[][]> diffs = new ArrayList<int[][]>();
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
            diff[x][y] = avgDiff;
          }
        }
        diffs.add(diff);
      }

      /*for (int i = 1; i < imageList.size(); ++i) {
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
        System.out.println("avgDiff: " + avgDiff);
        for (int x = 0; x < width; ++x) {
          for (int y = 0; y < height; ++y) {
            if (diff[x][y] > avgDiff) {
              cur.setRGB(x, y, 0xff000000 | (int)(avgDiff << 8));
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
      BufferedImage display = getProcessedImage(dicom);
      int width = display.getWidth();
      int height = display.getHeight();
      lazyInit(width, height);
      panel.setDisplay(display);
    }
  }
}
