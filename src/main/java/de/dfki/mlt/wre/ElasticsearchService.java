/**
 *
 */
package de.dfki.mlt.wre;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import com.google.common.collect.ImmutableList;

import de.dfki.mlt.wre.preferences.Config;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ElasticsearchService {
	private Client client;
	private BulkProcessor bulkProcessor;

	public ElasticsearchService() {
		getClient();
	}

	public Client getClient() {
		if (client == null) {
			Map<String, String> userConfig = getUserConfig();
			List<InetSocketAddress> transportAddresses = getTransportAddresses();
			List<TransportAddress> transportNodes;
			transportNodes = new ArrayList<>(transportAddresses.size());
			for (InetSocketAddress address : transportAddresses) {
				transportNodes.add(new InetSocketTransportAddress(address));
			}
			Settings settings = Settings.settingsBuilder().put(userConfig)
					.build();

			TransportClient transportClient = TransportClient.builder()
					.settings(settings).build();
			for (TransportAddress transport : transportNodes) {
				transportClient.addTransportAddress(transport);
			}

			// verify that we actually are connected to a cluster
			ImmutableList<DiscoveryNode> nodes = ImmutableList
					.copyOf(transportClient.connectedNodes());
			if (nodes.isEmpty()) {
				throw new RuntimeException(
						"Client is not connected to any Elasticsearch nodes!");
			}

			client = transportClient;
		}
		return client;
	}

	public static Map<String, String> getUserConfig() {
		Map<String, String> config = new HashMap<>();
		config.put(Config.BULK_FLUSH_MAX_ACTIONS, Config.getInstance()
				.getString(Config.BULK_FLUSH_MAX_ACTIONS));
		config.put(Config.CLUSTER_NAME,
				Config.getInstance().getString(Config.CLUSTER_NAME));

		return config;
	}

	public static List<InetSocketAddress> getTransportAddresses() {
		List<InetSocketAddress> transports = new ArrayList<>();
		try {
			transports.add(new InetSocketAddress(InetAddress.getByName(Config
					.getInstance().getString(Config.HOST)), Config
					.getInstance().getInt(Config.PORT)));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return transports;
	}

	public void stopConnection() {
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

	public boolean checkItemAvailability(String wikipediaTitle) {
		SearchResponse response = searchItemByWikipediaTitle(wikipediaTitle);
		if (isResponseValid(response)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean isResponseValid(SearchResponse response) {
		return response != null && response.getHits().totalHits() > 0;
	}

	private SearchResponse searchItemByWikipediaTitle(String wikipediaTitle) {
		QueryBuilder query = QueryBuilders
				.boolQuery()
				.must(QueryBuilders.termQuery("type", "item"))
				.must(QueryBuilders
						.termQuery("wikipedia_title", wikipediaTitle));

		try {
			SearchRequestBuilder requestBuilder = getClient()
					.prepareSearch(
							Config.getInstance().getString(
									Config.WIKIDATA_INDEX))
					.setTypes(
							Config.getInstance().getString(
									Config.WIKIDATA_ENTITY))
					.addFields("id", "type", "org_label", "label",
							"wikipedia_title").setQuery(query).setSize(1);
			SearchResponse response = requestBuilder.execute().actionGet();
			return response;

		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	private SearchResponse searchPropertyById(String propertyId) {
		QueryBuilder query = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("type", "property"))
				.must(QueryBuilders.termQuery("id", propertyId));

		try {
			SearchRequestBuilder requestBuilder = getClient()
					.prepareSearch(
							Config.getInstance().getString(
									Config.WIKIDATA_INDEX))
					.setTypes(
							Config.getInstance().getString(
									Config.WIKIDATA_ENTITY))
					.addFields("id", "type", "org_label", "label",
							"wikipedia_title").setQuery(query).setSize(1000);
			SearchResponse response = requestBuilder.execute().actionGet();
			return response;

		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean checkPropertyAvailability(String propertyId) {
		SearchResponse response = searchPropertyById(propertyId);
		if (isResponseValid(response)) {
			return true;
		} else {
			return false;
		}
	}

	public Property getProperty(String propertyId) {
		boolean isFirst = false;
		SearchResponse response = searchPropertyById(propertyId);
		Property property = new Property();
		if (isResponseValid(response)) {
			for (SearchHit hit : response.getHits()) {
				if (!isFirst) {
					property.setId(propertyId);
					property.setLabel(hit.field("org_label").getValue()
							.toString());
					isFirst = true;
				}
				property.getAliases().add(
						hit.field("label").getValue().toString());
			}
		}

		return property;
	}

	public List<String> getRelationsOfTwoItems(String entityId, String objectId) {
		SearchResponse response = searchRelationsOfTwoItems(entityId, objectId);
		List<String> propertyIdList = new ArrayList<String>();
		if (isResponseValid(response)) {
			for (SearchHit hit : response.getHits()) {
				propertyIdList.add(hit.field("property_id").getValue()
						.toString());
			}
		}
		return propertyIdList;

	}

	private SearchResponse searchRelationsOfTwoItems(String entityId,
			String objectId) {
		QueryBuilder query = QueryBuilders
				.boolQuery()
				.must(QueryBuilders.termQuery("entity_id", entityId))
				.must(QueryBuilders.termQuery("data_type", "wikibase-entityid"))
				.must(QueryBuilders.termQuery("data_value", objectId));
		// System.out.println(query);
		try {
			SearchRequestBuilder requestBuilder = getClient()
					.prepareSearch(
							Config.getInstance().getString(
									Config.WIKIDATA_INDEX))
					.setTypes(
							Config.getInstance().getString(
									Config.WIKIDATA_CLAIM))
					.addFields("property_id").setQuery(query).setSize(1000);
			SearchResponse response = requestBuilder.execute().actionGet();
			// System.out.println(response);
			return response;

		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	private BulkProcessor getBulkProcessor() {
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
								WikiRelationExtractionApp.LOG
										.error("Elasticsearch Service getBulkProcessor() "
												+ response
														.buildFailureMessage());
							}
						}

						@Override
						public void afterBulk(long executionId,
								BulkRequest request, Throwable failure) {
							WikiRelationExtractionApp.LOG
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

	public void insertRelation(String sentence, String subjectId,
			String wikipediaTitle,
			HashMap<String, HashMap<String, String>> objectRelationMap)
			throws IOException {
		XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
				.field("sentence", sentence).field("subject-id", subjectId)
				.field("subject-label", wikipediaTitle).startArray("objects");
		for (Map.Entry<String, HashMap<String, String>> objectRelation : objectRelationMap
				.entrySet()) {
			builder.startObject().field("object-id", objectRelation.getKey())// object-label
					.startArray("relations");
			for (Map.Entry<String, String> relation : objectRelation.getValue()
					.entrySet()) {
				builder.startObject().field("property-id", relation.getKey())
						.field("surface", relation.getValue()).endObject();
			}
			builder.endArray().endObject();
		}
		builder.endArray().endObject();
		String json = builder.string();
		IndexRequest indexRequest = Requests
				.indexRequest()
				.index(Config.getInstance().getString(Config.WIKIPEDIA_INDEX))
				.type(Config.getInstance().getString(Config.WIKIPEDIA_RELATION))
				.source(json);
		getBulkProcessor().add(indexRequest);

	}

	public void insertSentence(String sentence, String subjectId,
			String wikipediaTitle) throws IOException {
		XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
				.field("title", wikipediaTitle).field("sentence", sentence)
				.endObject();
		String json = builder.string();
		// System.out.println(json);
		IndexRequest indexRequest = Requests
				.indexRequest()
				.index(Config.getInstance().getString(Config.WIKIPEDIA_INDEX))
				.type(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE)).source(json);
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
						Settings.settingsBuilder()
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

	private boolean putMappingForSentence(IndicesAdminClient indicesAdminClient)
			throws IOException {
		XContentBuilder mappingBuilder = XContentFactory
				.jsonBuilder()
				.startObject()
				.startObject(
						Config.getInstance().getString(
								Config.WIKIPEDIA_SENTENCE))
				.startObject("properties").startObject("title")
				.field("type", "string").field("index", "not_analyzed")
				.endObject().startObject("sentence").field("type", "string")
				.endObject().endObject() // properties
				.endObject()// documentType
				.endObject();

		WikiRelationExtractionApp.LOG.debug("Mapping for wikipedia sentence: "
				+ mappingBuilder.string());
		PutMappingResponse putMappingResponse = indicesAdminClient
				.preparePutMapping(
						Config.getInstance().getString(Config.WIKIPEDIA_INDEX))
				.setType(
						Config.getInstance().getString(
								Config.WIKIPEDIA_SENTENCE))
				.setSource(mappingBuilder).execute().actionGet();
		return putMappingResponse.isAcknowledged();
	}

	private boolean putMappingForRelations(IndicesAdminClient indicesAdminClient)
			throws IOException {
		XContentBuilder mappingBuilder = XContentFactory
				.jsonBuilder()
				.startObject()
				.startObject(
						Config.getInstance().getString(
								Config.WIKIPEDIA_RELATION))
				.startObject("properties").startObject("subject-id")
				.field("type", "string").field("index", "not_analyzed")
				.endObject().startObject("subject-label")
				.field("type", "string").field("index", "not_analyzed")
				.endObject().startObject("sentence").field("type", "string")
				.endObject().startObject("objects").field("type", "nested")
				.startObject("properties").startObject("object-id")
				.field("type", "string").field("index", "not_analyzed")
				.endObject().startObject("object-label")
				.field("type", "string").endObject().startObject("relations")
				.field("type", "nested").startObject("properties")
				.startObject("property-id").field("type", "string")
				.field("index", "not_analyzed").endObject()
				.startObject("surface").field("type", "string").endObject()
				.endObject().endObject().endObject().endObject().endObject() // properties
				.endObject()// documentType
				.endObject();

		WikiRelationExtractionApp.LOG.debug("Mapping for wikipedia sentence: "
				+ mappingBuilder.string());
		PutMappingResponse putMappingResponse = indicesAdminClient
				.preparePutMapping(
						Config.getInstance().getString(Config.WIKIPEDIA_INDEX))
				.setType(
						Config.getInstance().getString(
								Config.WIKIPEDIA_SENTENCE))
				.setSource(mappingBuilder).execute().actionGet();
		return putMappingResponse.isAcknowledged();
	}
}
