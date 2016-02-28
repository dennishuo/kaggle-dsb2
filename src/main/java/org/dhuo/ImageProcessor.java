package org.dhuo;

import ij.plugin.DICOM;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.BackgroundSubtracter;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;


public class ImageProcessor {
  public static ParsedImage getProcessedImage(DICOM dicom) throws Exception {
   /* new GaussianBlur().blur(dicom.getProcessor(), 10.0);
    BackgroundSubtracter subtractor = new BackgroundSubtracter();
    subtractor.rollingBallBackground(
        dicom.getProcessor(),
        20, false, false, false, false, true);*/
    
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

    ParsedImage toReturn = new ParsedImage();
    toReturn.sliceLocation = sliceLocation;
    toReturn.image = baseImage;
    return toReturn;
  }
}
