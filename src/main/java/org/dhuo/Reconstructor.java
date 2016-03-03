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

      // TODO(dhuo): Also weed out slices that have identified an LV that is in a vastly different
      // location than other adjacent slices.

      List<AggregatedSeriesResult> aggregated = new ArrayList<AggregatedSeriesResult>();
      for (Integer locationId : locationBuckets.keySet()) {
        int validCount = 0;
        double sumVolSys = 0;
        double sumVolDia = 0;
        for (SeriesResult res : locationBuckets.get(locationId)) {
          if (res.sysVolShrink > 0 || res.diaVolShrink > 0) {
            ++validCount;
            sumVolSys += res.sysVolShrink;
            sumVolDia += res.diaVolShrink;
          }
          if (res.sysVolGrow > 0 || res.diaVolGrow > 0) {
            ++validCount;
            sumVolSys += res.sysVolGrow;
            sumVolDia += res.diaVolGrow;
          }
        }
        if (validCount > 0) {
          sumVolSys /= validCount;
          sumVolDia /= validCount;
          AggregatedSeriesResult agg = new AggregatedSeriesResult();
          agg.sliceLocation = locationBuckets.get(locationId).get(0).sliceLocation;
          agg.sliceThickness = locationBuckets.get(locationId).get(0).sliceThickness;
          agg.volumeSys = sumVolSys;
          agg.volumeDia = sumVolDia;
          agg.validCount = validCount;
          aggregated.add(agg);
          // Slices that didn't yield measurements will be left out as if they didn't exist.
        }
      }

      double totalVolumeSys = 0;
      double totalVolumeDia = 0;
      if (aggregated.size() > 1) {
        // For now, only accept cases that had at least two useful slices, otherwise we'll fallback
        // to some other logic.
        for (int i = 1; i < aggregated.size(); ++i) {
          // Trapezoidal approximation for now?
          double averageSys = aggregated.get(i).volumeSys + aggregated.get(i - 1).volumeSys;
          averageSys /= 2;
          double averageDia = aggregated.get(i).volumeDia + aggregated.get(i - 1).volumeDia;
          averageDia /= 2;
          double distance = aggregated.get(i).sliceLocation - aggregated.get(i - 1).sliceLocation;
          totalVolumeSys += averageSys * distance / 1000;
          totalVolumeDia += averageDia * distance / 1000;
        }
      }

      /*for (SeriesResult res : seriesResultList) {
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
      }*/
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
