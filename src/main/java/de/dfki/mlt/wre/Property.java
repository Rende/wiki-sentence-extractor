/**
 *
 */
package de.dfki.mlt.wre;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aydan Rende, DFKI
 *
 */
public class Property {
	private String id;
	private String label;
	private List<String> aliases;

	public Property() {
		id = "";
		label = "";
		aliases = new ArrayList<String>();
	}

	public Property(String id, String label, List<String> aliases) {
		this.id = id;
		this.label = label;
		this.aliases = aliases;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @return the aliases
	 */
	public List<String> getAliases() {
		return aliases;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @param label
	 *            the label to set
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	/**
	 * @param aliases
	 *            the aliases to set
	 */
	public void setAliases(List<String> aliases) {
		this.aliases = aliases;
	}

}
