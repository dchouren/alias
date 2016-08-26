package com.tripadvisor;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.termvector.TermVectorResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.hppc.ObjectLookupContainer;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.json.JSONObject;
import org.json.JSONException;

import com.tripadvisor.RequestLocationDuplicationDoubleCheck.IDFClient;

/**
 * This class provides two implementations of the IDFClient interface defined in RequestLocationDuplicationDoubleCheck.java.
 * IDFClientWithElasticsearch implementation uses elastic search to get the IDFs of the tokens.
 * IDFClientWithoutElasticsearch implementation is a dummy client to get the IDFs of the tokens.
 * 
 * 
 * @author msrivastava
 * @since December 22, 2015
 *
 */

public class IDFClientWithElasticsearch implements IDFClient
{

    public static class IDFClientWithElasticsearchException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public IDFClientWithElasticsearchException(String message)
        {
            super(message);
        }
    }

    private static final Logger LOGGER = LogManager.getLogger(IDFClientWithElasticsearch.class);
    private static final TransportClient client = _createTransportClient();
    private static final ObjectLookupContainer<String> availableIndices = _getAvailableIndices();

    @Override
    public Map<String, Double> getInverseDocumentFrequencyByToken(final String country, final PlaceType placeType, final String field, final String tokenStr)
    {
        String placeTypeName = placeType.toString().toLowerCase();

//        String countryNameForElasticSearch = MatcherConstants._getCountryForFilename(country);
        String countryNameForElasticSearch = "usa";

        LOGGER.trace("Using elastic search to get the IDFs");

        if (availableIndices.contains(countryNameForElasticSearch))
        {
            JSONObject termVectorJson;
            try
            {
                termVectorJson = _getTermVectorJson(client, countryNameForElasticSearch, placeTypeName, field, tokenStr);
                if (termVectorJson.has(field))
                {
                    return _getTermIDF(termVectorJson, field);
                }
                else
                {
                    LOGGER.warn("Elastic search did not return anything for token " + tokenStr + " in " + country + ", " + placeType + ".");
                }
            }
            catch (ElasticsearchException | IOException | JSONException e)
            {
                throw new IDFClientWithElasticsearchException(e.getMessage());
            }
        }
        else
        {
            LOGGER.error("No index exists for the country " + country);
        }

        return new HashMap<String, Double>();

    }

    private static TransportClient _createTransportClient()
    {
        Properties prop = new Properties();
        try (InputStream in = IDFClientWithElasticsearch.class.getClassLoader().getResourceAsStream("ESConfig.properties"))
        {
            prop.load(in);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        final org.elasticsearch.common.settings.Settings clientSettings = ImmutableSettings.settingsBuilder()
                .put("cluster.name", prop.getProperty("CLUSTER_NAME")) // TODO change to get this from the existing code.
                .put("client.transport.sniff", true)
                .put("client.transport.ping_timeout", "180s")
                .build();
        final TransportClient client = new TransportClient(clientSettings);

        String[] addresses = prop.getProperty("ES_INDEX_URI").split("[,\\s]+");
        for (String address : addresses)
        {
            URI addressUri = null;
            try
            {
                addressUri = new URI(address);
            }
            catch (URISyntaxException e)
            {
                LOGGER.error("Error creating transport client", e);
                throw new IllegalArgumentException(e);

            }
            client.addTransportAddress(new InetSocketTransportAddress(addressUri.getHost(), addressUri.getPort()));
        }
        return client;
    }

    private static ObjectLookupContainer<String> _getAvailableIndices()
    {
        ObjectLookupContainer<String> availableIndices = client.admin().cluster()
                .prepareState().execute()
                .actionGet().getState()
                .getMetaData().aliases().keys();
        return availableIndices;

    }

    private Map<String, Double> _getTermIDF(JSONObject termVectorJson, String field) throws JSONException
    {
        Map<String, Integer> termFrequencies = new HashMap<String, Integer>();
        Integer docCount = termVectorJson.getJSONObject(field).getJSONObject("field_statistics").getInt("doc_count");
        JSONObject terms = termVectorJson.getJSONObject(field).getJSONObject("terms");
        Iterator<?> termKeys = terms.keys();
        while (termKeys.hasNext())
        {
            String key = (String) termKeys.next();
            if (terms.get(key) instanceof JSONObject)
            {
                if (terms.getJSONObject(key).has("doc_freq"))
                {
                    termFrequencies.put(key.toLowerCase(), terms.getJSONObject(key).getInt("doc_freq"));
                }
            }
        }
        Map<String, Double> termIDF = termFrequencies.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Math.log(docCount / (entry.getValue() + 1.0))));
        termIDF.put("default", Math.log(docCount + 1.0 / (1.0)));
        return termIDF;
    }

    private static JSONObject _getTermVectorJson(final TransportClient client, final String country, final String placeType, final String field, final String tokenStr) throws ElasticsearchException, IOException, JSONException
    {
        TermVectorResponse resp;
        XContentBuilder builder;

        resp = client.prepareTermVector().setIndex(country).setType(placeType)
                .setDoc(jsonBuilder()
                        .startObject()
                        .field(field, tokenStr)
                        .endObject())
                .setSelectedFields(field).setTermStatistics(true).execute().actionGet();
        builder = XContentFactory.jsonBuilder();
        builder.startObject();
        
       
        resp.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();

        String termVectorJsonString = XContentHelper.convertToJson(builder.bytes(), false);
        JSONObject termVectorJson = new JSONObject(termVectorJsonString);
        return termVectorJson.getJSONObject("term_vectors");

    }

}
