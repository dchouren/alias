package com.tripadvisor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import org.apache.lucene.search.spell.LevensteinDistance;
import org.elasticsearch.ElasticsearchException;
import com.google.common.collect.ImmutableSet;
import com.tripadvisor.RequestLocationDuplicationDoubleCheck.IDFClient;
import com.tripadvisor.TokenAlignmentMatcher.FieldTokenMatchingPair;
import com.tripadvisor.MatcherConstants.CacheKey;

/**
 * This class implements the FieldTokenMatchingPair interface defined in TokenAlignmentMatcher.
 * It uses the client to get the idfs of each term in the request and possible match and returns
 * the pair of tokens for the TokenAlignmentMatcher.
 *
 * _pair function takes as input argument a request field, possible match field and a client and returns a pair of tokens.
 *
 * 
 * @author msrivastava
 * @since December 18, 2015
 *
 */
public class FieldTokenMatchingPairCreator
{

    private static final double LEVENSTEIN_DISTANCE_THRESHOLD = 0.67;
    private static final Pattern TOKEN_DELIM = Pattern.compile("\\W+");
    private static final ConcurrentMap<CacheKey, ImmutableSet<String>> STOP_WORDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<CacheKey, HashMap<String, String>> SYNONYMS = new ConcurrentHashMap<>();

    private final IDFClient m_client;

    public FieldTokenMatchingPairCreator(@Nonnull IDFClient client)
    {
        m_client = client;
    }

    private static ImmutableSet<String> stopWordsFor(String country, PlaceType placetype, String fieldname)
    {
        return STOP_WORDS.computeIfAbsent(new CacheKey(country, placetype, fieldname), MatcherConstants::_readStopWords);
    }

    private static HashMap<String, String> synonymsFor(String country, PlaceType placetype, String fieldname)
    {
        return SYNONYMS.computeIfAbsent(new CacheKey(country, placetype, fieldname), MatcherConstants::_readSynonyms);
    }

    FieldTokenMatchingPair pair(@Nonnull final String requestFieldValue,
            @Nonnull final String possibleMatchFieldValue,
            @Nonnull final String country,
            @Nonnull final PlaceType placeType,  // fieldnam
            @Nonnull final String fieldname) throws ElasticsearchException, IOException, URISyntaxException
    {
        List<Token> requestTokens = _tokens(requestFieldValue.replace("'", ""), country, placeType, fieldname);
        List<Token> possibleMatchTokens = _tokens(possibleMatchFieldValue.replace("'", ""), country, placeType, fieldname);
        return new SimpleFieldTokenMatchingPair(requestTokens, possibleMatchTokens);
    }

    private List<Token> _tokens(final String tokenStr, String country, PlaceType placeType, String fieldname)
    {
        String tokenStrLowercase = tokenStr.toLowerCase();
        Map<String, Double> idfByToken = m_client.getInverseDocumentFrequencyByToken(country, placeType, fieldname, tokenStrLowercase);
        if (idfByToken.isEmpty())
        {
            return Collections.<Token> emptyList();
        }

        final HashMap<String, String> synonyms = synonymsFor(country, placeType, fieldname);
        final ImmutableSet<String> stopWords = stopWordsFor(country, placeType, fieldname);
        return TOKEN_DELIM.splitAsStream(tokenStrLowercase)
                .map(s -> synonyms.getOrDefault(s, s))
                .map(s -> new Token(s, _idfFor(s, stopWords, idfByToken)))
                .collect(Collectors.toList());

    }

    private double _idfFor(String s, ImmutableSet<String> stopWords, Map<String, Double> mapIDF)
    {
        if (mapIDF.containsKey(s))
        {
            return mapIDF.get(s);
        }
        if (stopWords.contains(s))
        {
            return 1.0;
        }
        // the default IDF is used for tokens that are not present in index and are not stop words (rare unique word).
        // is calculated using the term frequency of 1 and the document frequency in the index.
        return mapIDF.get("default");
    }

    public static class SimpleFieldTokenMatchingPair implements FieldTokenMatchingPair
    {
        private static final LevensteinDistance levensteinDistance = new LevensteinDistance();

        private final List<Token> requestTokens, candidateTokens;

        public SimpleFieldTokenMatchingPair(List<Token> requestTokens, List<Token> candidateTokens)
        {
            this.requestTokens = requestTokens;
            this.candidateTokens = candidateTokens;
        }

        @Override
        public int numRequestTokens()
        {
            return requestTokens.size();
        }

        @Override
        public int numCandidateTokens()
        {
            return candidateTokens.size();
        }

        @Override
        public String requestTokenAt(int requestTokenIndex)
        {
            return requestTokens.get(requestTokenIndex).str;
        }

        @Override
        public double idfRequestTokenAt(int requestTokenIndex)
        {
            return requestTokens.get(requestTokenIndex).idf;
        }

        @Override
        public String candidateTokenAt(int cadidateTokenIndex)
        {
            return candidateTokens.get(cadidateTokenIndex).str;
        }

        @Override
        public double fracMatchingChars(int requestTokenIndex, int candidateTokenIndex)
        {
            return levensteinDistance.getDistance(requestTokens.get(requestTokenIndex).str,
                                                  candidateTokens.get(candidateTokenIndex).str);
        }

        @Override
        public String toString()
        {
            return requestTokens.toString() + '\n' + candidateTokens.toString();
        }

        @Override
        public int findTokenPosInCandidate(int reqRow)
        {
            int candPos = -1;
            for (int candCol = 0; candCol < numCandidateTokens(); candCol++)
            {
                if (fracMatchingChars(reqRow, candCol) > LEVENSTEIN_DISTANCE_THRESHOLD)
                {
                    if (candPos == -1 || Math.abs(reqRow - candPos) > Math.abs(reqRow - candCol))
                        candPos = candCol;
                }
            }
            return candPos;
        }

        @Override
        public double idfCandidateTokenAt(int candTokenIndex)
        {
            return candidateTokens.get(candTokenIndex).idf;
        }

        @Override
        public double sumRequestIDF()
        {
            double sumIDF = 0;

            for (int reqRow = 0; reqRow < numRequestTokens(); reqRow++)
            {
                sumIDF += idfRequestTokenAt(reqRow);
            }
            return sumIDF;

        }

        @Override
        public double sumCandidateIDF()
        {
            double sumIDF = 0;

            for (int candCol = 0; candCol < numCandidateTokens(); candCol++)
            {
                sumIDF += idfCandidateTokenAt(candCol);
            }
            return sumIDF;
        }

    }

    public static class Token
    {
        public final String str;
        public final double idf;

        public Token(String chars, double idf)
        {
            this.str = chars;
            this.idf = idf;
        }

        @Override
        public String toString()
        {
            return str + '[' + idf + ']';
        }

    }

}
