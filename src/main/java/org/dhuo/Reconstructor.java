package org.dhuo;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Takes 2D area estimates from slices and assembles them into an estimated volume.
 */
public class Reconstructor {
  public static List<CaseSummary> computeCaseSummaries(
      Map<Integer, List<SeriesResult>> sortedResults) {
    List<CaseSummary> ret = new ArrayList<CaseSummary>();
    for (Integer caseId : sortedResults.keySet()) {
      List<SeriesResult> seriesResultList = sortedResults.get(caseId);
      Collections.sort(seriesResultList, new Comparator<SeriesResult>() {
        @Override
        public int compare(SeriesResult a, SeriesResult b) {
          return Double.compare(a.sliceLocation, b.sliceLocation);
        }
      });

      Map<Integer, List<SeriesResult>> locationBuckets = new TreeMap<Integer, List<SeriesResult>>();
      for (SeriesResult res : seriesResultList) {
        int roundedLocation = (int) Math.round(res.sliceLocation);
        if (!locationBuckets.containsKey(roundedLocation)) {
          locationBuckets.put(roundedLocation, new ArrayList<SeriesResult>());
        }
        locationBuckets.get(roundedLocation).add(res);
      }

      double totalVolumeSys = 0;
      double totalVolumeDia = 0;
      for (SeriesResult res : seriesResultList) {
        if (res.sysVolShrink > 0) {
          totalVolumeSys += res.sysVolShrink * res.sliceThickness / 1000;
        } else if (res.sysVolGrow > 0) {
          totalVolumeSys += res.sysVolGrow * res.sliceThickness / 1000;
        }
        if (res.diaVolShrink > 0) {
          totalVolumeDia += res.diaVolShrink * res.sliceThickness / 1000;
        } else if (res.diaVolGrow > 0) {
          totalVolumeDia += res.diaVolGrow * res.sliceThickness / 1000;
        }
      }
      CaseSummary summary = new CaseSummary();
      summary.caseId = caseId;
      summary.totalVolumeSys = totalVolumeSys;
      summary.totalVolumeDia = totalVolumeDia;
      ret.add(summary);
    }
    return ret;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.out.println("Usage: java Reconstructor <series result file>");
      System.exit(1);
    }

    Map<Integer, List<SeriesResult>> sortedResults = new TreeMap<Integer, List<SeriesResult>>();
    Scanner scan = new Scanner(new FileInputStream(args[0]));
    while (scan.hasNextLine()) {
      String line = scan.nextLine();
      String[] parts = line.split(",");
      SeriesResult res = new SeriesResult();
      int caseId = Integer.parseInt(parts[0]);
      res.seriesNumber = Integer.parseInt(parts[1]);
      res.sliceLocation = Double.parseDouble(parts[2]);
      res.sliceThickness = Double.parseDouble(parts[3]);
      res.sysVolShrink = Double.parseDouble(parts[4]);
      res.diaVolShrink = Double.parseDouble(parts[5]);
      res.sysVolGrow = Double.parseDouble(parts[6]);
      res.diaVolGrow = Double.parseDouble(parts[7]);
      if (!sortedResults.containsKey(caseId)) {
        sortedResults.put(caseId, new ArrayList<SeriesResult>());
      }
      sortedResults.get(caseId).add(res);
    }
    List<CaseSummary> summaries = Reconstructor.computeCaseSummaries(sortedResults);
    for (CaseSummary summary : summaries) {
      System.out.println(summary.caseId + "," + summary.totalVolumeSys + "," + summary.totalVolumeDia);
    }
  }
}
