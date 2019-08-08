/**
 *
 */
package de.dfki.mlt.ws2es;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import de.dfki.mlt.ws2es.preferences.Config;
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
			String wikipediaTitle = getWikipediaTitleForm(page.getTitle());
			String pageId = page.getId();
			String cleanText = cleanUpText(page.getText());
			String firstSentence = getFirstSentence(cleanText.trim());
			if (firstSentence.length() > 0 && !firstSentence.contains("may refer to")) {
				firstSentence = removeTags(firstSentence);
				firstSentence = fixSubjectAnnotation(firstSentence);
				String tokenizedSentence = lemmatizeText(firstSentence);
				List<String> candidateSubjIds = new ArrayList<String>();
				candidateSubjIds.add(App.esService.getItemId(wikipediaTitle, this.lang));
				App.esService.insertSentence(pageId, firstSentence, candidateSubjIds, wikipediaTitle,
						tokenizedSentence);
			}
		}

	}

	public String lemmatizeText(String text) {
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
			App.LOG.info("The number of wikipedia pages processed: " + count);
		if (invalidCount % 10000 == 0)
			App.LOG.info(invalidCount + " pages are invalid");
	}

	private boolean isPageValid(WikiArticle page) {
		// skip redirected pages
		boolean isRedirect = false;
		List<String> redirectList = Arrays.asList(Config.getInstance().getStringArray(Config.PAGE_REDIRECT));
		for (String redirection : redirectList) {
			isRedirect = isRedirect || page.getText().startsWith(redirection);
		}
		// skip wikipedia's internal pages
		boolean isValid = true;
		for (String invalid : invalidPageTitles) {
			if (page.getTitle().contains(invalid)) {
				isValid = false;
				break;
			}
		}
		if (!isValid)
			invalidCount++;

		return !(page == null || page.getText() == null || isRedirect || !isValid);
	}

	private String cleanUpText(String text) {
		text = removeContentBetweenMatchingBracket(text, "{{", '{', '}');
		text = removeContentBetweenMatchingBracket(text, "(", '(', ')');
		for (String extension : extensionList)
			text = removeContentBetweenMatchingBracket(text, extension, '[', ']');
		return removeTags(text);
	}

	public String removeTags(String text) {
		String cleanText = text.replaceAll("(?m)<!--[\\s\\S]*?-->", "").replaceAll("\\<.*?\\>", "")
				.replaceAll("==.*?==", "").replaceAll("\\{.*?\\}", "").replaceAll("http.*?\\s", "")
				.replaceAll("&ndash", "");
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

	public String getWikipediaTitleForm(String pageTitle) {
		String wikiTitle = "";
		if (pageTitle.contains("|")) {
			String[] splits = pageTitle.split("\\|");
			wikiTitle = splits[0];
		} else {
			wikiTitle = pageTitle;
		}
		wikiTitle = StringUtils.capitalize(wikiTitle.trim().replaceAll(" ", "_"));
		return wikiTitle;
	}

	public String getSubjectName(String sentence) {
		Pattern pattern = Pattern.compile("[''']+.*?[''']+");
		Matcher matcher = pattern.matcher(sentence);
		String found = "";
		while (matcher.find()) {
			found = matcher.group(0).replaceAll("[''']", "").trim();
		}
		found = found.replaceAll("\\[\\[ ", "").replaceAll(" \\]\\]", "").trim();
		return found;
	}
}
