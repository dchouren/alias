package com.tripadvisor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import com.google.common.collect.ImmutableSet;
//import com.TripResearch.object.Country;
import com.tripadvisor.PlaceType;

public class MatcherConstants
{
    private static final Pattern SYNONYMSFILE_DELIM = Pattern.compile("=>|,");
    private static final ESLogger LOGGER = Loggers.getLogger(MatcherConstants.class);

    protected static ImmutableSet<String> _readStopWords(CacheKey key)
    {
        String filename = _getFileName("stopwords", key);

        try (BufferedReader in = _createResourceReader(filename))
        {
            return ImmutableSet.copyOf(in.lines().map(String::trim).iterator());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    protected static HashMap<String, String> _readSynonyms(CacheKey k)
    {

        String filename = _getFileName("synonyms", k);
        HashMap<String, String> synonymsMap = new HashMap<String, String>();
        try (BufferedReader in = _createResourceReader(filename))
        {
            String synonym = null;
            while ((synonym = in.readLine()) != null)
            {
                final String[] items = SYNONYMSFILE_DELIM.split(synonym);
                if(items.length>=2) {
                    // The synonym file has lines like a,b,c => z.
                    // After splitting the last term in the list is the value with which all the other tokens should be replaced.
                    String replaceWith = items[items.length - 1].trim();
                    for (String s : items)
                    {
                        s = s.trim();
                        if (!s.equals(replaceWith))
                            synonymsMap.put(s, replaceWith);
                    }
                }
            }

            return synonymsMap;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static String _getFileName(String fileType, CacheKey key)
    {
        String placeTypeName = key.placeType.toString().toLowerCase();
        Map<Pair, String> fileNames = new HashMap<Pair, String>();

        fileNames.put(new Pair("stopwords", "street"), "addressStopWords.txt");
        fileNames.put(new Pair("stopwords", "name"), placeTypeName + "NameStopWords.txt");
        fileNames.put(new Pair("synonyms", "street"), "addressSynonyms.txt");
        fileNames.put(new Pair("synonyms", "name"), placeTypeName + "NameSynonyms.txt");


        String resourceFolder = "/indexing/";
//        Country countryIndex = Country.match(key.country);
        String countryIndex = "Not null, ha";
        if (countryIndex != null)
        {
//            String indexName = countryIndex.getIndexName();
            String indexName = "usa";
            if (indexName.equalsIgnoreCase("generic"))
            {
                return resourceFolder + fileNames.get(new Pair(fileType, key.fieldname));
            }
            return resourceFolder + indexName + "/" + fileNames.get(new Pair(fileType, key.fieldname));
        }
        LOGGER.warn("Stopwords and synonym files not found for ", key.country);
        return resourceFolder + fileNames.get(new Pair(fileType, key.fieldname));
    }

    protected static BufferedReader _createResourceReader(String filename)
    {
        try
        {
            return new BufferedReader(new InputStreamReader(FieldTokenMatchingPairCreator.class.getResourceAsStream(filename)));

        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Unable to access input file:" + filename);
        }
    }

    public static class Pair
    {

        private final String fileType;
        private final String fieldName;

        public Pair(String fileType, String fieldName)
        {
            this.fileType = fileType;
            this.fieldName = fieldName;
        }

        @Override
        public int hashCode()
        {
            return new HashCodeBuilder(17, 31)
                    .append(fileType)
                    .append(fieldName)
                    .toHashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Pair))
                return false;
            if (obj == this)
                return true;

            Pair rhs = (Pair) obj;
            return new EqualsBuilder()
                    .append(fileType, rhs.fileType)
                    .append(fieldName, rhs.fieldName)
                    .isEquals();
        }
    }

    protected static class CacheKey
    {
        private final String country;
        private final PlaceType placeType;
        private final String fieldname;

        public CacheKey(String country, PlaceType placeType, String fieldname)
        {
            this.country = country;
            this.placeType = placeType;
            this.fieldname = fieldname;
        }

        @Override
        public int hashCode()
        {
            return new HashCodeBuilder(17, 31)
                    .append(country)
                    .append(placeType)
                    .append(fieldname)
                    .toHashCode();
        }

//        @Override
//        public boolean equals(Object obj)
//        {
//            if (!(obj instanceof CacheKey))
//                return false;
//            if (obj == this)
//                return true;
//
//            CacheKey rhs = (CacheKey) obj;
//            return new EqualsBuilder()
//                    .append(country, rhs.country)
//                    .append(placeType, rhs.placeType)
//                    .append(fieldname, rhs.fieldname)
//                    .isEquals();
//        }
    }
}
