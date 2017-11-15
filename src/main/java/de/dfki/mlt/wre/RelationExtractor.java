/**
 *
 */
package de.dfki.mlt.wre;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class RelationExtractor {

	public void relationProcess(String sentence, String subjectId,
			String wikipediaTitle) {
		HashMap<String, HashMap<String, String>> objectRelationMap = new HashMap<String, HashMap<String, String>>();
		List<String> relationIdList = new ArrayList<String>();
		List<String> objectList = getObjectList(sentence);
		String plainSentence = removeMarkup(sentence);
		String searchSentence = " " + plainSentence + " ";
		for (String objectLabel : objectList) {
			String objectId = WikiRelationExtractionApp.esService
					.getItemId(objectLabel);
			if (!objectId.equals("")) {
				relationIdList = WikiRelationExtractionApp.esService
						.getRelationsOfTwoItems(subjectId, objectId);
				HashMap<String, String> relationMap = new HashMap<String, String>();
				for (String propId : relationIdList) {
					Property property = WikiRelationExtractionApp.esService
							.getProperty(propId);
					String matchedString = matchString(searchSentence, property);
					relationMap.put(propId, matchedString);
				}
				objectRelationMap.put(objectId, relationMap);
			}
		}
		try {
			WikiRelationExtractionApp.esService.insertRelation(plainSentence,
					subjectId, wikipediaTitle, objectRelationMap);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String matchString(String surface, Property property) {
		for (String label : property.getAliases()) {
			if (surface.contains(" " + label + " ")) {
				return label;
			}
		}
		return "";
	}

	public List<String> getObjectList(String sentence) {
		System.out.println(sentence);
		sentence = sentence.replaceAll("'' '.*?'' '", "").replaceAll("' '", "");
		List<String> objectList = new ArrayList<String>();
		Matcher matcher = Pattern.compile("\\[\\[(.*?)\\]\\]")
				.matcher(sentence);
		while (matcher.find()) {
			String objectLabel = matcher.group(1);
			if (objectLabel.contains("|")) {
				objectLabel = objectLabel.split("\\|")[0];
			}
			objectList.add(Utils.fromStringToWikilabel(objectLabel));
		}
		return objectList;

	}

	public String removeMarkup(String sentence) {
		sentence = sentence.replaceAll("'' '", "").replaceAll("' '", "");
		// System.out.println(sentence);
		Matcher matcher = Pattern.compile("\\[\\[(.*?)\\]\\]")
				.matcher(sentence);
		while (matcher.find()) {
			String found = matcher.group(1);
			if (found.contains("|")) {
				int beginIndex = matcher.start();
				int endIndex = matcher.end();
				sentence = sentence.substring(0, beginIndex)
						+ found.split("\\|")[1].replaceAll("\\[\\[ ", "")
								.replaceAll(" \\]\\]", "")
						+ sentence.substring(endIndex);
				matcher = Pattern.compile("\\[\\[ (.*?) \\]\\]")
						.matcher(sentence);
			}
		}
		sentence = sentence.replaceAll("\\[\\[", "").replaceAll("\\]\\]", "")
				.trim().replaceAll(" +", " ");
		return sentence;
	}
}
