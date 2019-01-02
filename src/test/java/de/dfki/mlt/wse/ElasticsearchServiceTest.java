/**
 * 
 */
package de.dfki.mlt.wse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import de.dfki.mlt.wse.ElasticsearchService;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ElasticsearchServiceTest {
	private ElasticsearchService esService = new ElasticsearchService();

	@Test
	public void testGetRelatedItemst() {
		List<String> relatedItems = esService.getRelatedItems("Ada Lovelace", "en");
		assertThat(relatedItems).contains("Q7259");
		List<String> relatedItemList = esService.getRelatedItems("Cajeput tree", "en");
		assertThat(relatedItemList).contains("Q5017979");
	}

	@Test
	public void testGetItem() {
		String itemAdaId = esService.getItemId("Ada_Lovelace", "en");
		assertThat(itemAdaId).isEqualTo("Q7259");
		String itemBrainId = esService.getItemId("Brain", "en");
		assertThat(itemBrainId).isEqualTo("Q1073");
		String longItemId = esService.getItemId("Madison_High_School_(Nebraska)", "en");
		assertThat(longItemId).isEqualTo("Q6728007");
	}

	@Test
	public void testGetRelatedItemsDE() {
		List<String> relatedItems = esService.getRelatedItems("Saarbrücken", "de");
		assertThat(relatedItems).contains("Q1724");

	}

	@Test
	public void testGetItemDE() {
		String itemId = esService.getItemId("Saarbrücken", "de");
		assertThat(itemId).isEqualTo("Q1724");
	}
}
