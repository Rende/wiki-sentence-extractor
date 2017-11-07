/**
 *
 */
package de.dfki.mlt.wiki_relation_extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import de.dfki.mlt.wre.WikiRelationExtractionApp;
import de.dfki.mlt.wre.Utils;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class UtilsTest {

	@Test
	public void testFromStringToWikilabel() {
		String testImage = " Moon landing | landed ";
		String expected = "Moon_landing";
		String actual = Utils.fromStringToWikilabel(testImage);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testCheckingAvailableProperties() {

		List<String> propList = Arrays.asList("P175", "P106", "P31");
		Set<String> propertySet = new HashSet<String>(propList);
		WikiRelationExtractionApp.esService.writePropertiesToFile(propertySet);
	}

}
