/**
 *
 */
package de.dfki.mlt.wiki_relation_extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import de.dfki.mlt.wre.ArticleFilter;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ArticleFilterTest {

	public ArticleFilterTest() {
	}

	@Test
	public void test() throws IOException {

		assertThat(
				ArticleFilter.removeContentBetweenMatchingBracket("[[xxx]]",
						"[[", '[', ']')).isEqualTo("");
		assertThat(
				ArticleFilter.removeContentBetweenMatchingBracket("abc[[xxx]]",
						"[[", '[', ']')).isEqualTo("abc");
		assertThat(
				ArticleFilter.removeContentBetweenMatchingBracket("[[xxx]]123",
						"[[", '[', ']')).isEqualTo("123");
		assertThat(
				ArticleFilter.removeContentBetweenMatchingBracket(
						"a[[x[[x]]x]]1", "[[", '[', ']')).isEqualTo("a1");

		assertThat(
				ArticleFilter.removeContentBetweenMatchingBracket(
						"[[abc[[File:x[[x]]x]]123]]", "[[File:", '[', ']'))
				.isEqualTo("[[abc123]]");

		String testInput = new String(Files.readAllBytes(Paths
				.get("wiki-test.txt")), StandardCharsets.UTF_8);

		testInput = ArticleFilter.removeContentBetweenMatchingBracket(
				testInput, "{{", '{', '}');
		// System.out.println(testInput);
		testInput = ArticleFilter.removeContentBetweenMatchingBracket(
				testInput, "(", '(', ')');
		testInput = ArticleFilter.removeContentBetweenMatchingBracket(
				testInput, "[[File:", '[', ']');
		testInput = ArticleFilter.removeContentBetweenMatchingBracket(
				testInput, "[[Image:", '[', ']');
		testInput = ArticleFilter.removeContentBetweenMatchingBracket(
				testInput, "[[Category:", '[', ']');

		System.out.println(testInput);

	}

	@Test
	public void testRemoveCurlyParentheses() {
		String testText = "{a {{Redirect2|Anarchist|Anarchists|the fictional character|Anarchist \n"
				+ "(comics)|other uses|Anarchists (disambiguation)}}"
				+ "{{pp-move-indef}}"
				+ "{{Use British English{{Basic forms \n"
				+ "of government}}|date=January 2014}}"
				+ "{{Anarchism sidebar}} r}";
		String expectedResult = "{a  r}";
		String actualResult = ArticleFilter
				.removeContentBetweenMatchingBracket(testText, "{{", '{', '}');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveNestedParentheses() {
		String testText = "a (Redirect2|Anarchist|Anarchists|the fictional character|Anarchist \n"
				+ "(comics)|other uses|Anarchists (disambiguation))"
				+ "(pp-move-indef)"
				+ "(Use British English(Basic forms \n"
				+ "of government)|date=January 2014)" + "(Anarchism sidebar) r";
		String expectedResult = "a  r";
		String actualResult = ArticleFilter
				.removeContentBetweenMatchingBracket(testText, "(", '(', ')');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveFileExtensions() {
		String extension = "[[File:";
		String testText = "ABC[[File:Levellers declaration and standard.gif|thumb|upright|Woodcut "
				+ "from a [[Diggers]] document by [[William Everard (Digger)\n"
				+ "[[zdssfdjghgjh]]|William Everard]]]] The earliest";
		String expectedResult = "ABC The earliest";
		String actualResult = ArticleFilter
				.removeContentBetweenMatchingBracket(testText, extension, '[',
						']');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveCategoryExtensions() {
		String extension = "[[Category:";
		String testText = "ABC* [[Category:Anarchism by country|Anarchism by country]] The earliest";
		String expectedResult = "ABC*  The earliest";
		String actualResult = ArticleFilter
				.removeContentBetweenMatchingBracket(testText, extension, '[',
						']');
		assertThat(actualResult).isEqualTo(expectedResult);
	}
}
