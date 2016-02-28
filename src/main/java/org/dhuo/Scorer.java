package org.dhuo;

import java.io.*;
import java.util.*;

/**
 * Computes the score of a complete submission against the known answers.
 */
public class Scorer {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println(
          "Usage: java -cp kaggle-dsb2-1.0.jar org.dhuo.Scorer <submission file> <answers file>");
    }

    // From case id to the array of CDF values for Systole.
    Map<String, double[]> sysProbs = new TreeMap<String, double[]>();

    // From case id to the array of CDF values for Diastole.
    Map<String, double[]> diaProbs = new TreeMap<String, double[]>();

    System.out.println("Parsing submission data...");
    {
      Scanner scan = new Scanner(new FileInputStream(args[0]));
      scan.nextLine();  // Skip header line
      while (scan.hasNextLine()) {
        String line = scan.nextLine();
        String[] parts = line.split(",");
        String[] idTokens = parts[0].split("_");
        double[] probs = new double[600];
        for (int i = 1; i <= 600; ++i) {
          probs[i - 1] = Double.parseDouble(parts[i]);
        }

        if ("Diastole".equals(idTokens[1])) {
          diaProbs.put(idTokens[0], probs);
        } else if ("Systole".equals(idTokens[1])) {
          sysProbs.put(idTokens[0], probs);
        } else {
          System.err.println("Invalid row: " + line);
          System.exit(1);
        }
      }
    }
    System.out.format(
        "Parsed %d sysProbs and %d diaProbs\n", sysProbs.size(), diaProbs.size());

    Map<String, Double> sysAnswers = new TreeMap<String, Double>();
    Map<String, Double> diaAnswers = new TreeMap<String, Double>();

    System.out.println("Parsing answers...");
    {
      Scanner scan = new Scanner(new FileInputStream(args[1]));
      scan.nextLine();  // Skip header line
      while (scan.hasNextLine()) {
        String line = scan.nextLine();
        String[] parts = line.split(",");
        sysAnswers.put(parts[0], Double.parseDouble(parts[1]));
        diaAnswers.put(parts[0], Double.parseDouble(parts[2]));
      }
    }
    System.out.format("Parsed %d sysAnswers and %d diaAnswers\n",
        sysAnswers.size(), diaAnswers.size());

    System.out.println("Adding up score...");
    double finalScore = 0;
    if (sysAnswers.keySet().size() != diaAnswers.keySet().size()) {
      System.err.format(
          "Mismatched keySet size for answers! %d vs %d\n",
          sysAnswers.keySet().size(), diaAnswers.keySet().size());
    }
    Set<String> caseIds = sysAnswers.keySet();
    for (String caseId : caseIds) {
      double sysScore = 0;
      for (int vol = 0; vol < 600; ++vol) {
        if (vol > 0 && sysProbs.get(caseId)[vol] < sysProbs.get(caseId)[vol - 1]) {
          System.err.println("Decreased CDF for case " + caseId + "! At vol " + vol);
          System.exit(1);
        }
        double err = sysProbs.get(caseId)[vol] - (vol >= sysAnswers.get(caseId) ? 1.0 : 0.0);
        err *= err;
        sysScore += err;
      }
      finalScore += sysScore;

      double diaScore = 0;
      for (int vol = 0; vol < 600; ++vol) {
        if (vol > 0 && diaProbs.get(caseId)[vol] < diaProbs.get(caseId)[vol - 1]) {
          System.err.println("Decreased CDF for case " + caseId + "! At vol " + vol);
          System.exit(1);
        }
        double err = diaProbs.get(caseId)[vol] - (vol >= diaAnswers.get(caseId) ? 1.0 : 0.0);
        err *= err;
        diaScore += err;
      }
      finalScore += diaScore;
      System.out.format("%s: %f %f\n", caseId, sysScore / 600, diaScore / 600);
    }
    finalScore /= 600 * caseIds.size() * 2;
    System.out.println("Final score: " + finalScore);


  }
}
