package org.dhuo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

}
