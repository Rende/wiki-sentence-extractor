package de.dfki.mlt.wse;

import info.bliki.wiki.dump.WikiXMLParser;

import java.io.IOException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import de.dfki.mlt.wse.preferences.Config;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class App {

	public static ElasticsearchService esService = new ElasticsearchService();
	public static final Logger LOG = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws UnknownHostException, InterruptedException {
		System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
		long start = System.currentTimeMillis();
		boolean isIndexCreated = false;

		LOG.info("Wikipedia first sentence extraction started.");

		try {
			isIndexCreated = esService
					.checkAndCreateIndex(Config.getInstance().getString(Config.WIKIPEDIA_SENTENCE_INDEX));
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
		if (isIndexCreated) {
			String dumpfile = Config.getInstance().getString(Config.DIRECTORY_PATH);
			String lang = Config.getInstance().getString(Config.LANG);
			ArticleFilter handler = new ArticleFilter(lang);
			WikiXMLParser wxp;
			try {
				wxp = new WikiXMLParser(dumpfile, handler);
				wxp.parse();
				logCounts(handler);
			} catch (SAXException | IOException e) {
				e.printStackTrace();
			}
		}

		long elapsedTimeMillis = System.currentTimeMillis() - start;
		float elapsedTimeHour = elapsedTimeMillis / (60 * 60 * 1000F);
		LOG.info("Time spent in hours: " + elapsedTimeHour);
		esService.stopConnection();
	}

	private static void logCounts(ArticleFilter handler) {
		LOG.info("Overall the number of wikipedia pages: " + handler.count);
		LOG.info(handler.invalidCount + " pages are invalid");
	}
}
