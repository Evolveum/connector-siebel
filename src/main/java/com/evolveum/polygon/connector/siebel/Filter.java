package com.evolveum.polygon.connector.siebel;

import org.identityconnectors.framework.common.objects.Attribute;

/**
 *
 * @author  Marián Petráš
 */
public class Filter {

	private static final String ATTR_NAME_UID = "__UID__";
	private static final String ATTR_NAME_NAME = "__NAME__";

	static enum Mode {
		SEARCH_BY_ID,
		SEARCH_BY_LOGIN_NAME,
		RETURN_ALL,
		;
	}

	final Mode mode;

	final String param;

	private Filter(final Mode mode,
	               final String param) {
		this.mode = mode;
		this.param = param;
	}

	static boolean isSupportedAttribute(final Attribute attr) {
		return isSupportedAttribute(attr.getName());
	}

	static boolean isSupportedAttribute(final String attrName) {
		switch (attrName) {
			case ATTR_NAME_UID:
			case ATTR_NAME_NAME:
				return true;
		}
		return false;
	}

	static Filter byIdOrLoginName(final String attrName,
	                              final String value) {
		switch (attrName) {
			case ATTR_NAME_UID:
				return byId(value);
			case ATTR_NAME_NAME:
				return byLoginName(value);
			default:
				return null;
		}
	}

	static Filter byId(final String id) {
		return new Filter(Mode.SEARCH_BY_ID, id);
	}

	static Filter byLoginName(final String loginName) {
		return new Filter(Mode.SEARCH_BY_LOGIN_NAME, loginName);
	}

	static Filter all() {
		return new Filter(Mode.RETURN_ALL, null);
	}

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder(50);
		buf.append("Filter(");
		switch (mode) {
			case RETURN_ALL:
				buf.append("no filtering");
				break;
			case SEARCH_BY_ID:
				buf.append("Id = \"").append(param).append('"');
				break;
			case SEARCH_BY_LOGIN_NAME:
				buf.append("LoginName = \"").append(param).append('"');
				break;
		}
		buf.append(')');
		return buf.toString();
	}

}
