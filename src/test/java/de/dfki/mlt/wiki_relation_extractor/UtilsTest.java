/**
 *
 */
package de.dfki.mlt.wiki_relation_extractor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

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

}
