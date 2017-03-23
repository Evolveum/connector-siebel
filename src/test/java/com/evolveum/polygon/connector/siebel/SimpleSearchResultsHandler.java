package com.evolveum.polygon.connector.siebel;

import java.util.List;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.test.common.ToListResultsHandler;

/**
 *
 * @author  Marián Petráš
 */
public class SimpleSearchResultsHandler implements SearchResultsHandler {

	private final ToListResultsHandler nestedHandler;

	protected int handleCallCount = 0;

	private boolean handleResultCalled = false;

	private SearchResult lastSearchResult;

	/**
	 * Creates a handler that refuses objects from the beginning.
	 */
	SimpleSearchResultsHandler() {
		this.nestedHandler = new ToListResultsHandler();
	}

	@Override
	public boolean handle(final ConnectorObject connectorObject) {
		++handleCallCount;
		return preHandleObject(connectorObject) && nestedHandler.handle(connectorObject);
	}

	protected boolean preHandleObject(final ConnectorObject connectorObject) {
		return true;
	}

	@Override
	public void handleResult(final SearchResult result) {
		handleResultCalled = true;
		lastSearchResult = result;
	}

	/**
	 * Returns the list of accepted objects.
	 *
	 * @return  list of accepted connector objects
	 */
	List<ConnectorObject> getObjects() {
		return nestedHandler.getObjects();
	}

	SearchResult getLastSearchResult() {
		return lastSearchResult;
	}

	/**
	 * Returns the number the method {@code handle(ConnectorObject)} was called.
	 * 
	 * @return  number the method was called, regardless the returned value
	 */
	int getHandleCallCount() {
		return handleCallCount;
	}

	/**
	 * Returns whether the method {@code handleResult(SearchResult)} was called.
	 * 
	 * @return  {@code true} if the method was called, {@code false} otherwise
	 */
	boolean isHandleResultCalled() {
		return handleResultCalled;
	}

}
