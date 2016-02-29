package org.dhuo;

import ij.plugin.DICOM;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Spark job which takes a directory containing case ids by number each containing
 * subdirectories [caseid]/study/sax_[seriesid]/[imagenumber].png, and computes estimated
 * systole/diastole areas per sax series.
 */
public class DistribTool {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: spark-submit kaggle-dsb2-1.0.jar <cases dir>");
      System.exit(1);
    }

    // Master should come from the environment.
    SparkConf conf = new SparkConf()
        .setAppName("org.dhuo.DistribTool");
    JavaSparkContext sc = new JavaSparkContext(conf);

    System.out.println("Looking for case ids...");
    Path basePath = new Path(args[0]);
    FileStatus[] list = basePath.getFileSystem(new Configuration()).listStatus(basePath);
    List<String> casePaths = new ArrayList<String>();
    for (FileStatus stat : list) {
      if (stat.isDirectory()) {
        // TODO(dhuo): Also filter on being a numerical case id.
        casePaths.add(stat.getPath().toString());
      }
    }
    System.out.println("Found " + casePaths.size() + " directories inside " + basePath + ".");
    JavaRDD<String> casePathsRdd = sc.parallelize(casePaths).repartition(casePaths.size());
    JavaRDD<String> answers = casePathsRdd.map(new Function<String, String>() {
      @Override
      public String call(String casePath) throws Exception {
        Path studyDir = new Path(casePath, "study");
        FileSystem fs = studyDir.getFileSystem(new Configuration());
        /*if (!fs.exists(studyDir)) {
          throw new Exception("Failed to find /study dir in " + casePath);
        }*/

        FileStatus[] series = fs.listStatus(studyDir);
        List<Path> saxPaths = new ArrayList<Path>();
        for (FileStatus stat : series) {
          if (stat.getPath().getName().startsWith("sax_")) {
            System.out.println("Found saxPath: " + stat.getPath());
            saxPaths.add(stat.getPath());
          }
        }
        if (saxPaths.size() == 0) {
          throw new Exception("Found 0 saxPaths for " + casePath);
        }

        // TODO(dhuo): Reorganize file series by series ids and slice locations to properly
        // multiplex mixed streams like case 123.
        String caseId = new Path(casePath).getName();

        // TODO(dhuo): As a quick first pass, we'll just add up all available SAX slices
        // which produce any calculated area at all, and use slice thickness.
        double totalVolumeSys = 0.0;
        double totalVolumeDia = 0.0;

        for (Path saxPath : saxPaths) {
          System.out.println("Processing saxPath: " + saxPath);
          FileStatus[] dcmFiles = fs.listStatus(saxPath);
          List<Path> dcmList = new ArrayList<Path>();
          for (FileStatus stat : dcmFiles) {
            if (stat.getPath().getName().endsWith(".dcm")) {
              dcmList.add(stat.getPath());
            }
          }
          Collections.sort(dcmList);

          List<ParsedImage> parsedList = new ArrayList<ParsedImage>();
          for (Path dcmPath : dcmList) {
            DICOM dicom = new DICOM(new BufferedInputStream(fs.open(dcmPath)));
            dicom.run(caseId + "_" + saxPath.getName() + "_" + dcmPath.getName());
            try {
              ParsedImage parsed = ImageProcessor.getProcessedImage(dicom);
              parsedList.add(parsed);
            } catch (Exception e) {
              throw new Exception("While processing path: " + dcmPath, e);
            }
          }
          if (parsedList.size() == 0) {
            throw new Exception("Got 0 images inside saxPath: " + saxPath);
          }

          SeriesDiff combinedDiffs = ImageProcessor.getCombinedDiffs(parsedList);
          ConnectedComponent chosenShrink = ImageProcessor.chooseScc(
              combinedDiffs.shrinkScc, parsedList.get(0));
          ConnectedComponent chosenGrow = ImageProcessor.chooseScc(
              combinedDiffs.growScc, parsedList.get(0));
          ConnectedComponent toUse = chosenShrink;
          if (toUse == null) {
            toUse = chosenGrow;
          }
          if (toUse != null) {
            double sysVol = ImageProcessor.computeAreaSystole(toUse, parsedList.get(0));
            double diaVol = ImageProcessor.computeAreaDiastole(toUse, parsedList.get(0));

            sysVol *= parsedList.get(0).sliceThickness;
            diaVol *= parsedList.get(0).sliceThickness;

            sysVol /= 1000;
            diaVol /= 1000;

            totalVolumeSys += sysVol;
            totalVolumeDia += diaVol;
          }
        }
        return caseId + "," + totalVolumeSys + "," + totalVolumeDia;
      }
    });
    for (String s : answers.collect()) {
      System.out.println(s);
    }
    sc.stop();
  }
}
