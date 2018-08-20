/**
 *
 */
package de.dfki.mlt.wre;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import de.dfki.lt.tools.tokenizer.JTok;
import de.dfki.lt.tools.tokenizer.annotate.AnnotatedString;
import de.dfki.lt.tools.tokenizer.output.Outputter;
import de.dfki.lt.tools.tokenizer.output.Paragraph;
import de.dfki.lt.tools.tokenizer.output.TextUnit;
import de.dfki.lt.tools.tokenizer.output.Token;
import de.dfki.mlt.munderline.MunderLine;
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
	private String lang;
	public int noEntryCount = 0;
	public int invalidCount = 0;
	public int count = 0;
	private JTok jtok;
	private StanfordCoreNLP pipeline;
	private LemmatizerME lemmatizer;
	private MunderLine munderLine;

	public ArticleFilter(String lang) {
		this.extensionList = Arrays.asList(Config.getInstance().getStringArray(Config.WIKIPEDIA_EXTENSION));
		this.invalidPageTitles = Arrays.asList(Config.getInstance().getStringArray(Config.WIKIPEDIA_INVALID_PAGES));
		this.lang = lang;
		try {
			this.jtok = new JTok();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (this.lang.equals("en")) {
			initializeENModuls();
		} else if (this.lang.equals("de")) {
			initializeDEModuls();
		}
	}

	private void initializeENModuls() {
		Properties props;
		props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma");
		this.pipeline = new StanfordCoreNLP(props);
	}

	private void initializeDEModuls() {
		LemmatizerModel lemmatizerModel = null;
		try {
			this.munderLine = new MunderLine("DE_pipeline.conf");
			String lemmatizerModelPath = "models/DE-lemmatizer.bin";
			InputStream in = this.getClass().getClassLoader().getResourceAsStream(lemmatizerModelPath);
			if (null == in) {
				in = Files.newInputStream(Paths.get(lemmatizerModelPath));
			}
			lemmatizerModel = new LemmatizerModel(in);
		} catch (ConfigurationException | IOException e) {
			e.printStackTrace();
		}
		this.lemmatizer = new LemmatizerME(lemmatizerModel);
	}

	public void process(WikiArticle page, Siteinfo siteinfo) throws SAXException {
		if (isPageValid(page)) {
			logCounts();
			String wikipediaTitle = fromStringToWikilabel(page.getTitle());
			String pageId = page.getId();
			String subjectId = SentenceExtractionApp.esService.getItemId(wikipediaTitle);
			if (isSubjectValid(subjectId, wikipediaTitle)) {
				String text = page.getText();
				text = removeContentBetweenMatchingBracket(text, "{{", '{', '}');
				text = removeContentBetweenMatchingBracket(text, "(", '(', ')');
				for (String extension : extensionList)
					text = removeContentBetweenMatchingBracket(text, extension, '[', ']');
				text = cleanUpText(text);
				String firstSentence = getFirstSentence(text.trim());
				if (firstSentence.length() > 0 && !firstSentence.contains("may refer to")) {
					firstSentence = cleanUpText(firstSentence);
					firstSentence = fixSubjectAnnotation(firstSentence);
					String tokenizedSentence = tokenizeLemmatizeText(firstSentence);
					try {
						SentenceExtractionApp.esService.insertSentence(pageId, firstSentence, subjectId, wikipediaTitle,
								tokenizedSentence);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public String tokenizeLemmatizeText(String text) {
		String resultText = new String();
		List<String> tokensAsString = tokenize(text);
		if (this.lang.equals("en")) {
			resultText = lemmatizeEN(tokensAsString);
		} else if (this.lang.equals("de")) {
			resultText = lemmatizeDE(tokensAsString);
		}
		return resultText;
	}

	public List<String> tokenize(String text) {
		AnnotatedString annotatedString = this.jtok.tokenize(text, this.lang);
		List<Token> tokenList = Outputter.createTokens(annotatedString);
		List<String> tokensAsString = new ArrayList<String>();
		for (Token token : tokenList) {
			tokensAsString.add(token.getImage());
		}
		return tokensAsString;
	}

	public String lemmatizeDE(List<String> tokensAsString) {
		String[][] coNllTable = this.munderLine.processTokenizedSentence(tokensAsString);

		String[] tokens = new String[coNllTable.length];
		String[] posTags = new String[coNllTable.length];
		for (int i = 0; i < coNllTable.length; i++) {
			tokens[i] = coNllTable[i][1];
			posTags[i] = coNllTable[i][3];
		}
		String[] lemmata = this.lemmatizer.lemmatize(tokens, posTags);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < lemmata.length; i++) {
			if (containsAnnotation(tokens[i]) || lemmata[i].equals("--"))
				builder.append(tokens[i] + " ");
			else
				builder.append(lemmata[i] + " ");
		}
		return builder.toString().trim();
	}

	public String lemmatizeEN(List<String> tokensAsString) {
		StringBuilder builder = new StringBuilder();
		Annotation document = null;
		for (String token : tokensAsString) {
			if (!containsAnnotation(token)) {
				document = new Annotation(token);
				this.pipeline.annotate(document);
				List<CoreMap> sentences = document.get(SentencesAnnotation.class);
				for (CoreMap sentence : sentences) {
					for (CoreLabel coreLabel : sentence.get(TokensAnnotation.class)) {
						String image = coreLabel.get(LemmaAnnotation.class);
						// String tag = coreLabel.get(PartOfSpeechAnnotation.class);
						image = replaceParantheses(image).toLowerCase();
						builder.append(image + " ");
					}
				}
			} else {
				builder.append(token + " ");
			}
		}
		return builder.toString().trim();
	}

	private boolean containsAnnotation(String text) {
		return text.contains("[[") || text.contains("]]") || text.contains("'''");
	}

	public String replaceParantheses(String image) {
		return image = image.replaceAll("-lrb-", "\\(").replaceAll("-rrb-", "\\)").replaceAll("-lcb-", "\\{")
				.replaceAll("-rcb-", "\\}").replaceAll("-lsb-", "\\[").replaceAll("-rsb-", "\\]");
	}

	private void logCounts() {
		count++;
		if (count % 10000 == 0)
			SentenceExtractionApp.LOG.info("The number of wikipedia pages processed: " + count);
		if (noEntryCount % 10000 == 0)
			SentenceExtractionApp.LOG.info(noEntryCount + " pages has no entry in wikidata");
		if (invalidCount % 10000 == 0)
			SentenceExtractionApp.LOG.info(invalidCount + " pages are invalid");
	}

	private boolean isPageValid(WikiArticle page) {
		boolean isRedirect = false;
		List<String> redirectList = Arrays.asList(Config.getInstance().getStringArray(Config.PAGE_REDIRECT));
		for (String redirection : redirectList) {
			isRedirect = isRedirect || page.getText().startsWith(redirection);
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
				.replaceAll("(?m)<!--[\\s\\S]*?-->", "").replaceAll("\\<.*?\\>", "").replaceAll("==.*?==", "")
				.replaceAll("\\{.*?\\}", "").replaceAll("http.*?\\s", "").replaceAll("&ndash", "");
		return cleanText;
	}

	/***
	 * '''abc'''xyz => ''' abc ''' xyz
	 */

	public String fixSubjectAnnotation(String text) {
		StringBuilder builder = new StringBuilder();
		boolean isInQuote = false;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\'') {
				if (!isInQuote && (i - 1 >= 0 && text.charAt(i - 1) != ' '))
					builder.append(" ");
				builder.append(text.charAt(i));
				isInQuote = true;
			} else {
				if (text.charAt(i) != ' ' && isInQuote) {
					if ((i - 1 >= 0 && text.charAt(i - 1) != ' '))
						builder.append(" ");
					isInQuote = false;
				}
				builder.append(text.charAt(i));
			}
		}
		return builder.toString().trim();
	}

	private String getFirstSentence(String inputText) {
		StringBuilder builder = new StringBuilder();
		AnnotatedString annString = this.jtok.tokenize(inputText, this.lang);
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

	public String removeContentBetweenMatchingBracket(String input, String pattern, char open, char close) {
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
				result = input.substring(0, start) + removeContentBetweenMatchingBracket(
						input.substring(end, input.length()), pattern, open, close);
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
