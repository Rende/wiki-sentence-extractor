/**
 *
 */
package de.dfki.mlt.ws2es;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;

import de.dfki.mlt.ws2es.ArticleFilter;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ArticleFilterTest {
	private ArticleFilter filterEN;
	private ArticleFilter filterDE;

	public ArticleFilterTest() {
		filterEN = new ArticleFilter("en");
		filterDE = new ArticleFilter("de");
	}

	@Test
	public void test() throws IOException {

		assertThat(filterEN.removeContentBetweenMatchingBracket("[[xxx]]", "[[", '[', ']')).isEqualTo("");
		assertThat(filterEN.removeContentBetweenMatchingBracket("abc[[xxx]]", "[[", '[', ']')).isEqualTo("abc");
		assertThat(filterEN.removeContentBetweenMatchingBracket("[[xxx]]123", "[[", '[', ']')).isEqualTo("123");
		assertThat(filterEN.removeContentBetweenMatchingBracket("a[[x[[x]]x]]1", "[[", '[', ']')).isEqualTo("a1");
		assertThat(filterEN.removeContentBetweenMatchingBracket("[[abc[[File:x[[x]]x]]123]]", "[[File:", '[', ']'))
				.isEqualTo("[[abc123]]");

	}

	@Test
	public void testRemoveCurlyParentheses() {
		String testText = "{a {{Redirect2|Anarchist|Anarchists|the fictional character|Anarchist \n"
				+ "(comics)|other uses|Anarchists (disambiguation)}}" + "{{pp-move-indef}}"
				+ "{{Use British English{{Basic forms \n" + "of government}}|date=January 2014}}"
				+ "{{Anarchism sidebar}} r}";
		String expectedResult = "{a  r}";
		String actualResult = filterEN.removeContentBetweenMatchingBracket(testText, "{{", '{', '}');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveNestedParentheses() {
		String testText = "a (Redirect2|Anarchist|Anarchists|the fictional character|Anarchist \n"
				+ "(comics)|other uses|Anarchists (disambiguation))" + "(pp-move-indef)"
				+ "(Use British English(Basic forms \n" + "of government)|date=January 2014)" + "(Anarchism sidebar) r";
		String expectedResult = "a  r";
		String actualResult = filterEN.removeContentBetweenMatchingBracket(testText, "(", '(', ')');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveFileExtensions() {
		String extension = "[[File:";
		String testText = "ABC[[File:Levellers declaration and standard.gif|thumb|upright|Woodcut "
				+ "from a [[Diggers]] document by [[William Everard (Digger)\n"
				+ "[[zdssfdjghgjh]]|William Everard]]]] The earliest";
		String expectedResult = "ABC The earliest";
		String actualResult = filterEN.removeContentBetweenMatchingBracket(testText, extension, '[', ']');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testRemoveCategoryExtensions() {
		String extension = "[[Category:";
		String testText = "ABC* [[Category:Anarchism by country|Anarchism by country]] The earliest";
		String expectedResult = "ABC*  The earliest";
		String actualResult = filterEN.removeContentBetweenMatchingBracket(testText, extension, '[', ']');
		assertThat(actualResult).isEqualTo(expectedResult);
	}

	@Test
	public void testCleanTextRemoveComment() {
		String test = "<!--Please do not delete the language templates \n \n -->";
		String expected = "";
		String actual = filterEN.removeTags(test);
		assertThat(actual).isEqualTo(expected);

	}

	@Test
	public void testCleanTextRemoveGarbage() {
		String test = "{ | | } Abc";
		String expected = " Abc";
		String actual = filterEN.removeTags(test);
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

		String actual = filterEN.removeTags(test);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testTokenizer() {
		String test = " In [[ organic chemistry ]] ,"
				+ " ''' amines ''' are <<organic compound | compounds>>  and  {functional group} s"
				+ " that (contain) a [[ base | basic ]] [[ nitrogen ]] [[ atom ]] with a [[ lone pair ]] .";
		String expected = "in [[ organic chemistry ]] , ''' amine ''' be << organic compound | compound >> "
				+ "and { functional group } s that ( contain ) a [[ base | basic ]] [[ nitrogen ]] [[ atom ]] with a [[ lone pair ]] .";
		String actual = filterEN.lemmatizeText(test);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testTokenizerLowerCase() {
		String test = "''' Saint-Esteben ''' was a [[ commune of France | commune ]] in "
				+ "the [[ pyrénées-atlantique ]] [[ departments of France | department ]] "
				+ "in south-western [[ France ]] .";

		String expected = "''' saint-esteben ''' be a [[ commune of france | commune ]] in"
				+ " the [[ pyrénées-atlantique ]] [[ department of france | department ]] "
				+ "in south-western [[ france ]] .";
		String actual = filterEN.lemmatizeText(test);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testTokenizerAndLemmatizer() {
		String sentence = "''' St. George 's Bay ''' [ Geographical Names of Canada - St. George 's Bay ] "
				+ "- informally referred to as ''' Bay St. George ''' due to its French translation "
				+ "''' Baie St-George ''' - is a large [[ Canada | Canadian ]] bay in the province of [[ Newfoundland and Labrador ]] .";
		String expected = "''' st. george ' s bay ''' [ geographical name of canada - st. george ' s bay ] "
				+ "- informally refer to as ''' bay st. george ''' due to its french translation "
				+ "''' baie st-george ''' - be a large [[ canada | canadian ]] bay in the province of [[ newfoundland and labrador ]] .";
		String actual = filterEN.lemmatizeText(sentence);
		assertThat(actual).isEqualTo(expected);

	}

	@Test
	public void testLemmatizer() {
		assertThat(filterEN.lemmatizeText("[[")).isEqualTo("[[");
		assertThat(filterEN.lemmatizeText("]]")).isEqualTo("]]");
		assertThat(filterEN.lemmatizeText("[")).isEqualTo("[");
		assertThat(filterEN.lemmatizeText("]")).isEqualTo("]");
		assertThat(filterEN.lemmatizeText("(")).isEqualTo("(");
		assertThat(filterEN.lemmatizeText(")")).isEqualTo(")");
		assertThat(filterEN.lemmatizeText("{")).isEqualTo("{");
		assertThat(filterEN.lemmatizeText("}")).isEqualTo("}");
		assertThat(filterEN.lemmatizeText("'s")).isEqualTo("' s");
	}

	@Test
	public void testFixSubjectAnnotation() {
		String test = "'''abc'''xyz'''";
		String expected = "''' abc ''' xyz '''";
		String actual = filterEN.fixSubjectAnnotation(test);
		assertThat(actual).isEqualTo(expected);

		String sentence = "The ''' Sheshan Basilica ''' , officially "
				+ "the ''' National Shrine and Minor Basilica of Our Lady of Sheshan ''' "
				+ "and also known as ''' Basilica of Mary , Help of Christians'''is a prominent "
				+ "[[ Roman Catholic ]] church in [[ Shanghai ]] , China . ";
		String expectedSentence = "The ''' Sheshan Basilica ''' , officially "
				+ "the ''' National Shrine and Minor Basilica of Our Lady of Sheshan ''' "
				+ "and also known as ''' Basilica of Mary , Help of Christians ''' is a prominent "
				+ "[[ Roman Catholic ]] church in [[ Shanghai ]] , China .";
		String actualSentence = filterEN.fixSubjectAnnotation(sentence);
		assertThat(actualSentence).isEqualTo(expectedSentence);
	}

	@Test
	public void testGetSubjectName() {
		String sentence = "''''' Can \"Live\" Music ''''' is a [[ double album | double ]] "
				+ "[[ live album ]] by the band [[ Can | Can ]] , released in 1999 and "
				+ "recorded in the [[ UK ]] and [[ West Germany ]] between 1972 and 1977 .";
		String expected = "Can \"Live\" Music";
		String subjectFixed = filterEN.fixSubjectAnnotation(sentence);
		System.out.println(subjectFixed);
		String actual = filterEN.getSubjectName(subjectFixed);
		assertThat(actual).isEqualTo(expected);

		String text = "The following is an overview of the ''' demographics of [[ Myanmar ]] '''"
				+ " , including statistics such as population , [[ Ethnic group | ethnicity ]]"
				+ " , language , education level and religious affiliation .";
		String expectedSubj = "demographics of Myanmar";
		String actualSubj = filterEN.getSubjectName(text);
		assertThat(actualSubj).isEqualTo(expectedSubj);
	}

	@Test
	public void testGermanText() {
		String text = "Wie kam es zur [[ Katastrophe von Genua ]] mit Dutzenden Toten? "
				+ "In Italien tobt eine ''' Debatte über Schuldige ''' . "
				+ "Nun gibt es erste Hinweise, was das Unglück ausgelöst haben könnte.";
		String actual = filterDE.lemmatizeText(text);
		String expected = "wie kommen es zu [[ katastrophe von genua ]] mit dutzend tote ? "
				+ "in italien toben ein ''' debatte über schuldige ''' . "
				+ "nun geben es erster hinweis , was der unglück auslösen haben können .";
		assertThat(actual).isEqualTo(expected);
	}
}
