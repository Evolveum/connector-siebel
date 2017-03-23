package com.evolveum.polygon.connector.siebel;

import org.identityconnectors.framework.common.objects.ConnectorObject;

/**
 * {@code ResultsHandler} that starts refusing objects after a given
 * number of accepted objects.
 *
 * @author  Marián Petráš
 */
final class RefusingResultsHandler extends SimpleSearchResultsHandler {

	private final int maxAcceptCount;

	/**
	 * Creates a handler that refuses objects from the beginning.
	 */
	RefusingResultsHandler() {
		this(0);
	}

	/**
	 * Creates a handler that starts refusing objects after a given
	 * number of accepted objects.
	 * 
	 * @param  maxAcceptCount  number of objects to be accepted before it starts
	 *                         refusing more objects
	 */
	RefusingResultsHandler(final int maxAcceptCount) {
		this.maxAcceptCount = maxAcceptCount;
	}

	@Override
	protected boolean preHandleObject(ConnectorObject connectorObject) {
		return handleCallCount <= maxAcceptCount;
	}

}
