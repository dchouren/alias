package com.tripadvisor;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.Scanner;
import com.tripadvisor.TokenAlignmentMatcher.FieldTokenMatchingPair;
import com.tripadvisor.RequestLocationDuplicationDoubleCheck.IDFClient;

import static com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER;

/**
 * Created by dchouren on 8/26/16.
 */
public class Matcher {

    private IDFClientWithElasticsearch idfClient = new IDFClientWithElasticsearch();
    private FieldTokenMatchingPairCreator tc = new FieldTokenMatchingPairCreator((idfClient));
    private TokenAlignmentMatcher matcher = new TokenAlignmentMatcher();



    public static double getMatchScore(String name1, String name2) {

        String country = "usa";
        PlaceType placeType = PlaceType.getById(10023); //10021, 10022, 10023: attraction, restaurant, accomodation

        FieldTokenMatchingPair namePair;
        try {
            namePair = tc.pair(name1, name2, country, placeType, "name");
        } catch(IOException | URISyntaxException urie) {
            LOGGER.info("IOError or URISyntaxException on creating FieldTokenMatchingPair");

            return -1;
        }

        if (namePair.numRequestTokens() == 0) {
            LOGGER.info("Request name field does not have valid tokens");
        }
        else if (namePair.numCandidateTokens() == 0) {
            LOGGER.info("Possible match name field does not have valid tokens");
        }
        else {
            double nameMatchingScore = 0;
            nameMatchingScore = this.matcher.scoreMatch(namePair);
            return nameMatchingScore;
        }

        return -1;
    }
}