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
import org.apache.spark.api.java.function.PairFlatMapFunction;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Spark job which takes a directory containing case ids by number each containing
 * subdirectories [caseid]/study/sax_[seriesid]/[imagenumber].png, and computes estimated
 * systole/diastole areas per sax series.
 */
public class DistribTool {
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: spark-submit kaggle-dsb2-1.0.jar <cases dir> [optional: seriesresult output]");
      System.exit(1);
    }

    // Master should come from the environment.
    SparkConf conf = new SparkConf()
        .setAppName("org.dhuo.DistribTool");
    JavaSparkContext sc = new JavaSparkContext(conf);

    // Get initial list of caseIds directories.
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

    // Repartition to spread out the necessary list calls, and then for each caseId list all sax
    // directories.
    JavaRDD<String> casePathsRdd = sc.parallelize(casePaths).repartition(casePaths.size());
    JavaPairRDD<Integer, String> saxPathsRdd = casePathsRdd.flatMapToPair(
        new PairFlatMapFunction<String, Integer, String>() {
      @Override
      public Iterable<Tuple2<Integer, String>> call(String casePath) throws Exception {
        Path casePathObj = new Path(casePath);
        Path studyDir = new Path(casePathObj, "study");
        FileSystem fs = studyDir.getFileSystem(new Configuration());
        Integer caseId = Integer.parseInt(casePathObj.getName());
        /*if (!fs.exists(studyDir)) {
          throw new Exception("Failed to find /study dir in " + casePath);
        }*/

        FileStatus[] series = fs.listStatus(studyDir);
        List<Tuple2<Integer, String>> saxPaths = new ArrayList<Tuple2<Integer, String>>();
        for (FileStatus stat : series) {
          if (stat.getPath().getName().startsWith("sax_")) {
            System.out.println("Found saxPath: " + stat.getPath());
            saxPaths.add(new Tuple2<Integer, String>(caseId, stat.getPath().toString()));
          }
        }
        if (saxPaths.size() == 0) {
          throw new Exception("Found 0 saxPaths for " + casePath);
        }
        return saxPaths;
      }
    });
    saxPathsRdd.cache();
    long saxPathsRddCount = saxPathsRdd.count();
    System.out.println("Total of " + saxPathsRddCount + " saxPaths");

    // For each saxPath, list all dcmPaths.
    JavaPairRDD<Integer, String> dcmPathsRdd = saxPathsRdd.flatMapValues(
        new Function<String, Iterable<String>>() {
      @Override
      public Iterable<String> call(String saxPath) throws Exception {
        Path saxPathObj = new Path(saxPath);
        FileSystem fs = saxPathObj.getFileSystem(new Configuration());
        FileStatus[] dcmFiles = fs.listStatus(saxPathObj);
        List<String> dcmList = new ArrayList<String>();
        for (FileStatus stat : dcmFiles) {
          if (stat.getPath().getName().endsWith(".dcm")) {
            dcmList.add(stat.getPath().toString());
          }
        }
        return dcmList;
      }
    });
    dcmPathsRdd.cache();
    System.out.println("Total of " + dcmPathsRdd.count() + " dcmPaths.");

    // Repartition to number of saxPaths instead of num caseIds for better balance.
    dcmPathsRdd = dcmPathsRdd.repartition((int)saxPathsRddCount);

    // Decode all the dcm files and stash them in byte arrays; translate into new composite
    // keys of the form caseId/seriesNumber_sliceLocation.
    JavaPairRDD<String, byte[]> parsedImagesRdd = dcmPathsRdd.mapToPair(
        new PairFunction<Tuple2<Integer, String>, String, byte[]>() {
      @Override
      public Tuple2<String, byte[]> call(Tuple2<Integer, String> dcmEntry) throws Exception {
        int caseId = dcmEntry._1();
        Path dcmPath = new Path(dcmEntry._2());
        FileSystem fs = dcmPath.getFileSystem(new Configuration());
        DICOM dicom = new DICOM(new BufferedInputStream(fs.open(dcmPath)));
        dicom.run(Path.getPathWithoutSchemeAndAuthority(dcmPath).toString());
        ParsedImage parsed = null;
        try {
          parsed = ImageProcessor.getProcessedImage(dicom);
        } catch (Exception e) {
          throw new Exception("While processing path: " + dcmPath, e);
        }
        String uniqueId = String.format(
            "%d/%d_%d", caseId, parsed.seriesNumber, Math.round(parsed.sliceLocation));
        return new Tuple2<String, byte[]>(uniqueId, parsed.toBytes());
      }
    });
    JavaPairRDD<String, Iterable<byte[]>> demuxedSaxRdd = parsedImagesRdd.groupByKey();
    demuxedSaxRdd.cache();
    long demuxedSaxRddCount = demuxedSaxRdd.count();
    System.out.println("Total demuxed sax count: " + demuxedSaxRddCount);

    // Repartition to number of actual distinct sax series, the finest grain of work where
    // each task can still do processing across the timeseries.
    demuxedSaxRdd = demuxedSaxRdd.repartition((int)demuxedSaxRddCount);

    // Map back down to caseId keys and values which are computed results
    JavaPairRDD<Integer, SeriesResult> seriesResultsRdd = demuxedSaxRdd.mapToPair(
        new PairFunction<Tuple2<String, Iterable<byte[]>>, Integer, SeriesResult>() {
      @Override
      public Tuple2<Integer, SeriesResult> call(Tuple2<String, Iterable<byte[]>> seriesEntry)
          throws Exception {
        int caseId = Integer.parseInt(seriesEntry._1().split("/")[0]);

        List<ParsedImage> parsedList = new ArrayList<ParsedImage>();
        for (byte[] imageBuf : seriesEntry._2()) {
          parsedList.add(ParsedImage.fromBytes(imageBuf));
        }
        Collections.sort(parsedList, new Comparator<ParsedImage>() {
          @Override
          public int compare(ParsedImage a, ParsedImage b) {
            return Double.compare(a.triggerTime, b.triggerTime);
          }
        });
        SeriesDiff combinedDiffs = ImageProcessor.getCombinedDiffs(parsedList);
        ConnectedComponent chosenShrink = ImageProcessor.chooseScc(
            combinedDiffs.shrinkScc, parsedList.get(0));
        ConnectedComponent chosenGrow = ImageProcessor.chooseScc(
            combinedDiffs.growScc, parsedList.get(0));
        double sysVolShrink = 0;
        double diaVolShrink = 0;
        if (chosenShrink != null) {
          sysVolShrink = ImageProcessor.computeAreaSystole(chosenShrink, parsedList.get(0));
          diaVolShrink = ImageProcessor.computeAreaDiastole(chosenShrink, parsedList.get(0));
        }
        double sysVolGrow = 0;
        double diaVolGrow = 0;
        if (chosenGrow != null) {
          sysVolGrow = ImageProcessor.computeAreaSystole(chosenGrow, parsedList.get(0));
          diaVolGrow = ImageProcessor.computeAreaDiastole(chosenGrow, parsedList.get(0));
        }

        SeriesResult result = new SeriesResult();
        result.seriesNumber = parsedList.get(0).seriesNumber;
        result.sliceLocation = parsedList.get(0).sliceLocation;
        result.sliceThickness = parsedList.get(0).sliceThickness;
        result.sysVolShrink = sysVolShrink;
        result.diaVolShrink = diaVolShrink;
        result.sysVolGrow = sysVolGrow;
        result.diaVolGrow = diaVolGrow;
        SeriesResult res = result;
        return new Tuple2<Integer, SeriesResult>(caseId, result);
      }
    });
    JavaPairRDD<Integer, Iterable<SeriesResult>> resultsByCaseRdd = seriesResultsRdd.groupByKey();
    List<Tuple2<Integer, Iterable<SeriesResult>>> collectedResults = resultsByCaseRdd.collect();

    // Sort each list of slice-series by slice location, and place in a map by caseId that's easier
    // to work with.
    Map<Integer, List<SeriesResult>> sortedResults = new TreeMap<Integer, List<SeriesResult>>();
    for (Tuple2<Integer, Iterable<SeriesResult>> tup : collectedResults) {
      List<SeriesResult> resultList = new ArrayList<SeriesResult>();
      for (SeriesResult res : tup._2()) {
        resultList.add(res);
      }
      // Sort the list by sliceLocation.
      Collections.sort(resultList, new Comparator<SeriesResult>() {
        @Override
        public int compare(SeriesResult a, SeriesResult b) {
          return Double.compare(a.sliceLocation, b.sliceLocation);
        }
      });
      sortedResults.put(tup._1(), resultList);
    }

    // Compute volumes.
    List<CaseSummary> summaries = Reconstructor.computeCaseSummaries(sortedResults);
    for (CaseSummary summary : summaries) {
      System.out.println(summary.caseId + "," + summary.totalVolumeSys + "," + summary.totalVolumeDia);
    }

    // Optionally save out the series results so that we can refine the volume
    // calculations offline without recomputing image features or area calculations.
    if (args.length > 1) {
      Path seriesResultsOutputPath = new Path(args[1]);
      PrintStream fout = new PrintStream(seriesResultsOutputPath.getFileSystem(new Configuration())
          .create(seriesResultsOutputPath));
      System.out.println("Saving seriesResults out to " + args[1]);
      for (Integer caseId : sortedResults.keySet()) {
        List<SeriesResult> seriesResultList = sortedResults.get(caseId);
        for (SeriesResult res : seriesResultList) {
          fout.println(caseId + "," + res.seriesNumber + "," + res.sliceLocation
              + "," + res.sliceThickness + "," + res.sysVolShrink + "," + res.diaVolShrink
              + "," + res.sysVolGrow + "," + res.diaVolGrow);
        }
      }
      fout.close();
    }
    sc.stop();
  }
}
