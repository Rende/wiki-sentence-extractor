/**
 *
 */
package de.dfki.mlt.wse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import de.dfki.mlt.wse.preferences.Config;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ElasticsearchService {
	private Client client;
	private BulkProcessor bulkProcessor;

	public ElasticsearchService() {
		try {
			getClient();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("resource")
	public Client getClient() throws UnknownHostException {
		if (client == null) {
			Settings settings = Settings.builder()
					.put(Config.CLUSTER_NAME, Config.getInstance().getString(Config.CLUSTER_NAME)).build();
			client = new PreBuiltTransportClient(settings).addTransportAddress(
					new TransportAddress(InetAddress.getByName(Config.getInstance().getString(Config.HOST)),
							Config.getInstance().getInt(Config.PORT)));
		}
		return client;
	}

	public void stopConnection() throws UnknownHostException, InterruptedException {
		if (!getBulkProcessor().awaitClose(60, TimeUnit.SECONDS))
			throw new InterruptedException("The bulk process is unfinished!");
		getClient().close();
	}

	/**
	 * Call this for candidate items
	 * one-to-many
	 */
	public List<String> getRelatedItems(String label, String lang) {
		List<String> idList = new ArrayList<String>();
		SearchResponse response = searchItemsByLabel(label, lang);
		String itemId = "";
		if (isResponseValid(response)) {
			for (SearchHit hit : response.getHits()) {
				itemId = hit.getId();
				idList.add(itemId);
			}
		}
		return idList;
	}

	/**
	 * Call this for exact item
	 * one-to-one
	 */
	public String getItemId(String wikiLink, String lang) {
		String itemId = "";
		SearchResponse response = searchItemByWikipediaLink(wikiLink, lang);
		if (isResponseValid(response)) {
			for (SearchHit hit : response.getHits()) {
				itemId = hit.getId();
				break;
			}
		}
		return itemId;
	}

	public boolean isResponseValid(SearchResponse response) {
		return response != null && response.getHits().getTotalHits() > 0;
	}

	/**
	 * Returns all found items, 1-to-N matching
	 */
	private SearchResponse searchItemsByLabel(String label, String lang) {
		QueryBuilder matchPhrase = QueryBuilders.matchPhraseQuery("labels." + lang, label);
		QueryBuilder query = QueryBuilders.boolQuery().must(QueryBuilders.termQuery("type", "item"))
				.must(QueryBuilders.nestedQuery("labels", matchPhrase, ScoreMode.Max));
		try {
			SearchRequestBuilder requestBuilder = getClient()
					.prepareSearch(Config.getInstance().getString(Config.WIKIDATA_INDEX))
					.setTypes(Config.getInstance().getString(Config.WIKIDATA_ENTITY)).setQuery(query).setSize(10000);
			SearchResponse response = requestBuilder.execute().actionGet();
			return response;

		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Returns one item, 1-to-1 matching
	 */
	private SearchResponse searchItemByWikipediaLink(String wikiLink, String lang) {
		QueryBuilder matchPhrase = QueryBuilders.termQuery("sitelinks." + lang, wikiLink);
		QueryBuilder query = QueryBuilders.boolQuery().must(QueryBuilders.termQuery("type", "item"))
				.must(QueryBuilders.nestedQuery("sitelinks", matchPhrase, ScoreMode.Max));
		try {
			SearchRequestBuilder requestBuilder = getClient()
					.prepareSearch(Config.getInstance().getString(Config.WIKIDATA_INDEX))
					.setTypes(Config.getInstance().getString(Config.WIKIDATA_ENTITY)).setQuery(query).setSize(1);
			SearchResponse response = requestBuilder.execute().actionGet();
			return response;
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	private BulkProcessor getBulkProcessor() throws UnknownHostException {
		if (bulkProcessor == null) {
			bulkProcessor = BulkProcessor.builder(getClient(), new BulkProcessor.Listener() {
				@Override
				public void beforeBulk(long executionId, BulkRequest request) {
				}

				@Override
				public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
					if (response.hasFailures()) {
						App.LOG.error("Elasticsearch Service getBulkProcessor() " + response.buildFailureMessage());
					}
				}

				@Override
				public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
					App.LOG.error("Elasticsearch Service getBulkProcessor() " + failure.getMessage());

				}
			}).setBulkActions(20000).setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB))
					.setFlushInterval(TimeValue.timeValueSeconds(1)).setConcurrentRequests(1)
					.setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3)).build();
		}

		return bulkProcessor;
	}

	public void insertSentence(String pageId, String sentence, List<String> subjectId, String wikipediaTitle,
			String tokenizedSentence) {
		HashMap<String, Object> dataAsMap = new HashMap<String, Object>();
		dataAsMap.put("page-id", Long.parseLong(pageId));
		dataAsMap.put("title", wikipediaTitle);
		dataAsMap.put("subject-id", subjectId);
		dataAsMap.put("sentence", sentence);
		dataAsMap.put("lem-sentence", tokenizedSentence);

		IndexRequest indexRequest = Requests.indexRequest()
				.index(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE_INDEX))
				.type(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE)).source(dataAsMap, XContentType.JSON);
		try {
			getBulkProcessor().add(indexRequest);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

	}

	public boolean checkAndCreateIndex(String indexName) throws IOException, InterruptedException {
		boolean result = false;
		IndicesAdminClient indicesAdminClient = getClient().admin().indices();
		final IndicesExistsResponse indexExistReponse = indicesAdminClient.prepareExists(indexName).execute()
				.actionGet();
		if (indexExistReponse.isExists()) {
			deleteIndex(indicesAdminClient, indexName);
		}
		if (createIndex(indicesAdminClient, indexName)) {
			result = putMappingForSentence(indicesAdminClient);
		}
		return result;
	}

	private void deleteIndex(IndicesAdminClient indicesAdminClient, String indexName) {
		final DeleteIndexRequestBuilder delIdx = indicesAdminClient.prepareDelete(indexName);
		delIdx.execute().actionGet();
	}

	private boolean createIndex(IndicesAdminClient indicesAdminClient, String indexName) {
		final CreateIndexRequestBuilder createIndexRequestBuilder = indicesAdminClient.prepareCreate(indexName)
				.setSettings(Settings.builder()
						.put(Config.NUMBER_OF_SHARDS, Config.getInstance().getInt(Config.NUMBER_OF_SHARDS))
						.put(Config.NUMBER_OF_REPLICAS, Config.getInstance().getInt(Config.NUMBER_OF_REPLICAS)));
		CreateIndexResponse createIndexResponse = null;
		createIndexResponse = createIndexRequestBuilder.execute().actionGet();
		return createIndexResponse != null && createIndexResponse.isAcknowledged();
	}

	public boolean putMappingForSentence(IndicesAdminClient indicesAdminClient) throws IOException {
		XContentBuilder mappingBuilder = XContentFactory.jsonBuilder().startObject()
				.startObject(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE))
				.field("dynamic", "true")
				.startObject("properties")
				.startObject("page-id").field("type", "long").endObject()
				.startObject("subject-id").field("type", "keyword").endObject()
				.startObject("sentence").field("type", "text").endObject()
				.startObject("lem-sentence").field("type", "text").endObject()
				.endObject() // properties
				.endObject()// documentType
				.endObject();

		PutMappingResponse putMappingResponse = indicesAdminClient
				.preparePutMapping(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE_INDEX))
				.setType(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE)).setSource(mappingBuilder).execute()
				.actionGet();
		return putMappingResponse.isAcknowledged();
	}

}
