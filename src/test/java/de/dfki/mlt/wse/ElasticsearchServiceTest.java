/**
 * 
 */
package de.dfki.mlt.wiki_relation_extractor;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import de.dfki.mlt.wre.ElasticsearchService;
/**
 * @author Aydan Rende, DFKI
 *
 */
public class ElasticsearchServiceTest {
	private ElasticsearchService esService = new ElasticsearchService();

	@Test
	public void test() {
		List<String> relatedItems = esService.getRelatedItems("Ada Lovelace", "en");
		assertThat(relatedItems).contains("Q7259","Q30128828","Q55393255","Q3611580","Q58523930","Q21831091","Q58514692");
		assertThat(relatedItems.size()).isEqualTo(7);
	}
	
	@Test
	public void testDE() {
		List<String> relatedItems = esService.getRelatedItems("Saarbrücken", "de");
		assertThat(relatedItems.size()).isEqualTo(301);
		assertThat(relatedItems).contains("Q1724");
		
	}
}
