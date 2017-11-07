/**
 *
 */
package de.dfki.mlt.wre;

import org.apache.commons.lang.StringUtils;

/**
 * @author Aydan Rende, DFKI
 *
 */
public final class Utils {

	private Utils() {

	}

	public static String fromStringToWikilabel(String image) {
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
