/**
 *
 */
package de.dfki.mlt.wiki_relation_extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.dfki.mlt.wre.RelationExtractor;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class RelationExtractorTest {
	private RelationExtractor extractor = new RelationExtractor();

	@Test
	public void testRelationExtractor() {

	}

	@Test
	public void testGetObjectList() {
		String testSentence = "'' ' Apollo 11 '' ' was the [[ spaceflight ]] "
				+ "that [[ Moon landing | landed ]] the first two humans on the [[ Moon ]] .";
		List<String> actualObjectList = extractor.getObjectList(testSentence);
		List<String> expectedObjectList = new ArrayList<String>();
		expectedObjectList.add("Spaceflight");
		expectedObjectList.add("Moon_landing");
		expectedObjectList.add("Moon");
		assertThat(actualObjectList).containsAll(expectedObjectList);
	}

	@Test
	public void testRemoveMarkup() {
		String testSentence = "An '' ' astronaut '' ' or '' ' cosmonaut '' ' is a person "
				+ "trained by a [[ List of human spaceflight programs | human spaceflight program ]] "
				+ "to command , pilot , or serve as a crew member of a [[ spacecraft ]] .";
		String expectedSentence = "An astronaut or cosmonaut is a person "
				+ "trained by a human spaceflight program "
				+ "to command , pilot , or serve as a crew member of a spacecraft .";
		String actualSentence = extractor.removeMarkup(testSentence);
		assertThat(actualSentence).isEqualTo(expectedSentence);
	}
}
