package de.dfki.mlt.wre;

import info.bliki.wiki.dump.WikiXMLParser;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import de.dfki.mlt.wre.preferences.Config;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class SentenceExtractionApp {

	public static ElasticsearchService esService = new ElasticsearchService();
	public static final Logger LOG = LoggerFactory
			.getLogger(SentenceExtractionApp.class);

	public static void main(String[] args) {
		System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
		long start = System.currentTimeMillis();
		boolean isIndexCreated = false;

		LOG.info("Wikipedia first sentence extraction started.");

		try {
			isIndexCreated = esService.checkAndCreateIndex(Config.getInstance()
					.getString(Config.WIKIPEDIA_INDEX));
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
		if (isIndexCreated) {
			String dumpfile = Config.getInstance().getString(
					Config.DIRECTORY_PATH);
			ArticleFilter handler = new ArticleFilter();
			WikiXMLParser wxp;
			try {
				wxp = new WikiXMLParser(dumpfile, handler);
				wxp.parse();
				LOG.info("Overall the number of wikipedia pages: " + handler.count);
				LOG.info(handler.noEntryCount
						+ " pages has no entry in wikidata");
				LOG.info(handler.invalidCount + " pages are invalid");
			} catch (SAXException | IOException e) {
				e.printStackTrace();
			}
		}

		long elapsedTimeMillis = System.currentTimeMillis() - start;
		float elapsedTimeHour = elapsedTimeMillis / (60 * 60 * 1000F);
		LOG.info("Time spent in hours: " + elapsedTimeHour);
	}
}
