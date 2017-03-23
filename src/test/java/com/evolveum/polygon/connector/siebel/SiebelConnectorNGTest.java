package com.evolveum.polygon.connector.siebel;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.siebel.xml.employee_20interface.ListOfRelatedEmployeeOrganization;
import com.siebel.xml.employee_20interface.ListOfRelatedPosition;
import com.siebel.xml.employee_20interface.ListOfRelatedResponsibility;
import com.siebel.xml.employee_20interface.RelatedEmployeeOrganization;
import com.siebel.xml.employee_20interface.RelatedPosition;
import com.siebel.xml.employee_20interface.RelatedResponsibility;
import com.siebel.xml.swiemployeeio.Employee;
import com.siebel.xml.swiemployeeio.EmployeePosition;
import com.siebel.xml.swiemployeeio.ListOfEmployeePosition;

import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.testng.annotations.BeforeMethod;

import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_ALIAS;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_CELL_PHONE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_EMAIL_ADDR;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_EMPLOYEE_TYPE_CODE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_FAX;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_FIRST_NAME;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_JOB_TITLE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_LAST_NAME;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_PERSONAL_TITLE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_PHONE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_PREFERRED_COMMUNICATION;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_PRIMARY_ORGANIZATION;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_PRIMARY_POSITION;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_PRIMARY_RESPONSIBILITY;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_SALES_CHANNEL;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_SECONDARY_ORGANIZATIONS;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_SECONDARY_POSITIONS;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_SECONDARY_RESPONSIBILITIES;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_TIME_ZONE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.ATTR_USER_TYPE;
import static com.evolveum.polygon.connector.siebel.SiebelConnector.CONNID_SPI_FIRST_PAGE_OFFSET;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static org.identityconnectors.common.CollectionUtil.newSet;
import static org.identityconnectors.common.StringUtil.isNotEmpty;
import static org.identityconnectors.framework.common.objects.AttributeUtil.getStringValue;
import static org.identityconnectors.framework.common.objects.ObjectClass.ACCOUNT;
import static org.identityconnectors.framework.common.objects.OperationOptions.OP_PAGED_RESULTS_COOKIE;
import static org.identityconnectors.framework.common.objects.OperationOptions.OP_PAGED_RESULTS_OFFSET;
import static org.identityconnectors.framework.common.objects.OperationOptions.OP_PAGE_SIZE;
import static org.identityconnectors.test.common.TestHelpers.searchToList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;


/**
 *
 * @author  Marián Petráš
 */
public class SiebelConnectorNGTest {

	private static final OperationOptions NO_OPERATION_OPTIONS
			= new OperationOptions(Collections.<String, Object>emptyMap());

	private SiebelConfiguration configuration;

	private SiebelConnector connector;

	private URL wsUrl;

	private String username;

	private String password;

	@BeforeSuite(alwaysRun = true)
	@Parameters({"wsUrl"})
	public void setWsUrl(@Optional final String wsUrl) throws MalformedURLException {
		this.wsUrl = isNotEmpty(wsUrl) ? new URL(wsUrl) : null;
	}

	@BeforeSuite(alwaysRun = true)
	@Parameters({"username", "password"})
	public void setCredentials(@Optional final String username,
	                           @Optional final String password) {
		this.username = username;
		this.password = password;
	}

	@BeforeClass
	public void initConfiguration() {
		configuration = new SiebelConfiguration();

		if (wsUrl != null) {
			configuration.setWsUrl(wsUrl.toString());
		}

		if (username != null) {
			configuration.setUsername(username);
			configuration.setPassword(password);
		}
	}

	@BeforeMethod
	public void initConnector() {
		connector = new SiebelConnector();
	}

	@Test
	public void testTest() {
		configureForActualWS();
		try {
			connector.test();
		} catch (RuntimeException ex) {
			fail(ex.getMessage(), ex);
		}
	}

	@Test
	public void testCheckAlive() {
		configureForActualWS();
		try {
			connector.checkAlive();
		} catch (RuntimeException ex) {
			fail("not alive");
		}
	}

	private void configureForActualWS() {
		if (wsUrl == null) {
			throw new SkipException("WS URL is not set.");
		}

		connector.init(configuration);
	}

	@Test
	public void testSearchByLoginName_existing() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");

		Employee employee = queryService.addEmployee("41", "XTOMHOLO");
		addEmployeePosition(employee, "CEO");
		addEmployeePosition(employee, "cook", true);
		addEmployeePosition(employee, "plumber");

		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		final Filter filter = FilterBuilder.equalTo(new Name("XTOMHOLO"));

		final Collection<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertEquals(returnedObjects.size(), 1);

		final ConnectorObject returnedObject = returnedObjects.iterator().next();

		assertEquals(returnedObject.getName(), new Name("XTOMHOLO"));
		assertEquals(returnedObject.getUid(), new Uid("41"));

		final Attribute attrPrimaryPos = returnedObject.getAttributeByName(SiebelConnector.ATTR_PRIMARY_POSITION);

		assertEquals(getStringValue(attrPrimaryPos), "cook");

		final Attribute attrSecondaryPos = returnedObject.getAttributeByName(SiebelConnector.ATTR_SECONDARY_POSITIONS);
		final Collection<?> secondaryPositions = newSet(attrSecondaryPos.getValue());

		assertEquals(secondaryPositions, newSet("CEO", "plumber"));
	}

	@Test
	public void testSearchByLoginName_nonexisting() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		connector.init(queryService);

		final Filter filter = FilterBuilder.equalTo(new Name("XTOMHOLO"));

		final Collection<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertEmpty(returnedObjects);
	}

	@Test
	public void testSearchById_existing() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		final Filter filter = FilterBuilder.equalTo(new Uid("25"));

		final Collection<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertEquals(returnedObjects.size(), 1);

		final ConnectorObject returnedObject = returnedObjects.iterator().next();

		assertEquals(returnedObject.getName(), new Name("HCHKRDTN"));
		assertEquals(returnedObject.getUid(), new Uid("25"));
	}

	@Test
	public void testSearchById_nonexisting() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		final Filter filter = FilterBuilder.equalTo(new Uid("38"));

		final Collection<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertEmpty(returnedObjects);
	}

	@Test
	public void testSearchById_existing_refusedByHandler() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		final RefusingResultsHandler resultsHandler = new RefusingResultsHandler();

		final Filter filter = FilterBuilder.equalTo(new Uid("41"));

		search(connector, ACCOUNT, filter, resultsHandler, NO_OPERATION_OPTIONS);
		
		assertEmpty(resultsHandler.getObjects());
		assertEquals(resultsHandler.getHandleCallCount(), 1);
		assertTrue(resultsHandler.isHandleResultCalled());
		assertFalse(resultsHandler.getLastSearchResult().isAllResultsReturned());
	}

	@Test
	public void testFindAll_noPaging() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		testFindAll(queryService,
		            null,
		            asList("12", "25", "33", "41", "55"),
		            asList(new TestQueryService.QueryRecord(0, 5, true)));
	}

	@Test
	public void testFindAll_noPaging_refusedByHandler_1() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		final RefusingResultsHandler resultsHandler = new RefusingResultsHandler();   //doesn't accept any object

		queryService.startRecordingQueries();

		search(connector, ACCOUNT, null, resultsHandler, NO_OPERATION_OPTIONS);
		
		assertEmpty(resultsHandler.getObjects());
		assertEquals(resultsHandler.getHandleCallCount(), 1);
		assertTrue(resultsHandler.isHandleResultCalled());
		assertFalse(resultsHandler.getLastSearchResult().isAllResultsReturned());
	}

	@Test
	public void testFindAll_noPaging_refusedByHandler_2() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService);

		final RefusingResultsHandler resultsHandler = new RefusingResultsHandler(3);   //accepts 3 objects

		queryService.startRecordingQueries();

		search(connector, ACCOUNT, null, resultsHandler, NO_OPERATION_OPTIONS);

		testReturnedObjects(queryService,
		                    resultsHandler.getObjects(),
		                    asList("12", "25", "33"),
		                    asList(new TestQueryService.QueryRecord(0, 5, true)));
		assertEquals(resultsHandler.getHandleCallCount(), 4);
		assertTrue(resultsHandler.isHandleResultCalled());
		assertFalse(resultsHandler.getLastSearchResult().isAllResultsReturned());
	}

	/**
	 * Tests the case that the query service returns fewer records
	 * than requested, although more are available.
	 */
	@Test
	public void testFindAll_reluctantService_noPaging() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		queryService.addEmployee("72", "DAMADAMA");
		queryService.addEmployee("77", "GRUSGRUS");
		queryService.addEmployee("83", "ASIOOTUS");
		queryService.addEmployee("85", "LYNXLYNX");
		queryService.addEmployee("89", "TYTOALBA");
		queryService.addEmployee("91", "PICAPICA");
		queryService.setPageSizeOverride(4);
		connector.init(queryService);

		final SimpleSearchResultsHandler resultsHandler = new SimpleSearchResultsHandler();

		queryService.startRecordingQueries();

		search(connector, ACCOUNT, null, resultsHandler, NO_OPERATION_OPTIONS);

		testReturnedObjects(queryService,
		                    resultsHandler.getObjects(),
		                    asList("12", "25", "33", "41", "55", "72", "77", "83", "85", "89", "91"),
		                    asList(new TestQueryService.QueryRecord(0,  4, false),
		                           new TestQueryService.QueryRecord(4,  8, false),
		                           new TestQueryService.QueryRecord(8, 11, true)));
		assertTrue(resultsHandler.isHandleResultCalled());
		assertTrue(resultsHandler.getLastSearchResult().isAllResultsReturned());
	}

	/**
	 * Tests the case that the query service returns fewer records
	 * than requested, although more are available.
	 */
	@Test
	public void testFindAll_reluctantService_paging() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		queryService.addEmployee("68", "UFUFUFUF");
		queryService.addEmployee("72", "DAMADAMA");
		queryService.addEmployee("77", "GRUSGRUS");
		queryService.addEmployee("83", "ASIOOTUS");
		queryService.addEmployee("85", "LYNXLYNX");
		queryService.addEmployee("89", "TYTOALBA");
		queryService.addEmployee("91", "PICAPICA");
		queryService.addEmployee("94", "BUBOBUBO");
		queryService.setPageSizeOverride(3);
		connector.init(queryService);

		final Map<String, Object> searchOptions = new HashMap<>();

		SimpleSearchResultsHandler resultsHandler;

		searchOptions.put(OP_PAGE_SIZE, (Object) 5);

		queryService.startRecordingQueries();
		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       searchOptions);

		testReturnedObjects(queryService,
		                    resultsHandler.getObjects(),
		                    asList("12", "25", "33", "41", "55"),
		                    asList(new TestQueryService.QueryRecord(0, 3, false),
		                           new TestQueryService.QueryRecord(3, 5, false)));
		assertTrue(resultsHandler.isHandleResultCalled());
		assertFalse(resultsHandler.getLastSearchResult().isAllResultsReturned());

		queryService.setPageSizeOverride(TestQueryService.NO_OVERRIDE);

		searchOptions.put(OP_PAGED_RESULTS_OFFSET, (Object) (5 + CONNID_SPI_FIRST_PAGE_OFFSET));

		queryService.startRecordingQueries();
		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       searchOptions);

		testReturnedObjects(queryService,
		                    resultsHandler.getObjects(),
		                    asList("68", "72", "77", "83", "85"),
		                    asList(new TestQueryService.QueryRecord(5, 10, false)));
		assertTrue(resultsHandler.isHandleResultCalled());
		assertFalse(resultsHandler.getLastSearchResult().isAllResultsReturned());

		queryService.setPageSizeOverride(2);

		searchOptions.put(OP_PAGED_RESULTS_OFFSET, (Object) (10 + CONNID_SPI_FIRST_PAGE_OFFSET));

		queryService.startRecordingQueries();
		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       searchOptions);

		testReturnedObjects(queryService,
		                    resultsHandler.getObjects(),
		                    asList("89", "91", "94"),
		                    asList(new TestQueryService.QueryRecord(10, 12, false),
		                           new TestQueryService.QueryRecord(12, 13, true)));
		assertTrue(resultsHandler.isHandleResultCalled());
		assertTrue(resultsHandler.getLastSearchResult().isAllResultsReturned());
	}

	@Test
	public void testFindAll_exactPage() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		connector.init(queryService, 4);

		testFindAll(queryService,
		            null,
		            asList("12", "25", "33", "41"),
		            asList(new TestQueryService.QueryRecord(0, 4, true)));
	}

	@Test
	public void testFindAll_twoExactPages() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		queryService.addEmployee("68", "UFUFUFUF");
		connector.init(queryService, 3);

		testFindAll(queryService,
		            null,
		            asList("12", "25", "33", "41", "55", "68"),
		            asList(new TestQueryService.QueryRecord(0, 3, false),
		                   new TestQueryService.QueryRecord(3, 6, true)));
	}

	@Test
	public void testFindAll_twoInexactPages() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		queryService.addEmployee("68", "UFUFUFUF");
		connector.init(queryService, 4);

		testFindAll(queryService,
		            null,
		            asList("12", "25", "33", "41", "55", "68"),
		            asList(new TestQueryService.QueryRecord(0, 4, false),
		                   new TestQueryService.QueryRecord(4, 6, true)));
	}

	@Test
	public void testFindAll_bigPage() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService, 17);

		testFindAll(queryService,
		            null,
		            asList("12", "25", "33", "41", "55"),
		            asList(new TestQueryService.QueryRecord(0, 5, true)));
	}

	@Test
	public void testFindAll_smallPage() {
		final TestQueryService queryService = new TestQueryService();
		queryService.addEmployee("12", "BFLMPSVZ");
		queryService.addEmployee("25", "HCHKRDTN");
		queryService.addEmployee("33", "BUFOBUFO");
		queryService.addEmployee("41", "XTOMHOLO");
		queryService.addEmployee("55", "BLBLBLBL");
		connector.init(queryService, 2);

		testFindAll(queryService,
		            null,
		            asList("12", "25", "33", "41", "55"),
		            asList(new TestQueryService.QueryRecord(0, 2, false),
		                   new TestQueryService.QueryRecord(2, 4, false),
		                   new TestQueryService.QueryRecord(4, 5, true)));
	}

	@Test
	public void testPaging_Simple() {
		final TestQueryService queryService = new TestQueryService();
		for (int i = 1; i <= 22; i++) {
			queryService.addEmployee(Integer.toString(i), "PERSON_" + i);
		}
		connector.init(queryService, 15);

		final Map<String, Object> searchOptions = new HashMap<>();

		SimpleSearchResultsHandler resultsHandler;
		OperationOptions operationOptions;
		SearchResult searchResult;
		String pagedResultsCookie;


		queryService.startRecordingQueries();

		searchOptions.put(OP_PAGE_SIZE, 10);
		operationOptions = new OperationOptions(searchOptions);

		int totalObjectsCount = 0;

		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       operationOptions);

		assertNotNull(searchResult = resultsHandler.getLastSearchResult());
		assertFalse  (searchResult.isAllResultsReturned());
		assertIdsEqual(resultsHandler.getObjects(), asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));

		pagedResultsCookie = searchResult.getPagedResultsCookie();

		totalObjectsCount += resultsHandler.getObjects().size();

		searchOptions.put(OP_PAGED_RESULTS_OFFSET, totalObjectsCount + CONNID_SPI_FIRST_PAGE_OFFSET);
		searchOptions.put(OP_PAGED_RESULTS_COOKIE, pagedResultsCookie);
		operationOptions = new OperationOptions(searchOptions);

		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new RefusingResultsHandler(7),
		       operationOptions);

		assertNotNull(searchResult = resultsHandler.getLastSearchResult());
		assertFalse  (searchResult.isAllResultsReturned());
		assertIdsEqual(resultsHandler.getObjects(), asList("11", "12", "13", "14", "15", "16", "17"));

		pagedResultsCookie = searchResult.getPagedResultsCookie();

		totalObjectsCount += resultsHandler.getObjects().size();

		searchOptions.put(OP_PAGED_RESULTS_OFFSET, totalObjectsCount + CONNID_SPI_FIRST_PAGE_OFFSET);
		searchOptions.put(OP_PAGED_RESULTS_COOKIE, pagedResultsCookie);
		operationOptions = new OperationOptions(searchOptions);

		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       operationOptions);

		assertNotNull(searchResult = resultsHandler.getLastSearchResult());
		assertTrue   (searchResult.isAllResultsReturned());
		assertIdsEqual(resultsHandler.getObjects(), asList("18", "19", "20", "21", "22"));
	}

	@Test
	public void testPaging_requestedPageLargerThanMaxPageSize() {
		final TestQueryService queryService = new TestQueryService();
		for (int i = 1; i <= 42; i++) {
			queryService.addEmployee(Integer.toString(i), "PERSON_" + i);
		}
		connector.init(queryService, 15);

		final Map<String, Object> searchOptions = new HashMap<>();

		SimpleSearchResultsHandler resultsHandler;
		OperationOptions operationOptions;
		SearchResult searchResult;
		String pagedResultsCookie;


		queryService.startRecordingQueries();

		searchOptions.put(OP_PAGE_SIZE, 20);
		operationOptions = new OperationOptions(searchOptions);

		int totalObjectsCount = 0;

		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       operationOptions);

		assertNotNull(searchResult = resultsHandler.getLastSearchResult());
		assertFalse  (searchResult.isAllResultsReturned());
		assertIdsEqual(resultsHandler.getObjects(), asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20"));

		pagedResultsCookie = searchResult.getPagedResultsCookie();

		totalObjectsCount += resultsHandler.getObjects().size();

		searchOptions.put(OP_PAGED_RESULTS_OFFSET, totalObjectsCount + CONNID_SPI_FIRST_PAGE_OFFSET);
		searchOptions.put(OP_PAGED_RESULTS_COOKIE, pagedResultsCookie);
		operationOptions = new OperationOptions(searchOptions);

		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new RefusingResultsHandler(18),
		       operationOptions);

		assertNotNull(searchResult = resultsHandler.getLastSearchResult());
		assertFalse  (searchResult.isAllResultsReturned());
		assertIdsEqual(resultsHandler.getObjects(), asList("21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38"));

		pagedResultsCookie = searchResult.getPagedResultsCookie();

		totalObjectsCount += resultsHandler.getObjects().size();

		searchOptions.put(OP_PAGED_RESULTS_OFFSET, totalObjectsCount + CONNID_SPI_FIRST_PAGE_OFFSET);
		searchOptions.put(OP_PAGED_RESULTS_COOKIE, pagedResultsCookie);
		operationOptions = new OperationOptions(searchOptions);

		search(connector,
		       ACCOUNT,
		       null,
		       resultsHandler = new SimpleSearchResultsHandler(),
		       operationOptions);

		assertNotNull(searchResult = resultsHandler.getLastSearchResult());
		assertTrue   (searchResult.isAllResultsReturned());
		assertIdsEqual(resultsHandler.getObjects(), asList("39", "40", "41", "42"));
	}

	@Test(enabled = false)
	public void testActualWS_multipleRecordsReturned() {
		configureForActualWS();

		final Filter filter = null;

		final List<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		final Collection<String> knownNotFoundYet = new HashSet<>(asList("XJOAANTU", "XLUIANDE", "XPATALUS"));

		assertNotNull(returnedObjects);
		assertTrue(returnedObjects.size() >= knownNotFoundYet.size());
		for (ConnectorObject object : returnedObjects) {
			final String objName = object.getName().getNameValue();
			knownNotFoundYet.remove(objName);
		}
		assertTrue(knownNotFoundYet.isEmpty(), "Some of the expected accounts were not returned: " + knownNotFoundYet.toArray());
	}

	@Test
	public void testActualWS_recordById() {
		configureForActualWS();

		final Filter filter = FilterBuilder.equalTo(new Uid("1-YPUJD"));

		final List<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertNotNull(returnedObjects);
		assertEquals(returnedObjects.size(), 1);

		final ConnectorObject returnedObject = returnedObjects.iterator().next();

		assertEquals(returnedObject.getName(), new Name("XJOAANTU"));
		assertEquals(returnedObject.getUid(), new Uid("1-YPUJD"));
		assertStringAttribute(returnedObject, ATTR_FIRST_NAME, "Joaquim");
		assertStringAttribute(returnedObject, ATTR_LAST_NAME, "Antunes");
	}

	@Test
	public void testActualWS_recordById_nonexisting() {
		configureForActualWS();

		final Filter filter = FilterBuilder.equalTo(new Uid("9-7-5-3-1"));

		final List<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertNotNull(returnedObjects);
		assertTrue(returnedObjects.isEmpty());
	}

	@Test
	public void testActualWS_recordByLoginName() {
		configureForActualWS();

		final Filter filter = FilterBuilder.equalTo(new Name("HELPDESK"));

		final List<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertNotNull(returnedObjects);
		assertEquals(returnedObjects.size(), 1);

		final ConnectorObject returnedObject = returnedObjects.iterator().next();

		assertEquals(returnedObject.getName(), new Name("HELPDESK"));
		assertEquals(returnedObject.getUid(), new Uid("1-6MB7"));
		assertStringAttribute(returnedObject, ATTR_FIRST_NAME, "User");
		assertStringAttribute(returnedObject, ATTR_LAST_NAME, "Application");
	}

	@Test
	public void testActualWS_recordByLoginName_nonexisting() {
		configureForActualWS();

		final Filter filter = FilterBuilder.equalTo(new Name("HELPPP"));

		final List<ConnectorObject> returnedObjects = searchToList(connector, ACCOUNT, filter);

		assertNotNull(returnedObjects);
		assertTrue(returnedObjects.isEmpty());
	}

	/* ----------------------- CREATE ----------------------- */

	private static class AttributeSetBuilder {

		final Map<String, Attribute> attributesMap = new HashMap<>();

		AttributeSetBuilder() {
		}

		void setName(final String name) {
			setAttribute(Name.NAME, name);
		}

		void setAttribute(final String attributeName,
		                  final Object... attributeValue) {
			attributesMap.put(attributeName, AttributeBuilder.build(attributeName, attributeValue));
		}

		Set<Attribute> build() {
			return new HashSet<>(attributesMap.values());
		}

	}

	@Test
	public void testCreate_allSupportedAttributes() {
		final TestInsertService insertService = new TestInsertService();
		connector.init(insertService);

		final AttributeSetBuilder builder = new AttributeSetBuilder();
		builder.setName("X3X5X7");
		builder.setAttribute(ATTR_ALIAS, "theAlias");
		builder.setAttribute(ATTR_EMPLOYEE_TYPE_CODE, "theEmplTypeCode");
		builder.setAttribute(ATTR_PERSONAL_TITLE, "Prof.");
		builder.setAttribute(ATTR_PREFERRED_COMMUNICATION, "Phone, Fax");
		builder.setAttribute(ATTR_TIME_ZONE, "GMT");
		builder.setAttribute(ATTR_USER_TYPE, "a small one");
		builder.setAttribute(ATTR_CELL_PHONE, "+38 123 456 789");
		builder.setAttribute(ATTR_EMAIL_ADDR, "john.bell@example.com");
		builder.setAttribute(ATTR_FAX,        "+38 987 654 321");
		builder.setAttribute(ATTR_FIRST_NAME, "John");
		builder.setAttribute(ATTR_JOB_TITLE, "the big boss");
		builder.setAttribute(ATTR_LAST_NAME, "Bell");
		builder.setAttribute(ATTR_PHONE, "+38 123 454 321");
		builder.setAttribute(ATTR_SALES_CHANNEL, "SkyNews");
		builder.setAttribute(ATTR_PRIMARY_POSITION, "the boss");
		builder.setAttribute(ATTR_SECONDARY_POSITIONS, "no", "other", "positions");
		builder.setAttribute(ATTR_PRIMARY_ORGANIZATION, "Bell's labs");
		builder.setAttribute(ATTR_SECONDARY_ORGANIZATIONS, "I live", "at Bell's labs");
		builder.setAttribute(ATTR_PRIMARY_RESPONSIBILITY, "be the boss");
		builder.setAttribute(ATTR_SECONDARY_RESPONSIBILITIES, "be a good boss", "make money");
		builder.setAttribute(OperationalAttributes.ENABLE_NAME, TRUE);

		connector.create(ACCOUNT,
		                 builder.build(),
		                 NO_OPERATION_OPTIONS);

		final com.siebel.xml.employee_20interface.Employee employee = insertService.getInsertedEmployee();

		assertEquals(employee.getLoginName(), "X3X5X7");
		assertEquals(employee.getAlias(), "theAlias");
		assertEquals(employee.getEmployeeTypeCode(), "theEmplTypeCode");
		assertEquals(employee.getPersonalTitle(), "Prof.");
		assertEquals(employee.getPreferredCommunications(), "Phone, Fax");
		assertEquals(employee.getTimeZoneName(), "GMT");
		assertNull  (employee.getTimeZoneId());
		assertEquals(employee.getUserType(), "a small one");
		assertEquals(employee.getCellPhone(), "+38 123 456 789");
		assertEquals(employee.getEMailAddr(), "john.bell@example.com");
		assertEquals(employee.getFax(),       "+38 987 654 321");
		assertEquals(employee.getFirstName(), "John");
		assertEquals(employee.getJobTitle(), "the big boss");
		assertEquals(employee.getLastName(), "Bell");
		assertEquals(employee.getPhone(), "+38 123 454 321");
		assertEquals(employee.getSalesChannel(), "SkyNews");

		final ListOfRelatedPosition positionsList = employee.getListOfRelatedPosition();
		assertNotNull(positionsList);
		final List<RelatedPosition> positions = positionsList.getRelatedPosition();
		assertNotNull(positions);
		assertEquals(positions.size(), 4);
		{
			RelatedPosition position;
			final Iterator<RelatedPosition> iterator = positions.iterator();
			position = iterator.next();
			assertEquals(position.getPosition(), "the boss");
			assertEquals(position.getIsPrimaryMVG(), "Y");
			position = iterator.next();
			assertEquals(position.getPosition(), "no");
			assertEquals(position.getIsPrimaryMVG(), "N");
			position = iterator.next();
			assertEquals(position.getPosition(), "other");
			assertEquals(position.getIsPrimaryMVG(), "N");
			position = iterator.next();
			assertEquals(position.getPosition(), "positions");
			assertEquals(position.getIsPrimaryMVG(), "N");
		}

		final ListOfRelatedEmployeeOrganization organizationsList = employee.getListOfRelatedEmployeeOrganization();
		assertNotNull(organizationsList);
		final List<RelatedEmployeeOrganization> organizations = organizationsList.getRelatedEmployeeOrganization();
		assertNotNull(organizations);
		assertEquals(organizations.size(), 3);
		{
			RelatedEmployeeOrganization organization;
			final Iterator<RelatedEmployeeOrganization> iterator = organizations.iterator();
			organization = iterator.next();
			assertEquals(organization.getEmployeeOrganization(), "Bell's labs");
			assertEquals(organization.getIsPrimaryMVG(), "Y");
			organization = iterator.next();
			assertEquals(organization.getEmployeeOrganization(), "I live");
			assertEquals(organization.getIsPrimaryMVG(), "N");
			organization = iterator.next();
			assertEquals(organization.getEmployeeOrganization(), "at Bell's labs");
			assertEquals(organization.getIsPrimaryMVG(), "N");
		}

		final ListOfRelatedResponsibility responsibilitiesList = employee.getListOfRelatedResponsibility();
		assertNotNull(responsibilitiesList);
		final List<RelatedResponsibility> responsibilies = responsibilitiesList.getRelatedResponsibility();
		assertNotNull(responsibilies);
		assertEquals(responsibilies.size(), 3);
		{
			RelatedResponsibility responsibility;
			final Iterator<RelatedResponsibility> iterator = responsibilies.iterator();
			responsibility = iterator.next();
			assertEquals(responsibility.getResponsibility(), "be the boss");
			assertEquals(responsibility.getIsPrimaryMVG(), "Y");
			responsibility = iterator.next();
			assertEquals(responsibility.getResponsibility(), "be a good boss");
			assertEquals(responsibility.getIsPrimaryMVG(), "N");
			responsibility = iterator.next();
			assertEquals(responsibility.getResponsibility(), "make money");
			assertEquals(responsibility.getIsPrimaryMVG(), "N");
		}

		assertEquals(employee.getEmployeeStatus(), "Active");
	}

	private static void assertStringAttribute(final ConnectorObject connectorObject,
	                                          final String attrName,
	                                          final String expected) {
		final Attribute attribute = connectorObject.getAttributeByName(attrName);

		assertNotNull(attribute);

		final String stringValue = AttributeUtil.getStringValue(attribute);

		assertEquals(stringValue, expected);
	}

	private void testFindAll(final TestQueryService queryService,
	                         final Map<String, Object> searchOptions,
	                         final List<String> expectedIds,
	                         final List<TestQueryService.QueryRecord> expectedQueryRecords) {
		Collection<ConnectorObject> returnedObjects;

		final OperationOptions operationOptions
				= (searchOptions != null) ? new OperationOptions(searchOptions)
				                          : NO_OPERATION_OPTIONS;

		queryService.startRecordingQueries();

		returnedObjects = searchToList(connector, ACCOUNT, null, operationOptions);

		testReturnedObjects(queryService, returnedObjects, expectedIds, expectedQueryRecords);
	}

	private void testReturnedObjects(final TestQueryService queryService,
	                                 final Collection<ConnectorObject> returnedObjects,
	                                 final List<String> expectedIds,
	                                 final List<TestQueryService.QueryRecord> expectedQueryRecords) {
		List<TestQueryService.QueryRecord> recordedQueries;

		assertIdsEqual(returnedObjects, expectedIds);

		recordedQueries = queryService.stopRecordingQueries();

		assertEquals(recordedQueries, expectedQueryRecords);
	}

	/**
	 * Replacement for {@code org.identityconnectors.test.common.TestHelpers.search(...)}.
	 * The reason for this replacement is that the original implmentation
	 * from class {@code TestHelpers} doesn't pass the results handler
	 * that was passed as a paremeter but a proxy object that only implements
	 * interface {@link ResultsHandler} (but not {@link SearchResultsHandler}).
	 * This replacement implementation does not use the proxy but passes
	 * the same {@code ResultsHandler} instance that it was passes as
	 * an argument.
	 *
	 * @param  searchOp  object to be used for searching
	 * @param  objectClass  class of objects to be searched (ACCOUNT is the only class supported)
	 * @param  filter  filter (search criteria) to be used (may be {@code null})
	 * @param  resultsHandler  results handler to be used
	 * @param  operationOptions  operation options to be used (may be {@code null})
	 */
	private void search(final SearchOp<?> searchOp,
	                    final ObjectClass objectClass,
	                    final Filter filter,
	                    final SimpleSearchResultsHandler resultsHandler,
	                    final Map<String, Object> operationOptionsMap) {
		final OperationOptions operationOptions;
		if (isNullOrEmpty(operationOptionsMap)) {
			operationOptions = null;
		} else {
			operationOptions = new OperationOptions(operationOptionsMap);
		}
		search(searchOp, objectClass, filter, resultsHandler, operationOptions);
	}

	/**
	 * Replacement for {@code org.identityconnectors.test.common.TestHelpers.search(...)}.
	 * The reason for this replacement is that the original implmentation
	 * from class {@code TestHelpers} doesn't pass the results handler
	 * that was passed as a paremeter but a proxy object that only implements
	 * interface {@link ResultsHandler} (but not {@link SearchResultsHandler}).
	 * This replacement implementation does not use the proxy but passes
	 * the same {@code ResultsHandler} instance that it was passes as
	 * an argument.
	 *
	 * @param  searchOp  object to be used for searching
	 * @param  objectClass  class of objects to be searched (ACCOUNT is the only class supported)
	 * @param  filter  filter (search criteria) to be used (may be {@code null})
	 * @param  resultsHandler  results handler to be used
	 * @param  operationOptions  operation options to be used (may be {@code null})
	 */
	private void search(final SearchOp<?> searchOp,
	                    final ObjectClass objectClass,
	                    final Filter filter,
	                    final SimpleSearchResultsHandler resultsHandler,
	                    final OperationOptions operationOptions) {
		if (searchOp != this.connector) {
			throw new IllegalArgumentException("The searchOp parameter is different from this test's connector.");
		}
		if (objectClass != ACCOUNT) {
			throw new IllegalArgumentException("ACCOUNT is the only object class supported.");
		}

		findMatchingObjects(filter, operationOptions, resultsHandler);
	}

	private Collection<ConnectorObject> findMatchingObjects(final Filter filter,
	                                                        final OperationOptions operationOptions,
	                                                        final SimpleSearchResultsHandler resultsHandler) {
		final OperationOptions searchOptions = (operationOptions != null) ? operationOptions : NO_OPERATION_OPTIONS;

		if (filter == null) {
			connector.executeQuery(ACCOUNT, null, resultsHandler, searchOptions);
		} else {
			final List<com.evolveum.polygon.connector.siebel.Filter> siebelFilters
					= connector.createFilterTranslator(ACCOUNT, operationOptions).translate(filter);
			for (com.evolveum.polygon.connector.siebel.Filter siebelFilter : siebelFilters) {
				connector.executeQuery(ACCOUNT, siebelFilter, resultsHandler, operationOptions);
			}
		}

		final Collection<ConnectorObject> result = resultsHandler.getObjects();
		return result;
	}

	private static void assertIdsEqual(final Collection<ConnectorObject> objects,
	                                   final Collection<String> objectIds) {
		if ((objects == null) || (objectIds == null)) {
			fail("Collections not equal: expected: " + objects + " and actual: " + objectIds);
		}

		final int objectsCount   = objects.size();
		final int objectIdsCount = objectIds.size();
		
		assertEquals(objectsCount, objectIdsCount,
		             "Lists don't have the same size:"
		                 + " expected: " + objectIdsCount
		                 + ", actual: " + objectsCount);

		int i = 0;
		final Iterator<ConnectorObject> objectsIt = objects.iterator();
		final Iterator<String> objectIdsIt        = objectIds.iterator();
		while (objectsIt.hasNext()) {
			final String expected = objectIdsIt.next();
			final String actual = objectsIt.next().getUid().getUidValue();
			if (!actual.equals(expected)) {
				fail("Lists differ at position " + i
				   + ": expected: " + expected + ", actual: " + actual);
			}
		}
	}

	private static void assertEmpty(final Collection<?> coll) {
		assertTrue(coll.isEmpty());
	}

	private static void addEmployeePosition(final Employee employee,
	                                        final String positionName) {
		addEmployeePosition(employee, positionName, false);
	}

	private static void addEmployeePosition(final Employee employee,
	                                        final String positionName,
	                                        final boolean primary) {
		ListOfEmployeePosition listOfEmployeePosition = employee.getListOfEmployeePosition();
		if (listOfEmployeePosition == null) {
			employee.setListOfEmployeePosition(listOfEmployeePosition = new ListOfEmployeePosition());
		}

		final EmployeePosition position = new EmployeePosition();
		position.setPosition(positionName);
		position.setIsPrimaryMVG(primary ? "Y" : "N");

		listOfEmployeePosition.getEmployeePosition().add(position);
	}

	private static boolean isNullOrEmpty(final Map<?,?> map) {
		return (map == null) || map.isEmpty();
	}

}
