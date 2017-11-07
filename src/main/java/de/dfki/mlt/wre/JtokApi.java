/**
 *
 */
package de.dfki.mlt.wre;

import java.io.IOException;

import de.dfki.lt.tools.tokenizer.JTok;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class JtokApi {
	private static JTok jtok;

	private static void createInstance() {
		try {
			jtok = new JTok();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static JTok getInstance() {

		if (jtok == null) {
			createInstance();
		}
		return jtok;
	}
}
