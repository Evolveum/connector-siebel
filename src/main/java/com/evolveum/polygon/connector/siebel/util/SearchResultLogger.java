package com.evolveum.polygon.connector.siebel.util;

import org.identityconnectors.common.logging.Log;

import com.siebel.xml.swiemployeeio.Employee;


/**
 * Builds a log entry with a list of found employees.
 *
 * @author  Marián Petráš
 */
public class SearchResultLogger {

	private static final Log.Level LEVEL = Log.Level.OK;


	private final boolean enabled;

	private final Log log;

	private final StringBuilder buf;


	private boolean empty = true;


	public SearchResultLogger(final Log log) {
		enabled = log.isLoggable(LEVEL);

		if (enabled) {
			this.log = log;
			buf = new StringBuilder(2500);
		} else {
			this.log = null;
			buf = null;
		}
	}

	public void logEmployee(final Employee employee) {
		if (!enabled) {
			return;
		}

		buf.append(empty ? "Employees found: "
		                 : "), ");
		buf.append(employee.getLoginName()).append(" (id: ").append(employee.getId());

		empty = false;
	}

	public void writeResultToLog() {
		if (!enabled) {
			return;
		}

		writeToLog(empty ? "No employees found."
		                 : buf.append(')').toString());
	}

	private void writeToLog(final String text) {
		assert enabled;
		assert (log != null);

		log.log(LEVEL, null, text);
	}

}
