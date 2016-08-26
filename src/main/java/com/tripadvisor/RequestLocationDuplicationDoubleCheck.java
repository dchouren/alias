package com.tripadvisor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.apache.lucene.search.spell.LevensteinDistance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.Doubles;

import org.elasticsearch.ElasticsearchException;
import com.tripadvisor.PlaceType;
import com.tripadvisor.LocationPathElement;
import com.tripadvisor.MatchingRequest;
import com.tripadvisor.TokenAlignmentMatcher;
import com.tripadvisor.TokenAlignmentMatcher.FieldTokenMatchingPair;

import static com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER;

/**
 * This class scores how well a request duplicates a possible match. It extracts features like
 * city exact match, postal code exact match, phone number edit distance, name tokens match and street tokens match.
 * It then uses the coefficients from a trained logistic regression model to score how well
 * the request matches the old matcher top match response (possible match).
 * 
 * confirmDuplicateExists takes as input a request and the response from the old matcher.
 * The output of the function is an enum {Duplicate, NotDuplicate, No_Decison}.
 * No_decision is returned only if the request country is invalid or top match from the old matcher is not present.
 * 
 * 
 * @author msrivastava
 * @since December 20, 2015
 *
 */

public class RequestLocationDuplicationDoubleCheck
{
    private static final int FEATURE_VALUE_WHEN_MATCH = 1;
    private static final int FEATURE_VALUE_WHEN_NO_MATCH = 0;
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private static final Pattern NON_ALPHANUMBERIC = Pattern.compile("[^0-9a-zA-Z]");
    private static final Pattern NON_CHARS = Pattern.compile("[^a-zA-Z]");
    private static final double LEVENSTEIN_DISTANCE_THRESHOLD = 0.67;

    private final double m_decisionThreshold;
    private final double[] m_featureCoefs;

    public RequestLocationDuplicationDoubleCheck(double decision_threshold)
    {
        m_featureCoefs = _getClassifierCoefs();
        m_decisionThreshold = decision_threshold;
    }

    public enum Result
    {
        DUPLICATE, NOT_DUPLICATE, NO_DECISION;
    }

    public enum FeatureName
    {
        INTERCEPT, PHONE_NUMBER_EDIT_DISTANCE, POSTAL_CODE_EXACT_MATCH_1, POSTAL_CODE_EXACT_MATCH_0, CITY_EXACT_MATCH_1, CITY_EXACT_MATCH_0, NORMALISED_NAME_SCORE, NORMALISED_STREET_SCORE;

    }

//    public static class DuplicateMatcherException extends RuntimeException
//    {
//        private static final long serialVersionUID = 1L;
//
//        public DuplicateMatcherException(Exception e)
//        {
//            super(e);
//        }
//    }
//
//    private static final ESLogger LOGGER = Loggers.getLogger(RequestLocationDuplicationDoubleCheck.class);
//
//    public @Nonnull Result confirmDuplicateExists(@Nonnull final MatchingRequest request, @Nonnull final MatchingResult response, @Nonnull final IDFClient client)
//    {
//        if ((StringUtils.isBlank(request.getCountryName())) || (response.getTopMatch() == null))
//        {
//            LOGGER.error("Country " + request.getCountryName() + "Top match" + response.getTopMatch() + "are not supported by DuplicateMatcher.");
//            return Result.NO_DECISION;
//        }
//
//        try
//        {
//            double[] featureVector = _getFeatures(request, response, client);
//            double score = _scoreFeatures(featureVector);
//            LOGGER.info("score" + String.valueOf(score));
//            if (score > this.m_decisionThreshold)
//            {
//                return Result.DUPLICATE;
//            }
//            return Result.NOT_DUPLICATE;
//        }
//        catch (ElasticsearchException | IOException | URISyntaxException e)
//        {
//            throw new DuplicateMatcherException(e);
//        }
//
//    }
//
//    private static double[] _getFeatures(final MatchingRequest request, final MatchingResult response, final IDFClient client) throws ElasticsearchException, IOException, URISyntaxException
//    {
//        EnumMap<FeatureName, Double> featureMap = new EnumMap<FeatureName, Double>(FeatureName.class);
//        Arrays.stream(FeatureName.values()).forEach(f -> featureMap.put(f, 0.0));
//
//        featureMap.put(FeatureName.INTERCEPT, 1.0);
//
//        if (!StringUtils.isBlank(request.getPhone()) && (!StringUtils.isBlank(response.getTopMatch().getTelephone())))
//        {
//            double phoneNumberEditDistance = _getPhoneNumberEditScore(request.getPhone(), response.getTopMatch().getTelephone());
//            featureMap.put(FeatureName.PHONE_NUMBER_EDIT_DISTANCE, phoneNumberEditDistance);
//        }
//        else
//        {
//            LOGGER.info("Request phone number " + request.getPhone() + " or Possible match phone number " + response.getTopMatch().getTelephone() + "  is blank");
//        }
//
//        if ((!StringUtils.isBlank(request.getPostalCode())) && (!StringUtils.isBlank(response.getTopMatch().getPostalCode())))
//        {
//            double postalCodeMatch = _getPostalCodeExactMatch(request.getPostalCode(), response.getTopMatch().getPostalCode());
//            if (postalCodeMatch == FEATURE_VALUE_WHEN_MATCH)
//            {
//                featureMap.put(FeatureName.POSTAL_CODE_EXACT_MATCH_1, 1.0);
//            }
//            else if (postalCodeMatch == FEATURE_VALUE_WHEN_NO_MATCH)
//            {
//                featureMap.put(FeatureName.POSTAL_CODE_EXACT_MATCH_0, 1.0);
//            } // if returned value is -1 then both the features have 0 value.
//        }
//        else
//        {
//            LOGGER.info("Request postal code " + request.getPostalCode() + " or Possible match postal code " + response.getTopMatch().getPostalCode() + " is blank");
//        }
//
//        if ((!StringUtils.isBlank(request.getCity())) && (response.getTopMatch().getLocationPath() != null))
//        {
//            double cityMatch = _getCityExactMatch(request.getCity(), response.getTopMatch().getLocationPath());
//            if (cityMatch == FEATURE_VALUE_WHEN_MATCH)
//            {
//                featureMap.put(FeatureName.CITY_EXACT_MATCH_1, 1.0);
//            }
//            else if (cityMatch == FEATURE_VALUE_WHEN_NO_MATCH)
//            {
//                featureMap.put(FeatureName.CITY_EXACT_MATCH_0, 1.0);
//            } // if returned value is -1 then both the features have 0 value.
//        }
//
//        FieldTokenMatchingPairCreator tc = new FieldTokenMatchingPairCreator(client);
//        final TokenAlignmentMatcher matcher = new TokenAlignmentMatcher();
//
//        if (!StringUtils.isBlank(request.getName()) && !StringUtils.isBlank(response.getTopMatch().getName()))
//        {
//            FieldTokenMatchingPair namePair = tc.pair(request.getName(), response.getTopMatch().getName(), request.getCountryName(), request.getPlaceType(), "name");
//
//            // name1, name2, "usa", place, "name"
//
//            if (namePair.numRequestTokens() == 0)
//            {
//                LOGGER.info("Request name field does not have valid tokens");
//            }
//            else if (namePair.numCandidateTokens() == 0)
//            {
//                LOGGER.info("Possible match name field does not have valid tokens");
//            }
//            else
//            {
//                double nameMatchingScore = 0;
//                nameMatchingScore = matcher.scoreMatch(namePair);
//                featureMap.put(FeatureName.NORMALISED_NAME_SCORE, nameMatchingScore / Math.min(namePair.sumRequestIDF(), namePair.sumCandidateIDF()));
//            }
//        }
//        else
//        {
//            LOGGER.info("Request name field " + request.getName() + "or Possible match name field " + request.getName() + " is blank");
//        }
//
//        if (!StringUtils.isBlank(request.getStreet()) && !StringUtils.isBlank(response.getTopMatch().getStreet()))
//        {
//            FieldTokenMatchingPair streetPair = tc.pair(request.getStreet(), response.getTopMatch().getStreet(), request.getCountryName(), request.getPlaceType(), "street");
//            if (streetPair.numRequestTokens() == 0)
//            {
//                LOGGER.info("Request street field does not have valid tokens");
//            }
//            else if (streetPair.numCandidateTokens() == 0)
//            {
//                LOGGER.info("Possible match street field does not have valid tokens");
//            }
//            else
//            {
//                double streetMatchingScore = 0;
//                streetMatchingScore = matcher.scoreMatch(streetPair);
//                featureMap.put(FeatureName.NORMALISED_STREET_SCORE, streetMatchingScore / Math.min(streetPair.sumRequestIDF(), streetPair.sumCandidateIDF()));
//            }
//        }
//        else
//        {
//            LOGGER.info("Request street field " + request.getStreet() + "or Possible match street field " + request.getStreet() + " is blank");
//        }
//
//        return Doubles.toArray(featureMap.values());
//    }

    /**
     * Determine if the city name of the request matches any parents of the possible match.
     * 
     * @param requestCity
     * @param possibleMatchParents
     * @return 1 (if they match), 0 (if they do not match)
     */
    private static double _getCityExactMatch(@Nonnull final String requestCity, @Nonnull final List<LocationPathElement> possibleMatchParents)
    {

        String requestCityForMatching = NON_CHARS.matcher(requestCity).replaceAll("");

        if (!StringUtils.isBlank(requestCityForMatching))
        {
            if (possibleMatchParents.stream()
                    .map(LocationPathElement::getName)
                    .anyMatch((s) -> _findDuplicateBasedOnLevensteinDistance(s.toLowerCase(), requestCityForMatching.toLowerCase())))
                return 1;
            else
                return 0;
        }
        else
        {
//            LOGGER.info("Request city" + requestCity + "is invalid");
            return -1;
        }

    }

    private static boolean _findDuplicateBasedOnLevensteinDistance(String a, String b)
    {
        final LevensteinDistance levensteinDistance = new LevensteinDistance();
        return (levensteinDistance.getDistance(a, b) > LEVENSTEIN_DISTANCE_THRESHOLD);
    }

    /**
     * Determine the Levenstein distance between the request and possibleMatch phone number. All non numeric characters
     * are removed from the phone number before calculating the distance.
     * 
     * @param requestPhoneNumber
     * @param possibleMatchPhoneNumber
     * @return Returns a float between 0 and 1 based on how similar the phone numbers are. Returning a value of 1 means the phone numbers are identical and 0
     *         means they are maximally different.
     */
    private static double _getPhoneNumberEditScore(@Nonnull final String requestPhoneNumber, @Nonnull final String possibleMatchPhoneNumber)
    {
        String requestPhoneNumberForMatching = NON_DIGITS.matcher(requestPhoneNumber).replaceAll("");
        String possibleMatchPhoneNumberForMatching = NON_DIGITS.matcher(possibleMatchPhoneNumber).replaceAll("");
        if (!StringUtils.isBlank(requestPhoneNumberForMatching) && !StringUtils.isBlank(possibleMatchPhoneNumberForMatching))
        {
            final LevensteinDistance levensteinDistance = new LevensteinDistance();
            return levensteinDistance.getDistance(requestPhoneNumberForMatching, possibleMatchPhoneNumberForMatching);
        }
        else
        {
//            LOGGER.info("Request phone number" + requestPhoneNumber + "and Possible match phone number" + possibleMatchPhoneNumber + "is invalid");
            return 0.0;
        }

    }

    /**
     * Determine if the postal code of the request matches possibleMatch. All non alphanumeric characters
     * are removed from the postal codes before matching.
     * 
     * @param requestPostalCode
     * @param possibleMatchPostalCode
     * @return 1 (if they match), 0 (if they do not match) and -1 If one of the input is not valid or null.
     */
    private static int _getPostalCodeExactMatch(@Nonnull final String requestPostalCode, @Nonnull final String possibleMatchPostalCode)
    {

        String requestPostalCodeForMatching = NON_ALPHANUMBERIC.matcher(requestPostalCode).replaceAll("");
        String possibleMatchPostalCodeForMatching = NON_ALPHANUMBERIC.matcher(possibleMatchPostalCode).replaceAll("");
        if (!StringUtils.isBlank(requestPostalCodeForMatching) && !StringUtils.isBlank(possibleMatchPostalCodeForMatching))
        {
            return requestPostalCodeForMatching.equalsIgnoreCase(possibleMatchPostalCodeForMatching) ? FEATURE_VALUE_WHEN_MATCH : FEATURE_VALUE_WHEN_NO_MATCH;
        }
        else
        {
//            LOGGER.info("Request postal code" + requestPostalCode + " Possible Match postal code" + possibleMatchPostalCode + " are invalid");
            return -1;
        }

    }

    private static double[] _getClassifierCoefs()
    {
        EnumMap<FeatureName, Double> featureCoefs = new EnumMap<FeatureName, Double>(FeatureName.class);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(RequestLocationDuplicationDoubleCheck.class.getClassLoader().getResourceAsStream("LogisticRegressionCoefficients.csv"))))
        {
            in.lines()
                    .skip(1) // Header
                    .map(s -> s.split(","))
                    .filter(a -> a.length == 2)
                    .forEach(a -> featureCoefs.put(FeatureName.valueOf(a[0].trim()), Double.valueOf(a[1])));
        }
        catch (IOException e)
        {

            throw new UncheckedIOException(e);
        }
        return Doubles.toArray(featureCoefs.values());
    }

    private static void _printFeatures(final double[] featureVector)
    {
        FeatureName[] features = FeatureName.values();
        for (int i = 0; i < FeatureName.values().length; i++)
        {
            System.out.println(features[i]);
            System.out.println(featureVector[i] + "*");
        }
    }

    private double _scoreFeatures(final double[] featureVector) throws JsonProcessingException, IOException
    {
        double score = 0.0;
        for (int i = 0; i < FeatureName.values().length; i++)
        {
            score += m_featureCoefs[i] * featureVector[i];
        }
        score = 1.0 / (1.0 + Math.exp(-score));
        return score;

    }

    public interface IDFClient
    {

        public Map<String, Double> getInverseDocumentFrequencyByToken(String country, PlaceType placeType, String field, String tokenStr);

    }

}
