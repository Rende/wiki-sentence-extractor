/**
 *
 */
package de.dfki.mlt.wre;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import de.dfki.lt.tools.tokenizer.JTok;
import de.dfki.lt.tools.tokenizer.annotate.AnnotatedString;
import de.dfki.lt.tools.tokenizer.output.Outputter;
import de.dfki.lt.tools.tokenizer.output.Paragraph;
import de.dfki.lt.tools.tokenizer.output.TextUnit;
import de.dfki.lt.tools.tokenizer.output.Token;
import de.dfki.mlt.wre.preferences.Config;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class ArticleFilter implements IArticleFilter {

	private List<String> extensionList = new ArrayList<String>();
	private List<String> invalidPageTitles = new ArrayList<String>();
	public int noEntryCount = 0;
	public int invalidCount = 0;
	public int count = 0;
	private JTok jtok;
	protected StanfordCoreNLP pipeline;

	public ArticleFilter() {
		extensionList = Arrays.asList(Config.getInstance().getStringArray(
				Config.WIKIPEDIA_EXTENSION));
		invalidPageTitles = Arrays.asList(Config.getInstance().getStringArray(
				Config.WIKIPEDIA_INVALID_PAGES));
		try {
			jtok = new JTok();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Properties props;
		props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma");
		pipeline = new StanfordCoreNLP(props);
	}

	public void process(WikiArticle page, Siteinfo siteinfo)
			throws SAXException {
		if (isPageValid(page)) {
			logCounts();
			String wikipediaTitle = fromStringToWikilabel(page.getTitle());
			String pageId = page.getId();
			String subjectId = SentenceExtractionApp.esService
					.getItemId(wikipediaTitle);
			if (isSubjectValid(subjectId, wikipediaTitle)) {
				String text = page.getText();
				text = removeContentBetweenMatchingBracket(text, "{{", '{', '}');
				text = removeContentBetweenMatchingBracket(text, "(", '(', ')');
				for (String extension : extensionList)
					text = removeContentBetweenMatchingBracket(text, extension,
							'[', ']');
				text = cleanUpText(text);
				String firstSentence = getFirstSentence(text.trim());
				firstSentence = cleanUpText(firstSentence);
				String tokenizedSentence = tokenizer(firstSentence);
				try {
					SentenceExtractionApp.esService.insertSentence(pageId,
							firstSentence, subjectId, wikipediaTitle,
							tokenizedSentence);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public String tokenizer(String text) {
		AnnotatedString annString = jtok.tokenize(text, "en");
		List<Token> tokenList = Outputter.createTokens(annString);
		StringBuilder builder = new StringBuilder();
		for (Token token : tokenList) {
			builder.append(lemmatize(token.getImage()) + " ");
		}
		return builder.toString().trim();
	}

	// method should be used word-based
	public String lemmatize(String documentText) {
		StringBuilder builder = new StringBuilder();
		Annotation document = new Annotation(documentText);
		this.pipeline.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				String image = token.get(LemmaAnnotation.class);
				image = replaceParantheses(image);
				builder.append(image);
			}
		}
		return builder.toString();
	}

	public String replaceParantheses(String image) {
		return image = image.replaceAll("-lrb-", "\\(")
				.replaceAll("-rrb-", "\\)").replaceAll("-lcb-", "\\{")
				.replaceAll("-rcb-", "\\}").replaceAll("-lsb-", "\\[")
				.replaceAll("-rsb-", "\\]");
	}

	private void logCounts() {
		count++;
		if (count % 10000 == 0)
			SentenceExtractionApp.LOG
					.info("The number of wikipedia pages processed: " + count);
		if (noEntryCount % 100 == 0)
			SentenceExtractionApp.LOG.info(noEntryCount
					+ " pages has no entry in wikidata");
		if (invalidCount % 100 == 0)
			SentenceExtractionApp.LOG.info(invalidCount + " pages are invalid");
	}

	private boolean isPageValid(WikiArticle page) {
		boolean isRedirect = false;
		List<String> redirectList = Arrays.asList(Config.getInstance()
				.getStringArray(Config.PAGE_REDIRECT));
		for (String red : redirectList) {
			isRedirect = isRedirect || page.getText().startsWith(red);
		}
		return page != null && page.getText() != null && !isRedirect;
	}

	private boolean isSubjectValid(String subjectId, String wikipediaTitle) {
		boolean hasEntry = !subjectId.equals("");
		boolean isValid = true;
		for (String invalid : invalidPageTitles) {
			if (wikipediaTitle.contains(invalid)) {
				isValid = false;
				break;
			}
		}
		if (!hasEntry)
			noEntryCount++;
		if (!isValid)
			invalidCount++;

		return hasEntry && isValid;
	}

	public String cleanUpText(String text) {
		String cleanText = text
				// to remove xml comments
				.replaceAll("(?m)<!--[\\s\\S]*?-->", "")
				.replaceAll("\\<.*?\\>", "").replaceAll("==.*?==", "")
				.replaceAll("\\{.*?\\}", "").replaceAll("http.*?\\s", "")
				.replaceAll("&ndash", "");
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

	public String removeContentBetweenMatchingBracket(String input,
			String pattern, char open, char close) {
		String result = "";
		try {
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
		} catch (StackOverflowError e) {

		}
		return result;
	}

	public String fromStringToWikilabel(String image) {
		String label = "";
		if (image.contains("|")) {
			String[] images = image.split("\\|");
			label = images[0];
		} else {
			label = image;
		}
		label = StringUtils.capitalize(label.trim().replaceAll(" ", "_"));
		return label;
	}
}
