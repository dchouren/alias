package com.tripadvisor;


import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

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

import com.tripadvisor.Matcher;
import com.tripadvisor.MatchWriter;

import static com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER;

/**
 * Created by dchouren on 8/23/16.
 */
public class Connect {


    public static void main(String[] args) {

        String hostname = "tripmonster.tripadvisor.com";
        String port = "22";
        Connection conn = _openPSQL(hostname, port);

        String psql = "select primaryname, aliasname from t_location l join t_locationaliases a on l.id=a.locationid where isfitforsearch='T' or isfitformeta limit 10000;";
        final int PRIMARYNAME = 1;
        final int ALIASNAME = 2;

        String matchFile = args[0];
        String nonMatchFile = args[1];

        Matcher matcher = new Matcher();
        MatchWriter writer = new MatchWriter(matchFile, nonMatchFile, Double.NEGATIVE_INFINITY);


        try {
            Statement statement = conn.createStatement();
            ResultSet selectResult = statement.executeQuery(psql);
            ResultSet resultSet = statement.executeQuery(psql);
            ResultSetMetaData rsmd = resultSet.getMetaData();
            int numCols = rsmd.getColumnCount();
            while (resultSet.next()) {
                String primaryName = resultSet.getString(PRIMARYNAME);
                String aliasName = resultSet.getString(ALIASNAME);
                aliasName = _processAlias(aliasName);
                Double matchScore = matcher.getMatchScore(primaryName, aliasName);

                matchWriter.println(df.format(matchScore) + "\t" + primaryName + "\t|\t" + aliasName);
//                System.out.println("Match score: " + );
            }
            System.out.println();
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName()+": "+ e.getMessage() );
            System.exit(0);
        }

        try {
            conn.close(); matchWriter.close(); nonmatchWriter.close();
            System.out.println("Connection closed");

        } catch (Exception e) {
            e.printStackTrace(); System.err.println(e.getClass().getName()+": "+e.getMessage()); System.exit(0); return;
        }

        _testQueries(idfClient);
    }

    private static String _splitCamelCase(String aliasName) {
        String newAlias = String.join(" ", StringUtils.splitByCharacterTypeCamelCase(aliasName));
        return newAlias;
    }

    private static String _processAlias(String aliasName) {
        String newAlias = aliasName;
        newAlias = _splitCamelCase(aliasName);

        return newAlias;
    }




    /*
    Open PostgreSQL connection
     */
    private static Connection _openPSQL(String hostname, String port) {
        Connection c = null;
        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager
                    .getConnection("jdbc:postgresql://" + hostname,
                            "tripmonster", port);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName()+": "+e.getMessage());
            System.exit(0);
        }
        System.out.println("Opened " + hostname + " successfully\n");

        return c;
    }

    /*
    Prompt user for primary name and alias name
     */
    private static String[] _scanNames(Scanner reader) {
        System.out.println("Enter primary name: ");
        String primaryName = reader.nextLine();
        System.out.println("Enter alias name: ");
        String aliasName = reader.nextLine();
        String[] names = {primaryName, aliasName};

//        String[] names = {"This is a test", "This is not a test"};

        return names;
    }

    /*
    Find match score for primary and alias names
     */
    private static void _testQueries(TokenAlignmentMatcher matcher, FieldTokenMatchingPairCreator tc) {
        Scanner reader = new Scanner(System.in);

        String[] names = _scanNames(reader);
        String primaryName = names[0];
        String aliasName = names[1];
        if (primaryName == "STOP" || aliasName == "STOP") {
            return;
        }

        String country = "usa";
        PlaceType placeType = PlaceType.getById(10023); //10021, 10022, 10023: attraction, restaurant, accomodation

        FieldTokenMatchingPair namePair;

        while (primaryName != "STOP" && aliasName != "STOP") {

            try {
                namePair = tc.pair(primaryName, aliasName, country, placeType, "name");
            } catch(IOException | URISyntaxException urie) {
                LOGGER.info("IOError or URISyntaxException on creating FieldTokenMatchingPair");
                return;
            }

            if (namePair.numRequestTokens() == 0)
            {
                LOGGER.info("Request name field does not have valid tokens");
            }
            else if (namePair.numCandidateTokens() == 0)
            {
                LOGGER.info("Possible match name field does not have valid tokens");
            }
            else
            {
                double nameMatchingScore = 0;
                nameMatchingScore = matcher.scoreMatch(namePair);
                System.out.println("Match score is " + nameMatchingScore + "\n");
            }


            names = _scanNames(reader);
            primaryName = names[0];
            aliasName = names[1];
            if (primaryName == "STOP" || aliasName == "STOP") {
                return;
            }
        }
    }

}


