/**
 * 
 */
package de.dfki.mlt.wse;

/**
 * @author Aydan Rende, DFKI
 *
 */
public enum AppMode {

	TRAIN("train"), TEST("test");
	private String mode;

	private AppMode(String mode) {
		this.mode = mode;
	}

	public String getMode() {
		return mode;
	}

	public static AppMode fromString(String text) {
		for (AppMode appMode : AppMode.values()) {
			if (appMode.mode.equalsIgnoreCase(text)) {
				return appMode;
			}
		}
		return null;
	}
}
