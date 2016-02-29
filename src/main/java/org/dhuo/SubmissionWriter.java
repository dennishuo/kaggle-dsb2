package org.dhuo;

import org.apache.commons.math3.distribution.NormalDistribution;

import java.io.*;
import java.util.*;

/**
 * Creates a validly-formatted submission.
 */
public class SubmissionWriter {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java SubmissionWriter <presubmit predictions>");
      System.exit(1);
    }

    // Using trendline(s) from spreadsheet, translate raw predictions.
    double sysIntercept = 51.355;
    double sysSlope = 0.715;
    double diaIntercept = 125.806;
    double diaSlope = 0.415;

    // Output header row.
    PrintStream out = System.out;
    out.print("Id");
    for (int i = 0; i < 600; ++i) {
      out.print(",P" + i);
    }
    out.println();

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
