/**
 *
 */
package de.dfki.mlt.wre;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

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
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import de.dfki.mlt.wre.preferences.Config;

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
	private Client getClient() throws UnknownHostException {
		if (client == null) {
			Settings settings = Settings
					.builder()
					.put(Config.CLUSTER_NAME,
							Config.getInstance().getString(Config.CLUSTER_NAME))
					.build();
			client = new PreBuiltTransportClient(settings)
					.addTransportAddress(new TransportAddress(InetAddress
							.getByName("134.96.187.233"), 9300));
		}
		return client;
	}

	public void stopConnection() throws UnknownHostException {
		getBulkProcessor().close();
		getClient().close();
	}

	public String getItemId(String wikipediaTitle) {
		SearchResponse response = searchItemByWikipediaTitle(wikipediaTitle);
		String itemId = "";
		if (isResponseValid(response)) {
			for (SearchHit hit : response.getHits()) {
				itemId = hit.getId();
				return itemId;
			}
		}
		return itemId;
	}

	public boolean isResponseValid(SearchResponse response) {
		return response != null && response.getHits().totalHits > 0;
	}

	private SearchResponse searchItemByWikipediaTitle(String wikipediaTitle) {
		QueryBuilder query = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("type", "item"))
				.must(QueryBuilders.termQuery("wiki-title", wikipediaTitle));

		try {
			@SuppressWarnings("deprecation")
			SearchRequestBuilder requestBuilder = getClient()
					.prepareSearch(
							Config.getInstance().getString(
									Config.WIKIDATA_INDEX))
					.setTypes(
							Config.getInstance().getString(
									Config.WIKIDATA_ENTITY))
					.fields("wiki-title").setQuery(query).setSize(1);
			SearchResponse response = requestBuilder.execute().actionGet();
			return response;

		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	private BulkProcessor getBulkProcessor() throws UnknownHostException {
		if (bulkProcessor == null) {
			bulkProcessor = BulkProcessor
					.builder(getClient(), new BulkProcessor.Listener() {
						@Override
						public void beforeBulk(long executionId,
								BulkRequest request) {
							// WikiRelationExtractionApp.LOG
							// .info("Number of request processed: "
							// + request.numberOfActions());
						}

						@Override
						public void afterBulk(long executionId,
								BulkRequest request, BulkResponse response) {
							if (response.hasFailures()) {
								SentenceExtractionApp.LOG
										.error("Elasticsearch Service getBulkProcessor() "
												+ response
														.buildFailureMessage());
							}
						}

						@Override
						public void afterBulk(long executionId,
								BulkRequest request, Throwable failure) {
							SentenceExtractionApp.LOG
									.error("Elasticsearch Service getBulkProcessor() "
											+ failure.getMessage());

						}
					})
					.setBulkActions(10000)
					.setBulkSize(new ByteSizeValue(1, ByteSizeUnit.GB))
					.setFlushInterval(TimeValue.timeValueSeconds(5))
					.setConcurrentRequests(1)
					.setBackoffPolicy(
							BackoffPolicy.exponentialBackoff(
									TimeValue.timeValueMillis(100), 3)).build();
		}

		return bulkProcessor;
	}

	public void insertSentence(String pageId, String sentence,
			String subjectId, String wikipediaTitle, String tokenizedSentence)
			throws IOException {
		XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
				.field("page-id", Long.parseLong(pageId))
				.field("title", wikipediaTitle).field("subject-id", subjectId)
				.field("sentence", sentence)
				.field("tok-sentence", tokenizedSentence).endObject();
		String json = builder.string();
		// System.out.println(json);
		IndexRequest indexRequest = Requests
				.indexRequest()
				.index(Config.getInstance().getString(
						Config.WIKIPEDIA_SENTENCE_INDEX))
				.type(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE))
				.source(json);
		getBulkProcessor().add(indexRequest);

	}

	public boolean checkAndCreateIndex(String indexName) throws IOException,
			InterruptedException {
		boolean result = false;
		IndicesAdminClient indicesAdminClient = getClient().admin().indices();
		final IndicesExistsResponse indexExistReponse = indicesAdminClient
				.prepareExists(indexName).execute().actionGet();
		if (indexExistReponse.isExists()) {
			deleteIndex(indicesAdminClient, indexName);
		}
		if (createIndex(indicesAdminClient, indexName)) {
			result = putMappingForSentence(indicesAdminClient);
		}
		return result;
	}

	private void deleteIndex(IndicesAdminClient indicesAdminClient,
			String indexName) {
		final DeleteIndexRequestBuilder delIdx = indicesAdminClient
				.prepareDelete(indexName);
		delIdx.execute().actionGet();
	}

	private boolean createIndex(IndicesAdminClient indicesAdminClient,
			String indexName) {
		final CreateIndexRequestBuilder createIndexRequestBuilder = indicesAdminClient
				.prepareCreate(indexName).setSettings(
						Settings.builder()
								.put(Config.NUMBER_OF_SHARDS,
										Config.getInstance().getInt(
												Config.NUMBER_OF_SHARDS))
								.put(Config.NUMBER_OF_REPLICAS,
										Config.getInstance().getInt(
												Config.NUMBER_OF_REPLICAS)));
		CreateIndexResponse createIndexResponse = null;
		createIndexResponse = createIndexRequestBuilder.execute().actionGet();
		return createIndexResponse != null
				&& createIndexResponse.isAcknowledged();
	}

	public boolean putMappingForSentence(IndicesAdminClient indicesAdminClient)
			throws IOException {
		XContentBuilder mappingBuilder = XContentFactory
				.jsonBuilder()
				.startObject()
				.startObject(
						Config.getInstance().getString(
								Config.WIKIPEDIA_SENTENCE))
				.startObject("properties").startObject("page-id")
				.field("type", "long").field("index", "true").endObject()
				.startObject("title").field("type", "keyword")
				.field("index", "true").endObject().startObject("subject-id")
				.field("type", "keyword").field("index", "true").endObject()
				.startObject("sentence").field("type", "text").endObject()
				.startObject("tok-sentence").field("type", "text").endObject()
				.endObject() // properties
				.endObject()// documentType
				.endObject();

		SentenceExtractionApp.LOG.debug("Mapping for wikipedia sentence: "
				+ mappingBuilder.string());
		PutMappingResponse putMappingResponse = indicesAdminClient
				.preparePutMapping(
						Config.getInstance().getString(
								Config.WIKIPEDIA_SENTENCE_INDEX))
				.setType(
						Config.getInstance().getString(
								Config.WIKIPEDIA_SENTENCE))
				.setSource(mappingBuilder).execute().actionGet();
		return putMappingResponse.isAcknowledged();
	}

}
