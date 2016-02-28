package org.dhuo;

import java.io.*;

/**
 * Creates a validly-formatted submission.
 */
public class SubmissionWriter {
  public static void main(String[] args) {
    PrintStream out = System.out;

    out.print("Id");
    for (int i = 0; i < 600; ++i) {
      out.print(",P" + i);
    }
    out.println();
    for (int caseNumber = 1; caseNumber <= 500; ++caseNumber) {
      out.print(caseNumber + "_Diastole");
      for (int vol = 0; vol < 600; ++vol) {
        out.print(",0.0");
      }
      out.println();
      out.print(caseNumber + "_Systole");
      for (int vol = 0; vol < 600; ++vol) {
        out.print(",0.0");
      }
      out.println();
    }
    out.flush();
  }
}
