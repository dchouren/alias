package com.tripadvisor;

import java.io.PrintWriter;
import java.text.DecimalFormat;

/**
 * Created by dchouren on 8/26/16.
 */

public class MatchWriter {

    private String matchFile;
    private String nonmatchFile;
    private String encoding = "UTF-8";
    private DecimalFormat df = new DecimalFormat("#.00");
    private PrintWriter matchWriter;
    private PrintWriter nonMatchWriter;
    private Double matchThreshold = Double.NEGATIVE_INFINITY;

    public static MatchWriter(String matchFile) {
        this(matchFile, "ShouldNeverGetWrittenTo.txt", Double.NEGATIVE_INFINITY);
    }

    public static MatchWriter(String matchFile, String nonMatchFile, Double matchThreshold) {
        this.matchFile = matchFile;
        this.nonmatchFile = nonMatchFile;
        this.matchThreshold = matchThreshold;

        try {
            this.matchWriter = new PrintWriter(matchFile, this.encoding);
            this.nonMatchWriter = new PrintWriter(nonMatchFile, this.encoding);
        } catch (Exception e) {
            e.printStackTrace(); System.err.println(e.getClass().getName()+": "+e.getMessage()); System.exit(0); return;
        }
    }

    public void writeResults(Double score, String primaryName, String aliasName) {
        String output = df.format(score) + "\t" + primaryName + "\t|\t" + aliasName);
        if (score >= this.matchThreshold || this.matchThreshold == Double.NEGATIVE_INFINITY) {
            this.matchWriter.println(output);
        }
        else {
            this.nonMatchWriter.println(output);
        }
    }
}