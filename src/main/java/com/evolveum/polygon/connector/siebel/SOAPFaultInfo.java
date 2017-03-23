package com.evolveum.polygon.connector.siebel;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

/**
 * Container for details of a SOAP fault.
 *
 * @author Marián Petráš
 */
final class SOAPFaultInfo {

	final String faultString;

	final List<Error> errors;

	private final List<Error> errorsList;

	{
		errorsList = new ArrayList<>();
		errors = unmodifiableList(errorsList);
	}

	SOAPFaultInfo(final String faultString) {
		this.faultString = faultString;
	}

	void addError(final String errorCode,
	              final String errorSymbol,
	              final String errorMsg) {
		errorsList.add(new Error(errorCode, errorSymbol, errorMsg));
	}

	Error getFirstError() {
		return errorsList.isEmpty() ? null : errorsList.get(0);
	}

	String getFirstErrorCode() {
		return errorsList.isEmpty() ? null : errorsList.get(0).code;
	}

	String getFirstErrorSymbol() {
		return errorsList.isEmpty() ? null : errorsList.get(0).symbol;
	}

	String getFirstErrorMsg() {
		return errorsList.isEmpty() ? null : errorsList.get(0).msg;
	}

	final class Error {

		final String code;
		final String symbol;
		final String msg;

		Error(final String errorCode,
		      final String errorSymbol,
		      final String errorMsg) {
			this.code = errorCode;
			this.symbol = errorSymbol;
			this.msg = errorMsg;
		}

	}

}
