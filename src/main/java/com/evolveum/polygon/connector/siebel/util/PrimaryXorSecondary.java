package com.evolveum.polygon.connector.siebel.util;

/**
 * Holds information obout presence/absence of two values
 * (here named <em>primary</em> and <em>secondary</em>) where either one
 * can be missing but the state that both values are missing is considered
 * to be equal to the state that none is missing.
 *
 * @author Marián Petráš
 */
public final class PrimaryXorSecondary {

	private final String primary;
	private final String secondary;

	private String missing;

	public PrimaryXorSecondary(final String primary,
	                           final String secondary) {
		this.primary = primary;
		this.secondary = secondary;
	}

	public void markPresent(final String present) {
		if (present.equals(primary)) {
			missing = (missing == null) ? secondary : null;
		} else if (present.equals(secondary)) {
			missing = (missing == null) ? primary : null;
		} else {
			throw new IllegalArgumentException("Neither primary nor secondary: " + present);
		}
	}

	public String getMissing() {
		return missing;
	}

}
