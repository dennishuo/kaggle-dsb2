package org.dhuo;

import java.io.*;
import java.util.*;

/**
 * Creates a validly-formatted submission.
 */
public class SubmissionWriter {
  public static void main(String[] args) throws Exception {

    boolean usingObservedProb = false;
    double[] sysCount = new double[600];
    double[] diaCount = new double[600];
    if (args.length == 1) {
      // Supplied train.csv.
      usingObservedProb = true;

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
    for (int caseNumber = 1; caseNumber <= 500; ++caseNumber) {
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
    out.flush();
  }
}
