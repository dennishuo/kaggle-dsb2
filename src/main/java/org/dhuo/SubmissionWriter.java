package org.dhuo;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.io.*;
import java.util.*;

/**
 * Creates a validly-formatted submission.
 */
public class SubmissionWriter {
  public static void main(String[] args) throws Exception {
    if (args.length != 1 && args.length != 3) {
      System.err.println("Usage: java SubmissionWriter <presubmit predictions> [train presubmit] [train answers]");
      System.exit(1);
    }

    // Output header row.
    PrintStream out = System.out;
    out.print("Id");
    for (int i = 0; i < 600; ++i) {
      out.print(",P" + i);
    }
    out.println();


    if (args.length == 1) {
      // Using trendline(s) from spreadsheet, translate raw predictions.
      double sysIntercept = 51.355;
      double sysSlope = 0.715;
      double diaIntercept = 125.806;
      double diaSlope = 0.415;

      // The presubmit is *not* expected to have a header row.
      Scanner scan = new Scanner(new FileInputStream(args[0]));
      //scan.nextLine();
      while (scan.hasNextLine()) {
        String line = scan.nextLine();
        String[] parts = line.split(",");
        String caseId = parts[0];
        double predSys = Double.parseDouble(parts[1]);
        double predDia = Double.parseDouble(parts[2]);

        double adjSys = sysIntercept + sysSlope * predSys;
        double adjDia = diaIntercept + diaSlope * predDia;

        //double adjSys = predSys + (Math.random() - 0.5) * 60.0;
        //double adjDia = predDia + (Math.random() - 0.5) * 60.0;

        out.print(caseId + "_Diastole");
        NormalDistribution diaNormal = new NormalDistribution(adjDia, 29.1);  // avg err from spreadsheet
        for (int vol = 0; vol < 600; ++vol) {
          out.print("," + diaNormal.cumulativeProbability(vol));
        }
        out.println();
        out.print(caseId + "_Systole");
        NormalDistribution sysNormal = new NormalDistribution(adjSys, 44.1);  // avg err from spreadsheet
        for (int vol = 0; vol < 600; ++vol) {
          out.print("," + sysNormal.cumulativeProbability(vol));
        }
        out.println();
      }
      out.flush();
    } else if (args.length == 3) {
      Map<Integer, Double> predSys = new TreeMap<Integer, Double>();
      Map<Integer, Double> predDia = new TreeMap<Integer, Double>();
      Scanner predScan = new Scanner(new FileInputStream(args[1]));  // presubmit no header.
      while (predScan.hasNextLine()) {
        String line = predScan.nextLine();
        String[] parts = line.split(",");
        int caseId = Integer.parseInt(parts[0]);
        predSys.put(caseId, Double.parseDouble(parts[1]));
        predDia.put(caseId, Double.parseDouble(parts[2]));
      }

      Map<Integer, Double> actualSys = new TreeMap<Integer, Double>();
      Map<Integer, Double> actualDia = new TreeMap<Integer, Double>();
      Scanner actualScan = new Scanner(new FileInputStream(args[2]));
      actualScan.nextLine();  // Answers has a header.
      while (actualScan.hasNextLine()) {
        String line = actualScan.nextLine();
        String[] parts = line.split(",");
        int caseId = Integer.parseInt(parts[0]);
        actualSys.put(caseId, Double.parseDouble(parts[1]));
        actualDia.put(caseId, Double.parseDouble(parts[2]));
      }
      if (predSys.size() != actualSys.size() || predDia.size() != actualDia.size()) {
        System.err.println("Invalid sizes: "
            + " predSys " + predSys.size()
            + " predDia " + predDia.size()
            + " actualSys " + actualSys.size()
            + " actualDia " + actualDia.size());
        System.exit(1);
      }

      // First compute linear regression.
      SimpleRegression regressSys = new SimpleRegression(true);
      SimpleRegression regressDia = new SimpleRegression(true);

      for (Integer caseId : predSys.keySet()) {
        regressSys.addData(predSys.get(caseId), actualSys.get(caseId));
        regressDia.addData(predDia.get(caseId), actualDia.get(caseId));
      }

      RegressionResults regResultsSys = regressSys.regress();
      RegressionResults regResultsDia = regressDia.regress();

      System.err.println("Sys: " + regressSys.getSlope() + "x + " + regressSys.getIntercept()
          + " (r^2 = " + regResultsSys.getRSquared() + ", rmse = "
          + Math.sqrt(regressSys.getMeanSquareError())
          + ")");
      System.err.println("Dia: " + regressDia.getSlope() + "x + " + regressDia.getIntercept()
          + " (r^2 = " + regResultsDia.getRSquared() + ", rmse = "
          + Math.sqrt(regressDia.getMeanSquareError())
          + ")");

      // Store all the integer-rounded errors from regression-based prediction.
      List<Integer> errorsSys = new ArrayList<Integer>();
      List<Integer> errorsDia = new ArrayList<Integer>();
      int minErrSys = Integer.MAX_VALUE;
      int maxErrSys = 0;
      int minErrDia = Integer.MAX_VALUE;
      int maxErrDia = 0;
      for (Integer caseId : predSys.keySet()) {
        int errSys = (int) Math.round(actualSys.get(caseId) - regressSys.predict(predSys.get(caseId)));
        int errDia = (int) Math.round(actualDia.get(caseId) - regressDia.predict(predDia.get(caseId)));
        errorsSys.add(errSys);
        errorsDia.add(errDia);
        if (errSys < minErrSys) minErrSys = errSys;
        if (errSys > maxErrSys) maxErrSys = errSys;
        if (errDia < minErrDia) minErrDia = errDia;
        if (errDia > maxErrDia) maxErrDia = errDia;
      }

      // This will represent the distribution by tallying observed cumulative counts.
      double[] errDistSys = new double[maxErrSys - minErrSys + 1];
      double[] errDistDia = new double[maxErrDia - minErrDia + 1];
      for (Integer errSys : errorsSys) {
        for (int i = errSys - minErrSys; i < errDistSys.length; ++i) {
          errDistSys[i] += 1.0;
        }
      }
      for (int i = 0; i < errDistSys.length; ++i) {
        errDistSys[i] /= errorsSys.size();
      }
      for (Integer errDia : errorsDia) {
        for (int i = errDia - minErrDia; i < errDistDia.length; ++i) {
          errDistDia[i] += 1.0;
        }
      }
      for (int i = 0; i < errDistDia.length; ++i) {
        errDistDia[i] /= errorsDia.size();
      }

      // Finally, emit the output distributions.
      for (Integer caseId : predSys.keySet()) {
        int adjSys = (int) Math.round(regressSys.predict(predSys.get(caseId)));
        int adjDia = (int) Math.round(regressDia.predict(predDia.get(caseId)));

        out.print(caseId + "_Diastole");
        for (int vol = 0; vol < adjDia + minErrDia && vol < 600; ++vol) {
          out.print(",0.0");
        }
        for (int vol = adjDia + minErrDia; vol <= adjDia + maxErrDia && vol < 600; ++vol) {
          out.print("," + errDistDia[vol - (adjDia + minErrDia)]);
        }
        for (int vol = adjDia + maxErrDia + 1; vol < 600; ++vol) {
          out.print(",1.0");
        }
        out.println();
        out.print(caseId + "_Systole");
        for (int vol = 0; vol < adjSys + minErrSys && vol < 600; ++vol) {
          out.print(",0.0");
        }
        for (int vol = adjSys + minErrSys; vol <= adjSys + maxErrSys && vol < 600; ++vol) {
          out.print("," + errDistSys[vol - (adjSys + minErrSys)]);
        }
        for (int vol = adjSys + maxErrSys + 1; vol < 600; ++vol) {
          out.print(",1.0");
        }
        out.println();
      }
      out.flush();
    }

    // Fixed observed-probability distribution.
    /*boolean usingObservedProb = false;
    double[] sysCount = new double[600];
    double[] diaCount = new double[600];
    if (args.length == 1) {
      // Supplied train.csv.
      usingObservedProb = true;

      // train.csv
      Scanner scan = new Scanner(new FileInputStream(args[0]));
      scan.nextLine();  // Skip header row.
      int count = 0;
      while (scan.hasNextLine()) {
        String line = scan.nextLine();
        String[] parts = line.split(",");
        double sys = Double.parseDouble(parts[1]);
        double dia = Double.parseDouble(parts[2]);
        for (int i = (int)sys + 1; i < 600; ++i) {
          sysCount[i]++;
        }
        for (int i = (int)dia + 1; i < 600; ++i) {
          diaCount[i]++;
        }
        ++count;
      }
      for (int i = 0; i < 600; ++i) {
        sysCount[i] /= count;
        diaCount[i] /= count;
      }
    }

    PrintStream out = System.out;

    out.print("Id");
    for (int i = 0; i < 600; ++i) {
      out.print(",P" + i);
    }
    out.println();
    //for (int caseNumber = 1; caseNumber <= 500; ++caseNumber) {
    for (int caseNumber = 501; caseNumber <= 700; ++caseNumber) {
      out.print(caseNumber + "_Diastole");
      for (int vol = 0; vol < 600; ++vol) {
        if (usingObservedProb) {
          out.print("," + diaCount[vol]);
        } else {
          if (vol < 166) {
            out.print(",0.0");
          } else {
            out.print(",1.0");
          }
        }
      }
      out.println();
      out.print(caseNumber + "_Systole");
      for (int vol = 0; vol < 600; ++vol) {
        if (usingObservedProb) {
          out.print("," + sysCount[vol]);
        } else {
          if (vol < 72) {
            out.print(",0.0");
          } else {
            out.print(",1.0");
          }
        }
      }
      out.println();
    }
    out.flush();*/
  }
}
