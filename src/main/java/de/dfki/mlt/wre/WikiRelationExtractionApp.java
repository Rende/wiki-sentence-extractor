package de.dfki.mlt.wre;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.WikiXMLParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import de.dfki.mlt.wre.preferences.Config;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class WikiRelationExtractionApp {

	public static ElasticsearchService esService = new ElasticsearchService();
	public static final Logger LOG = LoggerFactory
			.getLogger(WikiRelationExtractionApp.class);

	public static void main(String[] args) throws IOException, SAXException {
		long start = System.currentTimeMillis();
		LOG.debug("Wikipedia relation extraction started.");
		// readAndCompareProperties();
		// esService.checkProperties();
		// esService.findNonItemizedProperties();
		boolean isIndexCreated = false;
		try {
			isIndexCreated = esService.checkAndCreateIndex("wikipedia-index");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (isIndexCreated) {
			String dumpfile = Config.getInstance().getString(
					Config.DIRECTORY_PATH);
			IArticleFilter handler = new ArticleFilter();
			WikiXMLParser wxp = new WikiXMLParser(dumpfile, handler);
			wxp.parse();

		}
		long elapsedTimeMillis = System.currentTimeMillis() - start;
		float elapsedTimeHour = elapsedTimeMillis / (60 * 60 * 1000F);
		LOG.debug("Time spent in hours: " + elapsedTimeHour);
		// crawler();
	}

	public static void crawler() {
		Document doc = new Document("");
		try {
			doc = Jsoup
					.connect(
							"https://www.wikidata.org/wiki/Wikidata:Database_reports/List_of_properties/all")
					.maxBodySize(1024 * 1024 * 2).get();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// select the first table
		Element table = doc.select("table").get(0);
		Elements rows = table.select("tr");
		// first row is the col names so skip it
		for (int i = 1; i < rows.size(); i++) {
			Element row = rows.get(i);
			Elements cols = row.select("td");
			String printOut = "";
			try {
				printOut = cols.get(0).text() + " " + cols.get(1).text() + "\n";
			} catch (IndexOutOfBoundsException e) {

			}
			try {
				Files.write(Paths.get("wikidata-website-properties.txt"),
						printOut.getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void readAndCompareProperties() throws IOException {
		StringBuilder builder = new StringBuilder();
		Set<String> unknownPropertySet = readPropertiesFromFile("our-properties-in-ES.txt");
		HashMap<String, String> wikidataWebsitePropertySet = getWikidataPropertyMap("all-website-properties.txt");
		for (String unknownProp : unknownPropertySet) {
			if (wikidataWebsitePropertySet.containsKey(unknownProp)) {
				builder.append(unknownProp + " "
						+ wikidataWebsitePropertySet.get(unknownProp) + "\n");
			} else {
				builder.append(unknownProp + " not in wikidata\n");
			}
		}
		try {
			Files.write(Paths.get("our-properties.txt"), builder.toString()
					.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static Set<String> readPropertiesFromFile(String fileName) {
		Set<String> propertySet = new HashSet<String>();

		try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
			propertySet = stream.collect(Collectors.toSet());

		} catch (IOException e) {
			e.printStackTrace();
		}

		return propertySet;
	}

	public static HashMap<String, String> getWikidataPropertyMap(String fileName)
			throws IOException {
		HashMap<String, String> propertyMap = new HashMap<String, String>();
		File file = new File(fileName);
		FileReader fileReader = new FileReader(file);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			String words[] = line.split(" ", 2);
			propertyMap.put(words[0], words[1]);
		}
		bufferedReader.close();
		fileReader.close();

		return propertyMap;
	}

	public static void checkProperties() {
		String fileName = "all-website-properties.txt";

		esService.writePropertiesToFile(readPropertiesFromFile(fileName));
	}
}
