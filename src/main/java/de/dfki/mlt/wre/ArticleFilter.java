/**
 *
 */
package de.dfki.mlt.wre;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.xml.sax.SAXException;

import de.dfki.lt.tools.tokenizer.annotate.AnnotatedString;
import de.dfki.lt.tools.tokenizer.output.Outputter;
import de.dfki.lt.tools.tokenizer.output.Paragraph;
import de.dfki.lt.tools.tokenizer.output.TextUnit;
import de.dfki.lt.tools.tokenizer.output.Token;
import de.dfki.mlt.wre.preferences.Config;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ArticleFilter implements IArticleFilter {

	private static final String PAGE_REDIRECT = "#REDIRECT ";

	private List<String> extensionList = new ArrayList<String>();
	private RelationExtractor relationExtractor = new RelationExtractor();

	public ArticleFilter() {
		String[] extensions = Config.getInstance().getStringArray(
				Config.WIKIPEDIA_EXTENSION);
		extensionList = Arrays.asList(extensions);
	}

	public void process(WikiArticle page, Siteinfo siteinfo)
			throws SAXException {
		if (page != null && page.getText() != null
				&& !page.getText().startsWith(PAGE_REDIRECT)) {
			String wikipediaTitle = Utils
					.fromStringToWikilabel(page.getTitle());
			String subjectId = WikiRelationExtractionApp.esService
					.getItemId(wikipediaTitle);
			if (!subjectId.equals("") && !wikipediaTitle.startsWith("List_of")) {
				String text = page.getText();
				text = removeContentBetweenMatchingBracket(text, "{{", '{', '}');
				text = removeContentBetweenMatchingBracket(text, "(", '(', ')');
				for (String extension : extensionList)
					text = removeContentBetweenMatchingBracket(text, extension,
							'[', ']');
				text = cleanUpText(text);
				String firstSentence = getFirstSentence(text.trim());
				firstSentence = removeGapsBetweenBrackets(firstSentence, '[',
						']');
				// System.out.println(count + " " + firstSentence);
				relationExtractor.relationProcess(firstSentence, subjectId,
						wikipediaTitle);

			}
		}
	}

	private String removeGapsBetweenBrackets(String text, char open, char close) {
		text = text.replaceAll("\\" + open + " " + "\\" + open, "\\" + open
				+ "\\" + open);
		text = text.replaceAll("\\" + close + " " + "\\" + close, "\\" + close
				+ "\\" + close);
		return text;
	}

	private String cleanUpText(String text) {
		String cleanText = text
				// to remove xml comments
				.replaceAll("(?m)<!--[\\s\\S]*?-->", "")
				.replaceAll("\\<.*?\\>", "").replaceAll("==.*?==", "");
		// .replaceAll("(?s)<ref.+?</ref>", "").replaceAll("<.+?>", "");
		return cleanText;
	}

	private String getFirstSentence(String inputText) {
		StringBuilder builder = new StringBuilder();
		AnnotatedString annString = JtokApi.getInstance().tokenize(inputText,
				"en");
		List<Paragraph> paragraphs = Outputter.createParagraphs(annString);
		for (Paragraph p : paragraphs) {
			if (p.getStartIndex() != p.getEndIndex()) {
				for (TextUnit textUnit : p.getTextUnits()) {
					for (Token token : textUnit.getTokens()) {
						builder.append(token.getImage() + " ");
						if (token.getImage().equals(".")) {
							return builder.toString();
						}
					}
				}
			}
		}
		return builder.toString();

	}

	public static String removeContentBetweenMatchingBracket(String input,
			String pattern, char open, char close) {
		String result = "";
		int openCloseCount = 0;
		int start = input.indexOf(pattern);

		if (start == -1) {
			return input;
		}
		int end = -1;
		for (int i = start; i < input.length(); i++) {
			char currentChar = input.charAt(i);
			if (currentChar == open) {
				openCloseCount++;
			} else if (currentChar == close) {
				openCloseCount--;
			}
			if (openCloseCount == 0) {
				end = i + 1;
				break;
			}
		}
		if (end == -1) {
			result = input.substring(0, start);
		} else {
			result = input.substring(0, start)
					+ removeContentBetweenMatchingBracket(
							input.substring(end, input.length()), pattern,
							open, close);
		}
		return result;
	}
}
