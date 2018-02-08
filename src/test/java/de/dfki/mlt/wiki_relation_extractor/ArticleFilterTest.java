/**
 *
 */
package de.dfki.mlt.wiki_relation_extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;

import de.dfki.mlt.wre.ArticleFilter;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ArticleFilterTest {
	private ArticleFilter filter = new ArticleFilter();

	public ArticleFilterTest() {

	}

	@Test
	public void test() throws IOException {

		assertThat(
				filter.removeContentBetweenMatchingBracket("[[xxx]]", "[[",
						'[', ']')).isEqualTo("");
		assertThat(
				filter.removeContentBetweenMatchingBracket("abc[[xxx]]", "[[",
						'[', ']')).isEqualTo("abc");
		assertThat(
				filter.removeContentBetweenMatchingBracket("[[xxx]]123", "[[",
						'[', ']')).isEqualTo("123");
		assertThat(
				filter.removeContentBetweenMatchingBracket("a[[x[[x]]x]]1",
						"[[", '[', ']')).isEqualTo("a1");

		assertThat(
				filter.removeContentBetweenMatchingBracket(
						"[[abc[[File:x[[x]]x]]123]]", "[[File:", '[', ']'))
				.isEqualTo("[[abc123]]");

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
		String actualResult = filter.removeContentBetweenMatchingBracket(
				testText, "{{", '{', '}');
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
		String actualResult = filter.removeContentBetweenMatchingBracket(
				testText, "(", '(', ')');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveFileExtensions() {
		String extension = "[[File:";
		String testText = "ABC[[File:Levellers declaration and standard.gif|thumb|upright|Woodcut "
				+ "from a [[Diggers]] document by [[William Everard (Digger)\n"
				+ "[[zdssfdjghgjh]]|William Everard]]]] The earliest";
		String expectedResult = "ABC The earliest";
		String actualResult = filter.removeContentBetweenMatchingBracket(
				testText, extension, '[', ']');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveCategoryExtensions() {
		String extension = "[[Category:";
		String testText = "ABC* [[Category:Anarchism by country|Anarchism by country]] The earliest";
		String expectedResult = "ABC*  The earliest";
		String actualResult = filter.removeContentBetweenMatchingBracket(
				testText, extension, '[', ']');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testCleanTextRemoveComment() {
		String test = "<!--Please do not delete the language templates \n \n -->";
		String expected = "";
		String actual = filter.cleanUpText(test);
		assertThat(actual).isEqualTo(expected);

	}

	@Test
	public void testCleanTextRemoveGarbage() {
		String test = "{ | | } Abc";
		String expected = " Abc";
		String actual = filter.cleanUpText(test);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testCleanTextRemoveParantheses() {
		String test = "{ | class = \" wikitable \" style = \" margin:auto 1em auto 1em ; "
				+ "float:right ; text-align:center ; \" | - ! Primary amine ! ! "
				+ "Secondary amine ! ! Tertiary amine | - | | | | } In [[ organic chemistry ]] ,"
				+ " ''' amines ''' are [[ organic compound | compounds ]] and [[ functional group ]] s"
				+ " that contain a [[ base | basic ]] [[ nitrogen ]] [[ atom ]] with a [[ lone pair ]] .";
		String expected = " In [[ organic chemistry ]] ,"
				+ " ''' amines ''' are [[ organic compound | compounds ]] and [[ functional group ]] s"
				+ " that contain a [[ base | basic ]] [[ nitrogen ]] [[ atom ]] with a [[ lone pair ]] .";

		String actual = filter.cleanUpText(test);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testTokenizer() {
		String test = " In [[ organic chemistry ]] ,"
				+ " ''' amines ''' are <<organic compound | compounds>>  and  {functional group} s"
				+ " that (contain) a [[ base | basic ]] [[ nitrogen ]] [[ atom ]] with a [[ lone pair ]] .";
		String expected = "in [[ organic chemistry ]] , ''' amine ''' be << organic compound | compound >> "
				+ "and { functional group } s that ( contain ) a [[ base | basic ]] [[ nitrogen ]] [[ atom ]] with a [[ lone pair ]] .";
		String actual = filter.tokenizer(test);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testLemmatizer() {
		assertThat("[[").isEqualTo(filter.lemmatize("[["));
		assertThat("]]").isEqualTo(filter.lemmatize("]]"));
		assertThat("(").isEqualTo(filter.lemmatize("("));
		assertThat(")").isEqualTo(filter.lemmatize(")"));
		assertThat("{").isEqualTo(filter.lemmatize("{"));
		assertThat("}").isEqualTo(filter.lemmatize("}"));
	}
}
