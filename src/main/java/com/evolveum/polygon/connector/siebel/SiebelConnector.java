package com.evolveum.polygon.connector.siebel;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HTTPException;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

import com.evolveum.polygon.connector.siebel.util.Pair;
import com.evolveum.polygon.connector.siebel.util.PrimaryXorSecondary;
import com.evolveum.polygon.connector.siebel.util.SearchResultLogger;
import com.siebel.asi.SWIEmployeeServicesQueryPageInput;
import com.siebel.asi.SWIEmployeeServicesQueryPageOutput;
import com.siebel.asi.SWISpcEmployeeSpcService;
import com.siebel.customui.SiebelEmployeeInsert1Input;
import com.siebel.customui.SiebelEmployeeInsert1Output;
import com.siebel.customui.SiebelEmployeeUpdate1Input;
import com.siebel.customui.SiebelEmployeeUpdate1Output;
import com.siebel.customui.SiebelSpcEmployee;
import com.siebel.xml.employee_20interface.ListOfEmployeeInterface;
import com.siebel.xml.employee_20interface.RelatedEmployeeOrganization;
import com.siebel.xml.employee_20interface.RelatedPosition;
import com.siebel.xml.employee_20interface.RelatedResponsibility;
import com.siebel.xml.swiemployeeio.Employee;
import com.siebel.xml.swiemployeeio.EmployeeOrganization;
import com.siebel.xml.swiemployeeio.EmployeePosition;
import com.siebel.xml.swiemployeeio.EmployeeResponsibility;
import com.siebel.xml.swiemployeeio.ListOfSwiemployeeio;

import static com.evolveum.polygon.connector.siebel.LogUtils.getAttributeNames;
import static com.evolveum.polygon.connector.siebel.Operation.CREATE;
import static com.evolveum.polygon.connector.siebel.Operation.UPDATE;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.EnumSet.of;
import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.common.StringUtil.isNotEmpty;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.MULTIVALUED;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_CREATABLE;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_UPDATEABLE;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.REQUIRED;
import static org.identityconnectors.framework.common.objects.AttributeUtil.getBooleanValue;
import static org.identityconnectors.framework.common.objects.AttributeUtil.getStringValue;
import static org.identityconnectors.framework.common.objects.AttributeUtil.isSpecialName;

@ConnectorClass(displayNameKey = "siebel.connector.display", configurationClass = SiebelConfiguration.class)
public class SiebelConnector implements PoolableConnector, TestOp, SchemaOp, SearchOp<Filter>, CreateOp, UpdateOp {

    private static final Log LOG = Log.getLog(SiebelConnector.class);

	private static final int HTTP_STATUS_UNAUTHORIZED = 401;

	private static final int HTTP_STATUS_REQUEST_TIMEOUT = 408;

	private static final String EMPLOYEE_ACTIVE = "Active";

	private static final String EMPLOYEE_INACTIVE = "Inactive";

	private static final String RESOURCE_PROP_VALUE_YES = "Y";

	private static final String RESOURCE_PROP_VALUE_NO  = "N";

	/**
	 * account Id used when testing the connection
	 *
	 * @see  #test()  test()
	 */
	private static final String TEST_ID = "BFLM_DUMMY_HCHKR";

	/**
	 * offset passed to the {@code SearchOp} when the first page is requested
	 */
	static final int CONNID_SPI_FIRST_PAGE_OFFSET = 1;

	/**
	 * number of the first row in Siebel
	 */
	private static final int SIEBEL_WS_FIRST_ROW_NUMBER = 0;

	/**
	 * number of the first row in Siebel - as a string
	 */
	private static final String SIEBEL_WS_FIRST_ROW_NUMBER_STR = Integer.toString(SIEBEL_WS_FIRST_ROW_NUMBER);

	/**
	 * constant to be added to the ConnId paged results offset to calculate
	 * the value of the corresponding web service's parameter <i>StartRowNum</i>
	 */
	private static final int CONNID_TO_WS_REC_NO_SHIFT
			= SIEBEL_WS_FIRST_ROW_NUMBER - CONNID_SPI_FIRST_PAGE_OFFSET;

	private static final Set<AttributeInfo.Flags> F_REQUIRED = of(REQUIRED);

	private static final Set<AttributeInfo.Flags> F_MULTIVALUED = of(MULTIVALUED);

	private static final Set<AttributeInfo.Flags> F_MULTIVALUED_READONLY = of(MULTIVALUED, NOT_UPDATEABLE);

	private static final SearchResult ALL_RESULTS_RETURNED = new SearchResult();

	/**
	 * error symbol of a SOAP fault caused by an attempt to create a duplicite
	 * account
	 */
	private static final String ERR_SYMBOL_DUPLICITE_LOGIN_NAME = "IDS_ERR_EAI_SA_INSERT_MATCH_FOUND";

	private static final String ERR_CODE_SPACE_IN_LOGIN_NAME = "SBL-APS-00195";

	/**
	 * error code of a SOAP fault caused by an attempt to change login name
	 * to a login name that is assigned to another account
	 */
	private static final String ERR_CODE_DUPLICITE_LOGIN_NAME = "SBL-DAT-00381";

	private static final String ERR_CODE_MISSING_LOGIN_NAME = "SBL-DAT-00498";

	private static final String ERR_CODE_NOT_FROM_PICKLIST = "SBL-DAT-00225";

	private static final String ERR_CODE_NOT_IN_BOUND_LIST = "SBL-EAI-04401";

	private static final String ERR_SYMBOL_NOT_IN_BOUND_LIST = "IDS_ERR_EAI_SA_PICK_VALIDATE";

	private static final String ERR_CODE_NOT_FROM_LIST_OF_VALUES = "SBL-DAT-00510";

	/**
	 * error code of a SOAP fault caused by an invalid value of attribute
	 * &quot;employee position&quot;
	 *
	 * @see  #ERR_SYMBOL_INVALID_POSITION
	 */
	private static final String ERR_CODE_INVALID_POSITION    = "SBL-EAI-04397";

	/**
	 * error code of a SOAP fault caused by an invalid value of attribute
	 * &quot;employee organization&quot; or &quot;employee responsibility&quot;
	 *
	 * @see  #ERR_SYMBOL_INVALID_ORG_OR_RESP
	 */
	private static final String ERR_CODE_INVALID_ORG_OR_RESP = "SBL-EAI-04184";

	/**
	 * error symbol of a SOAP fault caused by an invalid value of attribute
	 * &quot;employee position&quot;
	 *
	 * @see  #ERR_CODE_INVALID_POSITION
	 */
	private static final String ERR_SYMBOL_INVALID_POSITION    = "IDS_ERR_EAI_SA_NO_USERKEY";

	/**
	 * error symbol of a SOAP fault caused by an invalid value of attribute
	 * &quot;employee organization&quot; or &quot;employee responsibility&quot;
	 *
	 * @see  #ERR_CODE_INVALID_ORG_OR_RESP
	 */
	private static final String ERR_SYMBOL_INVALID_ORG_OR_RESP = "IDS_EAI_ERR_SA_INT_NOINSERT";

	static final String ATTR_ID                 = "Id";
	static final String ATTR_LOGIN_NAME         = "LoginName";
	static final String ATTR_ALIAS              = "Alias";
	static final String ATTR_EMPLOYEE_TYPE_CODE = "EmployeeTypeCode";
	static final String ATTR_PERSONAL_TITLE     = "PersonalTitle";
	static final String ATTR_PREFERRED_COMMUNICATION = "PreferredCommunication";
	static final String ATTR_TIME_ZONE          = "TimeZoneName";
	static final String ATTR_USER_TYPE          = "UserType";
	static final String ATTR_CELL_PHONE         = "CellPhone";
	static final String ATTR_EMAIL_ADDR         = "EMailAddr";
	static final String ATTR_FAX                = "Fax";
	static final String ATTR_FIRST_NAME         = "FirstName";
	static final String ATTR_JOB_TITLE          = "JobTitle";
	static final String ATTR_LAST_NAME          = "LastName";
	static final String ATTR_PHONE              = "Phone";
	static final String ATTR_SALES_CHANNEL      = "SalesChannel";
	static final String ATTR_PRIMARY_POSITION    = "PrimaryPosition";
	static final String ATTR_SECONDARY_POSITIONS = "SecondaryPositions";
	static final String ATTR_PRIMARY_ORGANIZATION    = "EmployeePrimaryOrganization";
	static final String ATTR_SECONDARY_ORGANIZATIONS = "EmployeeSecondaryOrganizations";
	static final String ATTR_PRIMARY_RESPONSIBILITY     = "PrimaryResponsibility";
	static final String ATTR_SECONDARY_RESPONSIBILITIES = "SecondaryResponsibilities";

	private static final PrimarySecondaryEmployeeAttribute<EmployeePosition, RelatedPosition> POSITIONS;

	private static final PrimarySecondaryEmployeeAttribute<EmployeeOrganization, RelatedEmployeeOrganization> ORGANIZATIONS;

	private static final PrimarySecondaryEmployeeAttribute<EmployeeResponsibility, RelatedResponsibility> RESPONSIBILITIES;

	static {
		try {
			POSITIONS = new PrimarySecondaryPosition();
			ORGANIZATIONS = new PrimarySecondaryEmployeeAttribute<>(EmployeeOrganization.class,
			                                                        RelatedEmployeeOrganization.class,
			                                                        ATTR_PRIMARY_ORGANIZATION,
			                                                        ATTR_SECONDARY_ORGANIZATIONS);
			RESPONSIBILITIES = new PrimarySecondaryEmployeeAttribute<>(EmployeeResponsibility.class,
			                                                           RelatedResponsibility.class,
			                                                           ATTR_PRIMARY_RESPONSIBILITY,
			                                                           ATTR_SECONDARY_RESPONSIBILITIES);
		} catch (ReflectiveOperationException ex) {
			throw new RuntimeException(ex);
		}
	}

    private SiebelConfiguration configuration;

	private SWISpcEmployeeSpcService queryService;

	private SiebelSpcEmployee insertUpdateService;

	private int maxResourcePageSize;

	private String maxResourcePageSizeStr;

	/**
	 * contains query input prototypes for each of the search modes
	 * (by Id, by login name, get all)
	 */
	private final Map<Filter.Mode, Pair<SWIEmployeeServicesQueryPageInput, Employee>> queryInputPrototypes
			= new EnumMap<>(Filter.Mode.class);

	private SoapFaultInspector soapFaultInspector;

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(final Configuration configuration) {
        LOG.info("Initializing connector (connecting to the WS)...");

        this.configuration = (SiebelConfiguration) configuration;

		queryService        = createService(SWISpcEmployeeSpcService.class);
		insertUpdateService = createService(SiebelSpcEmployee.class);
		setMaxResourcePageSize(this.configuration.getMaxPageSize());

		setupQueryInputPrototypes();

		try {
			soapFaultInspector = new SoapFaultInspector();
		} catch (XPathExpressionException ex) {
			throw new RuntimeException(ex);
		}
    }

	private void setMaxResourcePageSize(final int maxResourcePageSize) {
		this.maxResourcePageSize = maxResourcePageSize;
		this.maxResourcePageSizeStr = Integer.toString(maxResourcePageSize);
	}

	private void setupQueryInputPrototypes() {
		Pair<SWIEmployeeServicesQueryPageInput, Employee> queryInputPrototype;

		queryInputPrototype = createQueryInputPrototype();
		queryInputPrototypes.put(Filter.Mode.RETURN_ALL, queryInputPrototype);

		queryInputPrototype = createQueryInputPrototype();
		queryInputPrototypes.put(Filter.Mode.SEARCH_BY_ID, queryInputPrototype);

		queryInputPrototype = createQueryInputPrototype();
		queryInputPrototypes.put(Filter.Mode.SEARCH_BY_LOGIN_NAME, queryInputPrototype);
	}

	private Pair<SWIEmployeeServicesQueryPageInput, Employee> createQueryInputPrototype() {
		final SWIEmployeeServicesQueryPageInput queryInput = new SWIEmployeeServicesQueryPageInput();
		final Employee employee = new Employee();

		final ListOfSwiemployeeio listOfSwiemployeeio = new ListOfSwiemployeeio();
		listOfSwiemployeeio.getEmployee().add(employee);
		queryInput.setListOfSwiemployeeio(listOfSwiemployeeio);

		return new Pair<>(queryInput, employee);
	}

	private <S> S createService(final Class<S> seiClass) {
		final ClientProxyFactoryBean factory = new JaxWsProxyFactoryBean();   // a new instance must be used for each service
		/*
		// Uncomment this to enable SOAP request & response logging into external files.
		
		factory.getFeatures().add(new org.apache.cxf.feature.LoggingFeature("file:~/siebel-soap-response.xml",
		                                                                    "file:~/siebel-soap-request.xml",
		                                                                    100_000,
		                                                                    true));
		*/
		factory.setAddress (configuration.getWsUrl());
		factory.setUsername(configuration.getUsername());
		factory.setPassword(configuration.getPassword());
		factory.setServiceClass(seiClass);
		final S result = (S) factory.create();

		/* disable chunking: */
		final Client client = ClientProxy.getClient(result);
		final HTTPConduit http = (HTTPConduit) client.getConduit();
		final HTTPClientPolicy httpClientPolicy;
		{
			httpClientPolicy = new HTTPClientPolicy();
			httpClientPolicy.setAllowChunking(false);
			httpClientPolicy.setConnectionTimeout(configuration.getConnectTimeout());
			httpClientPolicy.setReceiveTimeout(configuration.getReceiveTimeout());
		}
		http.setClient(httpClientPolicy);

		return result;
	}

	/**
	 * Used by unit tests.
	 * 
	 * @param  queryService  test query service to be used by this connector
	 */
	void init(final SWISpcEmployeeSpcService queryService) {
		init(queryService, SiebelConfiguration.DEFAULT_MAX_PAGE_SIZE);
	}

	/**
	 * Used by unit tests.
	 *
	 * @param  queryService  test query service to be used by this connector
	 */
	void init(final SWISpcEmployeeSpcService queryService,
	          final int maxResourcePageSize) {
		this.queryService = queryService;
		setMaxResourcePageSize(maxResourcePageSize);

		setupQueryInputPrototypes();
	}

	/**
	 * Used by unit tests.
	 *
	 * @param  queryService  test update service to be used by this connector
	 */
	void init(final SiebelSpcEmployee insertUpdateService) {
		this.insertUpdateService = insertUpdateService;

		setupQueryInputPrototypes();
	}

    @Override
    public void dispose() {
		LOG.info("Disposing connector...");

		closeWsClient(insertUpdateService);
		insertUpdateService = null;

		closeWsClient(queryService);
		queryService = null;

		maxResourcePageSizeStr = null;
		maxResourcePageSize = -1;
		queryInputPrototypes.clear();
        configuration = null;
		soapFaultInspector = null;
    }

	private static void closeWsClient(final Object service) {
		if (service != null) {
			final Client client = ClientProxy.getClient(service);
			if (client != null) {
				client.destroy();
			}
		}
	}

	@Override
	public void checkAlive() {
	}

	@Override
	public void test() {
		LOG.ok("test() ...");
		executeQueryById(TEST_ID);
		LOG.ok("test() finished successfully");
	}

	@Override
	public Schema schema() {
		LOG.ok("schema()");
        final SchemaBuilder schemaBuilder = new SchemaBuilder(SiebelConnector.class);
		schemaBuilder.defineObjectClass(getEmployeeInfo());
		return schemaBuilder.build();
	}

	private static ObjectClassInfo getEmployeeInfo() {
        final ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

		builder.addAttributeInfo(new AttributeInfoBuilder(Uid.NAME)
		                         .setFlags(of(NOT_CREATABLE, NOT_UPDATEABLE))
		                         .setNativeName(ATTR_ID)
		                         .build());
		builder.addAttributeInfo(new AttributeInfoBuilder(Name.NAME)
		                         .setFlags(of(REQUIRED))
		                         .setNativeName(ATTR_LOGIN_NAME)
		                         .build());
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_ALIAS,              String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_EMPLOYEE_TYPE_CODE, String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_PERSONAL_TITLE,     String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_PREFERRED_COMMUNICATION, String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_TIME_ZONE,          String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_USER_TYPE,          String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_CELL_PHONE,         String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_EMAIL_ADDR,         String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_FAX,                String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_FIRST_NAME,         String.class, F_REQUIRED));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_JOB_TITLE,          String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_LAST_NAME,          String.class, F_REQUIRED));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_PHONE,              String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_SALES_CHANNEL,      String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_PRIMARY_POSITION,    String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_SECONDARY_POSITIONS, String.class, F_MULTIVALUED_READONLY));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_PRIMARY_ORGANIZATION,    String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_SECONDARY_ORGANIZATIONS, String.class, F_MULTIVALUED));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_PRIMARY_RESPONSIBILITY,     String.class));
		builder.addAttributeInfo(AttributeInfoBuilder.build(ATTR_SECONDARY_RESPONSIBILITIES, String.class, F_MULTIVALUED));

		builder.addAttributeInfo(OperationalAttributeInfos.ENABLE);

		return builder.build();
	}

	@Override
	public org.identityconnectors.framework.common.objects.filter.FilterTranslator<Filter>
			createFilterTranslator(final ObjectClass objectClass,
			                       final OperationOptions options) {
		return new FilterTranslator();
	}

	@Override
	public void executeQuery(final ObjectClass objectClass,
	                         final Filter query,
	                         final ResultsHandler handler,
	                         final OperationOptions options) {
		if (!objectClass.equals(ObjectClass.ACCOUNT)) {
			return;
		}

		LOG.ok("executeQuery(query={0}, options={1})", query, options);
		final SWIEmployeeServicesQueryPageInput queryInput = setupQueryInput(query);
		findMatchingObjects(queryInput, handler, options);
	}

	private void findMatchingObjects(final SWIEmployeeServicesQueryPageInput queryInput,
	                                 final ResultsHandler resultsHandler,
	                                 final OperationOptions options) {
		SWIEmployeeServicesQueryPageOutput queryResponse;
		List<Employee> employees;
		Boolean moreResultsAvailable = TRUE;

		final Integer requestedPageSize = normalizePagedOption(options.getPageSize());
		final Integer requestedOffset = (requestedPageSize != null)
		                                ? normalizePagedOption(options.getPagedResultsOffset())
		                                : null;

		int resourceOffset = (requestedOffset != null)
		                     ? requestedOffset + CONNID_TO_WS_REC_NO_SHIFT
		                     : 0;

		final SearchResultLogger employeeLogger = new SearchResultLogger(LOG);

		if (requestedPageSize == null) {
			queryInput.setPageSize(maxResourcePageSizeStr);

			main:
			do {
				int employeesCount;

				queryInput.setStartRowNum(Integer.toString(resourceOffset));

				LOG.ok(" - WS input: start row = {0}, page size = {1}",
				       queryInput.getStartRowNum(),
				       queryInput.getPageSize());

				queryResponse = executeQuery(queryInput);

				employees = queryResponse.getListOfSwiemployeeio().getEmployee();
				employeesCount = employees.size();

				LOG.ok(" - WS output: count = {0}, last page = {1}",
				       employeesCount,
				       queryResponse.getLastPage());

				int handledCount = 0;
				for (Employee employee : employees) {
					boolean cont = resultsHandler.handle(createAccount(employee));
					if (!cont) {
						LOG.ok(" - the connector only accepted {0} object(s)", handledCount);
						break main;
					}
					handledCount++;
					employeeLogger.logEmployee(employee);
				}
				moreResultsAvailable = isMoreResultsAvailable(queryResponse);
				resourceOffset += employeesCount;
			} while (moreResultsAvailable == TRUE);
		} else {
			int requestedRecordsRemaining = requestedPageSize;

			main:
			do {
				int resourcePageSize = min(requestedRecordsRemaining, maxResourcePageSize);

				int employeesCount;

				queryInput.setStartRowNum(Integer.toString(resourceOffset));
				queryInput.setPageSize(Integer.toString(resourcePageSize));

				LOG.ok(" - WS input: start row = {0}, page size = {1}",
				       queryInput.getStartRowNum(),
				       queryInput.getPageSize());

				queryResponse = executeQuery(queryInput);

				employees = queryResponse.getListOfSwiemployeeio().getEmployee();
				employeesCount = employees.size();

				LOG.ok(" - WS output: count = {0}, last page = {1}",
				       employeesCount,
				       queryResponse.getLastPage());

				int handledCount = 0;
				for (Employee employee : employees) {
					boolean cont = resultsHandler.handle(createAccount(employee));
					if (!cont) {
						LOG.ok(" - the connector only accepted {0} object(s)", handledCount);
						break main;
					}
					handledCount++;
					employeeLogger.logEmployee(employee);
				}

				moreResultsAvailable = isMoreResultsAvailable(queryResponse);
				if (moreResultsAvailable != TRUE) {
					break;
				}

				resourceOffset            += employeesCount;
				requestedRecordsRemaining -= employeesCount;
			} while (requestedRecordsRemaining > 0);
		}

		employeeLogger.writeResultToLog();

		if (moreResultsAvailable == null) {   //should be TRUE or FALSE
			recordInvalidValueOfLastPage(queryResponse);
		}

		if (resultsHandler instanceof SearchResultsHandler) {
			final SearchResultsHandler handler = (SearchResultsHandler) resultsHandler;
			final SearchResult searchResult = (moreResultsAvailable == TRUE)
			                                  ? new SearchResult(null, -1, false)
			                                  : ALL_RESULTS_RETURNED;
			handler.handleResult(searchResult);
		} else {
			LOG.warn("The ResultsHandler doesn't implement interface SearchResultsHandler: {0}",
			         resultsHandler.getClass().getName());
		}
	}

	private Employee findEmployeeById(final Uid uid) {
		return findEmployeeById(uid.getUidValue());
	}

	private Employee findEmployeeById(final String id) {
		final Employee result;
		final SWIEmployeeServicesQueryPageOutput queryOutput = executeQueryById(id);
		final List<Employee> employees = queryOutput.getListOfSwiemployeeio().getEmployee();
		if (isEmpty(employees)) {
			result = null;
		} else if (employees.size() == 1) {
			result = employees.get(0);
		} else {
			LOG.error("There were multiple accounts ({1}) found when searching by Id ({0}).", id, employees.size());
			throw new IllegalStateException("Multiple accounts found by Id.");
		}
		return result;
	}

	private SWIEmployeeServicesQueryPageOutput executeQueryById(final String id) {
		final SWIEmployeeServicesQueryPageInput testQueryInput;
		testQueryInput = setupQueryInput(Filter.byId(id));
		testQueryInput.setStartRowNum(SIEBEL_WS_FIRST_ROW_NUMBER_STR);
		testQueryInput.setPageSize("1");
		return executeQuery(testQueryInput);
	}

	private SWIEmployeeServicesQueryPageOutput executeQuery(final SWIEmployeeServicesQueryPageInput queryInput) {
		try {
			return queryService.swiEmployeeServicesQueryPage(queryInput);
		} catch (WebServiceException ex) {
			handleWebServiceException(ex);
			throw ex;
		}
	}

	/**
	 * Determines whether there are (possibly) more matching results available
	 * according to the given response from Siebel.
	 * 
	 * @param  queryResponse  response from the Siebel web service
	 * @return  {@code Boolean.TRUE} if more results are (possibly) available,
	 *          {@code Boolean.FALSE} if there are no more results available,
	 *          {@code null} if the response from Siebel contained an invalid
	 *          value
	 */
	private static Boolean isMoreResultsAvailable(final SWIEmployeeServicesQueryPageOutput queryResponse) {
		final String isLastPageStr = getMoreResultsAvailableStr(queryResponse);
		if (isLastPageStr == null) {
			return null;
		}
		switch (isLastPageStr) {
			case "true":  return FALSE;
			case "false": return TRUE;
		}
		return null;
	}

	private static void recordInvalidValueOfLastPage(final SWIEmployeeServicesQueryPageOutput queryResponse) {
		LOG.warn("Unsupported value of attribute \"LastPage\" in the response: {0}",
				 getMoreResultsAvailableStr(queryResponse));
	}

	private static String getMoreResultsAvailableStr(final SWIEmployeeServicesQueryPageOutput queryResponse) {
		return queryResponse.getLastPage();
	}

	private SWIEmployeeServicesQueryPageInput setupQueryInput(final Filter query) {
		final Filter.Mode queryMode = (query == null) ? Filter.Mode.RETURN_ALL
		                                              : query.mode;
		final Pair<SWIEmployeeServicesQueryPageInput, Employee> prototype = queryInputPrototypes.get(queryMode);
		switch (queryMode) {
			case SEARCH_BY_ID:
				prototype.b.setId(query.param);
				break;
			case SEARCH_BY_LOGIN_NAME:
				prototype.b.setLoginName(query.param);
				break;
		}
		return prototype.a;
	}

	private static ConnectorObject createAccount(final Employee employee) {
		final ConnectorObjectBuilder builder = new ConnectorObjectBuilder();

		builder.setUid (employee.getId());
		builder.setName(employee.getLoginName());
		builder.addAttribute(ATTR_ALIAS,              employee.getAlias());
		builder.addAttribute(ATTR_EMPLOYEE_TYPE_CODE, employee.getEmployeeTypeCode());
		builder.addAttribute(ATTR_PERSONAL_TITLE,     employee.getPersonalTitle());
		builder.addAttribute(ATTR_PREFERRED_COMMUNICATION, employee.getPreferredCommunications());
		builder.addAttribute(ATTR_TIME_ZONE,          employee.getTimeZone());
		builder.addAttribute(ATTR_USER_TYPE,          employee.getUserType());
		builder.addAttribute(ATTR_CELL_PHONE,         employee.getCellPhone());
		builder.addAttribute(ATTR_EMAIL_ADDR,         employee.getEMailAddr());
		builder.addAttribute(ATTR_FAX,                employee.getFax());
		builder.addAttribute(ATTR_FIRST_NAME,         employee.getFirstName());
		builder.addAttribute(ATTR_JOB_TITLE,          employee.getJobTitle());
		builder.addAttribute(ATTR_LAST_NAME,          employee.getLastName());
		builder.addAttribute(ATTR_PHONE,              employee.getPhone());
		builder.addAttribute(ATTR_SALES_CHANNEL,      employee.getSalesChannel());

		POSITIONS.fillConnObjAttributes(employee, builder);
		ORGANIZATIONS.fillConnObjAttributes(employee, builder);
		RESPONSIBILITIES.fillConnObjAttributes(employee, builder);

		builder.addAttribute(OperationalAttributes.ENABLE_NAME, isEmployeeActive(employee));

		final ConnectorObject account = builder.build();
		return account;
	}

	private static Boolean isEmployeeActive(final Employee employee) {
		final String status = employee.getEmployeeStatus();

		if (status == null) {
			LOG.warn("Employee {0} has assigned no employee status.", employee.getId());
			return null;
		}

		switch (status) {
			case EMPLOYEE_ACTIVE:
				return TRUE;
			case EMPLOYEE_INACTIVE:
				return FALSE;
			default:
				LOG.warn("Invalid employee status ({1}) assigned to employee #{0}.", employee.getId(), status);
				return null;
		}
	}

	private static String employeeActiveAsString(final Boolean active) {
		final String result;
		if (active == null) {
			result = null;
		} else {
			result = active ? EMPLOYEE_ACTIVE : EMPLOYEE_INACTIVE;
		}
		return result;
	}

	/**
	 * Normalizes the page size such that it is either positive or {@code null}.
	 * 
	 * @param  pageSize  page size to be normalized (possibly {@code null})
	 * @return  the original page size if it is {@code null} or positive;
	 *          {@code null} otherwise
	 */
	private static Integer normalizePagedOption(final Integer pageSize) {
		return ((pageSize != null) && (pageSize <= 0)) ? null : pageSize;
	}

	@Override
	public Uid create(final ObjectClass objectClass,
	                  final Set<Attribute> createAttributes,
	                  final OperationOptions options) {
		if (!objectClass.equals(ObjectClass.ACCOUNT)) {
			throw new UnsupportedOperationException("This connector can only create accounts. Requested ObjectClass: " + objectClass.getObjectClassValue());
		}

		if (LOG.isOk()) {
			LOG.ok("create(attributes = {1})",
			       getAttributeNames(createAttributes));
		}

		final SiebelEmployeeInsert1Input insertInput = createInsertInput(createAttributes);
		final SiebelEmployeeInsert1Output insertOutput;
		try {
			insertOutput = insertUpdateService.siebelEmployeeInsert1(insertInput);
		} catch (WebServiceException ex) {
			if (ex instanceof SOAPFaultException) {
				handleCreateOpSoapFault((SOAPFaultException) ex);
			}
			handleWebServiceException(ex);
			throw ex;
		}
		final String id = insertOutput.getListOfEmployeeInterface().getEmployee().get(0).getId();
		return new Uid(id);
	}

	@Override
	public Uid update(final ObjectClass objectClass,
	                  final Uid uid,
	                  final Set<Attribute> replaceAttributes,
	                  final OperationOptions options) {
		if (!objectClass.equals(ObjectClass.ACCOUNT)) {
			throw new UnsupportedOperationException("This connector can only update accounts. Requested ObjectClass: " + objectClass.getObjectClassValue());
		}

		if (LOG.isOk()) {
			LOG.ok("update(uid = {0}, attributes = {1})",
			       uid.getUidValue(),
			       getAttributeNames(replaceAttributes));
		}

		final Set<String> missingAttributes = findMissingUpdateAttributes(replaceAttributes);
		final Set<Attribute> combinedReplaceAttributes;
		if (isEmpty(missingAttributes)) {
			combinedReplaceAttributes = replaceAttributes;
		} else {
			LOG.ok(" - needs to GET extra attributes: {0}", missingAttributes);
			final Employee employee = findEmployeeById(uid);
			if (employee == null) {
				throw new UnknownUidException(uid, ObjectClass.ACCOUNT);
			}
			combinedReplaceAttributes = addMissingAttributes(replaceAttributes, missingAttributes, employee);
		}

		final SiebelEmployeeUpdate1Input updateInput = createUpdateInput(uid, combinedReplaceAttributes);
		final SiebelEmployeeUpdate1Output updateOutput;
		try {
			updateOutput = insertUpdateService.siebelEmployeeUpdate1(updateInput);
		} catch (WebServiceException ex) {
			if (ex instanceof SOAPFaultException) {
				handleUpdateOpSoapFault((SOAPFaultException) ex);
			}
			handleWebServiceException(ex);
			throw ex;
		}
		final String id = updateOutput.getListOfEmployeeInterface().getEmployee().get(0).getId();
		return new Uid(id);
	}

	/**
	 * Creates a new set of attributes by combining the current attributes
	 * with new attributes created from a given {@code Employee} object.
	 * The attributes specified by the parameter are left unchanged
	 * in the resulting set.
	 *
	 * @param  currentAttributes  current set of attributes
	 * @param  namesOfMissingAttributes  names of attributes whose values are
	 *                                   to be taken from the given employee
	 * @param  employee  employee to the values of missing attributes from
	 * @return  new set of attributes
	 */
	private Set<Attribute> addMissingAttributes(final Set<Attribute> currentAttributes,
	                                            final Set<String> namesOfMissingAttributes,
	                                            final Employee employee) {
		/*
		 * Get values of missing attributes:
		 */
		final ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
		builder.setUid (employee.getId());       //necessary for builder.build()
		builder.setName(employee.getLoginName());//necessary for builder.build()
		if (containsAny(namesOfMissingAttributes, ATTR_PRIMARY_POSITION,
		                                          ATTR_SECONDARY_POSITIONS)) {
			POSITIONS.fillConnObjAttributes(employee, builder);
		}
		if (containsAny(namesOfMissingAttributes, ATTR_PRIMARY_ORGANIZATION,
		                                          ATTR_SECONDARY_ORGANIZATIONS)) {
			ORGANIZATIONS.fillConnObjAttributes(employee, builder);
		}
		if (containsAny(namesOfMissingAttributes, ATTR_PRIMARY_RESPONSIBILITY,
		                                          ATTR_SECONDARY_RESPONSIBILITIES)) {
			RESPONSIBILITIES.fillConnObjAttributes(employee, builder);
		}
		final ConnectorObject connObj = builder.build();

		/*
		 * Create the combined set of attributes:
		 */
		final Set<Attribute> result = new HashSet<>(currentAttributes);
		for (String attrName : namesOfMissingAttributes) {
			final Attribute attribute = connObj.getAttributeByName(attrName);
			if (attribute != null) {
				result.add(attribute);
			}
		}

		/*
		 * TODO:
		 * Find a solution for the case that the new primary responsibility
		 * (organization, position) is among the current secondary responsibilities
		 * (organizations, positions).
		 */

		return result;
	}

	/**
	 * Determines attributes whose values are missing for a correct update
	 * of the account.
	 * The reason why some attributes may be missing is the difference between
	 * two different representations of multi-value attributes in Siebel
	 * and in the ConnId framework.
	 * <p>For example, in Siebel, all the employee
	 * positions are considered to be a single set of positions where each
	 * position has a boolean attribute (primary/secondary). In the ConnId
	 * framework, the same set of positions is represented by two separate
	 * attributes - a single-valued attribute &quot;primary position&quot;
	 * and a multi-valued attribute &quot;secondary positions&quot;.
	 * If the user modifies the primary position attribute only,
	 * the UpdateOp operation only receives a single attribute
	 * containing the new value of attribute &quot;primary position&quot;,
	 * but it must pass an updated set of <em>all positions</em> to Siebel.
	 * In such a case, the list of secondary positions must be obtained
	 * (read from Siebel) such that it can be appended to the (updated) primary
	 * position.</p>
	 *
	 * @param  presentAttributes  attributes whose values are being updated
	 *                            and whose values are currently known
	 * @return  attributes that must be added to the given attributes
	 *          to make the update operation correct and successful
	 */
	private Set<String> findMissingUpdateAttributes(final Collection<Attribute> presentAttributes) {
		final Set<String> result = new HashSet<>();
		final PrimaryXorSecondary organizations = new PrimaryXorSecondary(ATTR_PRIMARY_ORGANIZATION,
		                                                                  ATTR_SECONDARY_ORGANIZATIONS);
		final PrimaryXorSecondary responsibilities = new PrimaryXorSecondary(ATTR_PRIMARY_RESPONSIBILITY,
		                                                                     ATTR_SECONDARY_RESPONSIBILITIES);
		for (Attribute attribute : presentAttributes) {
			final String attrName = attribute.getName();
			switch (attrName) {
				case ATTR_PRIMARY_ORGANIZATION:
				case ATTR_SECONDARY_ORGANIZATIONS:
					organizations.markPresent(attrName);
					break;
				case ATTR_PRIMARY_RESPONSIBILITY:
				case ATTR_SECONDARY_RESPONSIBILITIES:
					responsibilities.markPresent(attrName);
					break;
			}
		}
		result.add(organizations.getMissing());      //may be null
		result.add(responsibilities.getMissing());   //may be null
		result.remove(null);
		return result;
	}

	private void handleCreateOpSoapFault(final SOAPFaultException exception) {
		final SOAPFaultInfo faultInfo = soapFaultInspector.getSOAPErrorInfo(exception);
		final SOAPFaultInfo.Error error = faultInfo.getFirstError();
		if (error != null) {
			handleInvalidValue(exception, error);
			if (ERR_SYMBOL_DUPLICITE_LOGIN_NAME.equals(error.symbol)) {
				throw new AlreadyExistsException(
						error.symbol + ": " + error.msg,
						exception);
			}
		}
	}

	private void handleUpdateOpSoapFault(final SOAPFaultException exception) {
		final SOAPFaultInfo faultInfo = soapFaultInspector.getSOAPErrorInfo(exception);
		final SOAPFaultInfo.Error error = faultInfo.getFirstError();
		if (error != null) {
			handleInvalidValue(exception, error);
			if (ERR_CODE_DUPLICITE_LOGIN_NAME.equals(error.code)) {
				throw new AlreadyExistsException(error.msg, exception);
			}
		}
	}

	private void handleInvalidValue(final SOAPFaultException exception,
	                                final SOAPFaultInfo.Error error) {
		if (isCausedByInvalidValue(error)) {
			throw new InvalidAttributeValueException(error.msg, exception);
		}
	}

	private static boolean isCausedByInvalidValue(final SOAPFaultInfo.Error error) {
		if (error.code != null) {
			switch (error.code) {
				case ERR_CODE_SPACE_IN_LOGIN_NAME:
				case ERR_CODE_MISSING_LOGIN_NAME:
				case ERR_CODE_NOT_FROM_PICKLIST:
				case ERR_CODE_NOT_IN_BOUND_LIST:
				case ERR_CODE_NOT_FROM_LIST_OF_VALUES:
				case ERR_CODE_INVALID_POSITION:
				case ERR_CODE_INVALID_ORG_OR_RESP:
					return true;
			}
			return false;
		}
		if (error.symbol != null) {
			switch (error.symbol) {
				case ERR_SYMBOL_NOT_IN_BOUND_LIST:
				case ERR_SYMBOL_INVALID_POSITION:
				case ERR_SYMBOL_INVALID_ORG_OR_RESP:
					return true;
			}
			return false;
		}
		return false;
	}

	private SiebelEmployeeInsert1Input createInsertInput(final Set<Attribute> attributes) {
		final ListOfEmployeeInterface listOfEmployees = createListOfEmployee(attributes);

		final SiebelEmployeeInsert1Input input = new SiebelEmployeeInsert1Input();
		input.setListOfEmployeeInterface(listOfEmployees);
		return input;
	}

	private SiebelEmployeeUpdate1Input createUpdateInput(final Uid uid,
	                                                     final Set<Attribute> attributes) {
		final ListOfEmployeeInterface listOfEmployees = createListOfEmployee(attributes, uid);

		final SiebelEmployeeUpdate1Input input = new SiebelEmployeeUpdate1Input();
		input.setListOfEmployeeInterface(listOfEmployees);
		return input;
	}

	private ListOfEmployeeInterface createListOfEmployee(final Set<Attribute> attributes) {
		return createListOfEmployee(attributes, null);
	}

	private ListOfEmployeeInterface createListOfEmployee(final Set<Attribute> attributes,
	                                                     final Uid uid) {
		final com.siebel.xml.employee_20interface.Employee employee = createEmployee(attributes, uid);

		final ListOfEmployeeInterface listOfEmployeeInterface = new ListOfEmployeeInterface();
		listOfEmployeeInterface.getEmployee().add(employee);
		return listOfEmployeeInterface;
	}

	private com.siebel.xml.employee_20interface.Employee createEmployee(final Set<Attribute> attributes,
	                                                                    final Uid uid) {
		final com.siebel.xml.employee_20interface.Employee employee = new com.siebel.xml.employee_20interface.Employee();

		final PrimarySecondaryEmployeeAttrValue positions = new PrimarySecondaryEmployeeAttrValue();
		final PrimarySecondaryEmployeeAttrValue organizations = new PrimarySecondaryEmployeeAttrValue();
		final PrimarySecondaryEmployeeAttrValue responsibilities = new PrimarySecondaryEmployeeAttrValue();

		final Operation operation;

		if (uid == null) {
			operation = CREATE;
		} else {
			operation = UPDATE;
			employee.setId(uid.getUidValue());
		}

		for (Attribute attribute : attributes) {
			final String attrName = attribute.getName();
			if (isSpecialName(attrName)) {
				if (attrName.equals(Name.NAME)) {
					employee.setLoginName(getStringValue(attribute));
				} else if (attrName.equals(OperationalAttributes.ENABLE_NAME)) {
					employee.setEmployeeStatus(employeeActiveAsString(getBooleanValue(attribute)));
				} else {
					LOG.warn("Unsupported attribute ({0}) will not be applied to a new account.", attrName);
				}
			} else {
				switch (attrName) {
					case ATTR_SECONDARY_POSITIONS:        setSecondaryAttrValues(attribute, positions); break;
					case ATTR_SECONDARY_ORGANIZATIONS:    setSecondaryAttrValues(attribute, organizations); break;
					case ATTR_SECONDARY_RESPONSIBILITIES: setSecondaryAttrValues(attribute, responsibilities); break;
					default:
						String stringValue = getStringValue(attribute);
						if (isEmptyString(stringValue)) {
							if (operation == CREATE) {
								continue;
							} else {
								stringValue = "";   // if it was null, change it to ""
							}
						}
						switch (attrName) {
							case ATTR_ALIAS:              employee.setAlias(stringValue); break;
							case ATTR_EMPLOYEE_TYPE_CODE: employee.setEmployeeTypeCode(stringValue); break;
							case ATTR_PERSONAL_TITLE:     employee.setPersonalTitle(stringValue); break;
							case ATTR_PREFERRED_COMMUNICATION: employee.setPreferredCommunications(stringValue); break;
							case ATTR_TIME_ZONE:          employee.setTimeZoneName(stringValue); break;
							case ATTR_USER_TYPE:          employee.setUserType(stringValue); break;
							case ATTR_CELL_PHONE:         employee.setCellPhone(stringValue); break;
							case ATTR_EMAIL_ADDR:         employee.setEMailAddr(stringValue); break;
							case ATTR_FAX:                employee.setFax(stringValue); break;
							case ATTR_FIRST_NAME:         employee.setFirstName(stringValue); break;
							case ATTR_JOB_TITLE:          employee.setJobTitle(stringValue); break;
							case ATTR_LAST_NAME:          employee.setLastName(stringValue); break;
							case ATTR_PHONE:              employee.setPhone(stringValue); break;
							case ATTR_SALES_CHANNEL:      employee.setSalesChannel(stringValue); break;
							case ATTR_PRIMARY_POSITION:         positions       .setPrimary(stringValue); break;
							case ATTR_PRIMARY_ORGANIZATION:     organizations   .setPrimary(stringValue); break;
							case ATTR_PRIMARY_RESPONSIBILITY:   responsibilities.setPrimary(stringValue); break;
							default:
								LOG.warn("Unsupported attribute ({0}) will not be applied to a new account.", attrName);
						}
				}
			}
		}
		POSITIONS       .fillEmployeeProperties(positions, employee);
		ORGANIZATIONS   .fillEmployeeProperties(organizations, employee);
		RESPONSIBILITIES.fillEmployeeProperties(responsibilities, employee);
		return employee;
	}

	/**
	 * Analyses the given web service exception and handles the common causes.
	 * If the given exception is found to be caused by a common scenario
	 * (unauthorized user, request timeout), then the exception is handled
	 * by throwing an appropriate runtime exception. In other cases,
	 * no exception is thrown and no data is changed.
	 * 
	 * @param  exception  exception to be analyzed (and possibly handled)
	 * @exception  InvalidCredentialException
	 *             if the given exception was caused by invalid credentials
	 * @exception  OperationTimeoutException
	 *             if the given exception was caused by a timeout during
	 *             the network communication
	 */
	private static void handleWebServiceException(final WebServiceException exception) {
		final Throwable cause = exception.getCause();
		if (cause instanceof HTTPException) {
			final HTTPException httpException = (HTTPException) cause;
			final int responseCode = httpException.getResponseCode();
			switch (responseCode) {
				case HTTP_STATUS_UNAUTHORIZED:
					throw new InvalidCredentialException(httpException.getMessage());
				case HTTP_STATUS_REQUEST_TIMEOUT:
					throw new OperationTimeoutException(httpException.getMessage());
			}
		} else if (cause instanceof SocketTimeoutException) {
			throw new OperationTimeoutException(cause.getMessage());
		}
	}

	private static void setSecondaryAttrValues(final Attribute attribute,
	                                           final PrimarySecondaryEmployeeAttrValue attrValue) {
		final List<Object> values = attribute.getValue();
		if (!isEmpty(values)) {
			for (Object value : values) {
				final String trimmed = trimToNull(value);
				if (trimmed != null) {
					attrValue.addSecondary(trimmed);
				}
			}
		}
	}

	private static boolean isEmptyString(final String str) {
		return (str == null) || str.isEmpty();
	}

	private static String trimToNull(final Object obj) {
		if (obj == null) {
			return null;
		}
		final String string = obj.toString();
		if (string == null) {
			return null;
		}
		final String trimmed = string.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed;
	}

	/**
	 * Helper class that gets/sets values of the following attributes:
	 * <ul>
	 *     <li>primary position and secondary positions</li>
	 *     <li>primary organization and secondary organizations</li>
	 *     <li>primary responsibility and secondary responsibilities</li>
	 * </ul>
	 * The aim of this class is elimination of repetition of code
	 * (now in methods {@link #fillConnObjAttributesImpl fillAttributesImpl(&hellip;)}
	 * and {@link #fillEmployeeProperties fillEmployeeProperties(&hellip;)}).
	 *
	 * @param <P> type of attribute in the source object (<code>Employee</code>)
	 *            when employees are being read from Siebel
	 *            ({@code EmployeePosition}, {@code EmployeeOrganization},
	 *            {@code EmployeeResponsibility})
	 * @param <Q> type of attribute in the target object (<code>Employee</code>)
	 *            when employees are being written to Siebel
	 *            ({@code RelatedPosition}, {@code RelatedEmployeeOrganization},
	 *            {@code RelatedResponsibility})
	 */
	private static class PrimarySecondaryEmployeeAttribute<P, Q> {

		/* --------- reading from Siebel --------- */

		private static final String CLASSNAME_PREFIX_READING = "Employee";

		private final Method methodGetListOfEmployee;
		private final Method methodGetList;
		private final Method methodGetProp;
		private final Method methodIsPrimary;

		private final String connObjAttrPrimary;
		private final String connObjAttrSecondary;

		/* --------- writing to Siebel --------- */

		private static final String CLASSNAME_PREFIX_WRITING = "Related";

		private final Class<Q> clsWriteProperty;
		private final Class<?> clsWritePropertiesList;

		private final Method methodGetRelatedList;
		private final Method methodSetRelatedListObj;
		private final Method methodSetRelatedAttr;
		private final Method methodSetRelatedAttrPrimOrSec;

		private PrimarySecondaryEmployeeAttribute(final Class<P> readPropertyClass,
		                                          final Class<Q> writePropertyClass,
		                                          final String connObjAttrPrimary,
		                                          final String connObjAttrSecondary) throws NoSuchMethodException, ClassNotFoundException {
			/* --------- reading from Siebel --------- */
			{
				final String propertyClassName = readPropertyClass.getSimpleName();         //"EmployeePosition", "EmployeeOrganization", "EmployeeResponsibility"
				final String getListOfEmployeeMethodName = "getListOf" + propertyClassName; //"getListOfEmployeePosition", ...
				final String getListMethodName = "get" + propertyClassName;                 //"getEmployeePosition", ...

				assert propertyClassName.startsWith(CLASSNAME_PREFIX_READING);
				final String resourceAttrName = propertyClassName.substring(CLASSNAME_PREFIX_READING.length());  //"Position", "Organization", "Responsibility"
				final String getPropMethodName = "get" + resourceAttrName;   //"getPosition", "getOrganization", "getResponsibility"
				final String isPrimaryMethodName = "getIsPrimaryMVG";

				methodGetListOfEmployee = Employee.class.getDeclaredMethod(getListOfEmployeeMethodName);       // Employee.getListOfEmployeePosition() : ListOfEmployeePosition
				methodGetList = methodGetListOfEmployee.getReturnType().getDeclaredMethod(getListMethodName);  // ListOfEmployeePosition.getEmployeePosition() : List<EmployeePosition>

				methodGetProp = readPropertyClass.getDeclaredMethod(getPropMethodName);      // EmployeePosition.getPosition() : String
				methodIsPrimary = readPropertyClass.getDeclaredMethod(isPrimaryMethodName);  // EmployeePosition.getIsPrimaryMVG() : String

				this.connObjAttrPrimary = connObjAttrPrimary;
				this.connObjAttrSecondary = connObjAttrSecondary;
			}

			/* --------- writing to Siebel --------- */
			{
				final String propertyClassName = writePropertyClass.getSimpleName();  //"RelatedPosition", "RelatedEmployeeOrganization", "RelatedResponsibility"
				final String getListMethodName = "get" + propertyClassName;           //"getRelatedPosition", ...
				final String setListObjMethodName = "setListOf" + propertyClassName;  //"setListOfRelatedPosition", ...

				assert propertyClassName.startsWith(CLASSNAME_PREFIX_WRITING);
				final String resourceAttrName = propertyClassName.substring(CLASSNAME_PREFIX_WRITING.length());  //"Position", "EmployeeOrganization", "Responsibility"
				final String setPropMethodName = "set" + resourceAttrName;    //"setPosition", "setEmployeeOrganization", "setResponsibility"
				final String setIsPrimaryMethodName = "setIsPrimaryMVG";

				clsWriteProperty = writePropertyClass;                                                // RelatedPosition, ...
				clsWritePropertiesList = deriveWritePropertiesListClass(clsWriteProperty);            // ListOfRelatedPosition, ...

				methodGetRelatedList = clsWritePropertiesList.getDeclaredMethod(getListMethodName);   // ListOfRelatedPosition.getRelatedPosition()
				methodSetRelatedListObj = com.siebel.xml.employee_20interface.Employee.class
				                          .getDeclaredMethod(setListObjMethodName, clsWritePropertiesList);  // Employee.setListOfRelatedPosition(...)
				methodSetRelatedAttr          = writePropertyClass.getDeclaredMethod(setPropMethodName, String.class);      //RelatedPosition.setPosition(String)
				methodSetRelatedAttrPrimOrSec = writePropertyClass.getDeclaredMethod(setIsPrimaryMethodName, String.class); //RelatedPosition.setIsPrimaryMVG(...)
			}
		}

		/**
		 * Finds (and loads) a class that represents a list of the given
		 * properties. For example, given class {@code RelatedResponsibility},
		 * this method returns class {@code ListOfRelatedResponsibility}.
		 *
		 * @param  writePropertyClass  type of properties that the returned class should be a list of
		 * @return  class reprenting a list of the given type of properties
		 * @throws  ClassNotFoundException  if the class could not be loaded
		 */
		private static Class<?> deriveWritePropertiesListClass(final Class<?> writePropertyClass) throws ClassNotFoundException {
			final String packageName = writePropertyClass.getPackage().getName();
			final String simpleName = writePropertyClass.getSimpleName();
			final Class<?> result = Class.forName(packageName + ".ListOf" + simpleName,
			                                      false,
			                                      writePropertyClass.getClassLoader());
			return result;
		}

		void fillConnObjAttributes(final Employee employee,
		                           final ConnectorObjectBuilder objBuilder) {
			try {
				fillConnObjAttributesImpl(employee, objBuilder);
			} catch (ReflectiveOperationException ex) {
				throw new RuntimeException(ex);
			}
		}

		private void fillConnObjAttributesImpl(final Employee employee,
		                                       final ConnectorObjectBuilder objBuilder) throws ReflectiveOperationException {
			final Object listOfEmployeeProps = methodGetListOfEmployee.invoke(employee);           // Employee.getListOfEmployeePosition() : ListOfEmployeePosition
			if (listOfEmployeeProps != null) {
				final List<P> employeeProps = (List<P>) methodGetList.invoke(listOfEmployeeProps); //     ListOfEmployeePosition.getEmployeePosition() : List<EmployeePosition>
				if (!isEmpty(employeeProps)) {
					final Collection<String> secondaryProps = new ArrayList<>();
					for (P empProp : employeeProps) {
						final String prop = (String) methodGetProp.invoke(empProp);    // String position = EmployeePosition.getPosition();
						if (isYes((String) methodIsPrimary.invoke(empProp))) {         // if (isYes(employeePosition.getIsPrimaryMVG())) ...
							setConnObjPrimaryAttrValue(objBuilder, connObjAttrPrimary, prop);
						} else {
							secondaryProps.add(prop);
						}
					}
					objBuilder.addAttribute(connObjAttrSecondary, secondaryProps);
				}
			}
		}

		void setConnObjPrimaryAttrValue(final ConnectorObjectBuilder objBuilder,
		                                final String attrName,
		                                final String attrValue) {
			objBuilder.addAttribute(attrName, attrValue);
		}

		void fillEmployeeProperties(final PrimarySecondaryEmployeeAttrValue primarySecondary,
		                            final com.siebel.xml.employee_20interface.Employee employee) {
			if (PrimarySecondaryEmployeeAttrValue.isEmpty(primarySecondary)) {
				return;
			}

			try {
				fillEmployeePropertiesImpl(primarySecondary, employee);
			} catch (ReflectiveOperationException ex) {
				throw new RuntimeException(ex);
			}
		}

		private void fillEmployeePropertiesImpl(final PrimarySecondaryEmployeeAttrValue primarySecondary,
		                                        final com.siebel.xml.employee_20interface.Employee employee)
				throws ReflectiveOperationException {
			if (PrimarySecondaryEmployeeAttrValue.isEmpty(primarySecondary)) {
				return;
			}

			final Object listObj = clsWritePropertiesList.newInstance();          // ListOfRelatedPosition listObj = new ListOfRelatedPosition();
			final List<Q> list = (List<Q>) methodGetRelatedList.invoke(listObj);  // List<RelatedPosition> list = listObj.getRelatedPosition();
			final String primary = primarySecondary.getPrimary();
			if (isNotEmpty(primary)) {
				list.add(createRelatedItem(primary, true));                       //       list.add(createPosition(primary, true));
			}
			for (String secondary : primarySecondary.getSecondary()) {
				list.add(createRelatedItem(secondary, false));                    //       list.add(createPosition(secondary, false));
			}
			methodSetRelatedListObj.invoke(employee, listObj);                    // employee.setListOfRelatedPosition(listObj);
		}

		Q createRelatedItem(final String connObjAttrValue,                                  // RelatedPosition createPosition(...)
		                    final boolean isPrimary) throws ReflectiveOperationException {
			final Q relatedItem = clsWriteProperty.newInstance();                           //     RelatedPosition position = new RelatedPosition();
			methodSetRelatedAttr.invoke(relatedItem, connObjAttrValue);                     //     position.setPosition(value);
			methodSetRelatedAttrPrimOrSec.invoke(relatedItem, booleanAsString(isPrimary));  //     position.setIsPrimaryMVG(booleanAsString(primary));
			return relatedItem;                                                             //     return position;
		}

		private static boolean isYes(final String str) {
			return RESOURCE_PROP_VALUE_YES.equals(str);
		}

		static String booleanAsString(final boolean value) {
			return value ? RESOURCE_PROP_VALUE_YES : RESOURCE_PROP_VALUE_NO;
		}

	}

	/**
	 * Special helper class for attributes <em>primary position</em>
	 * and <em>secondary position</em>.
	 */
	private static final class PrimarySecondaryPosition
			extends PrimarySecondaryEmployeeAttribute<EmployeePosition, RelatedPosition> {

		public PrimarySecondaryPosition() throws NoSuchMethodException, ClassNotFoundException {
			super(EmployeePosition.class,
			      RelatedPosition.class,
			      ATTR_PRIMARY_POSITION,
			      ATTR_SECONDARY_POSITIONS);
		}

		@Override
		void fillConnObjAttributes(final Employee employee,
		                           final ConnectorObjectBuilder objBuilder) {

			/* Value of attribute "PrimaryPosition" is not set here... */
			super.fillConnObjAttributes(employee, objBuilder);

			/* ... but here: */
			final String primaryPositionId = employee.getPrimaryPositionId();
			if (isNotEmpty(primaryPositionId)) {
				objBuilder.addAttribute(ATTR_PRIMARY_POSITION, primaryPositionId);
			}
		}

		@Override
		void setConnObjPrimaryAttrValue(final ConnectorObjectBuilder objBuilder,
		                                final String attrName,
		                                final String attrValue) {
			/*
			 * This method is a no-op. The value of the connection object's
			 * attribute "PrimaryPosition" is set by method
			 * fillConnObjAttributes(...) - see above.
			 */
		}

		@Override
		RelatedPosition createRelatedItem(final String connObjAttrValue,
		                                  final boolean isPrimary) throws ReflectiveOperationException {
			final RelatedPosition position = new RelatedPosition();
			if (isPrimary) {
				position.setPositionId(connObjAttrValue);
			} else {
				position.setPosition(connObjAttrValue);
			}
			position.setIsPrimaryMVG(booleanAsString(isPrimary));
			return position;
		}

	}

	/**
	 * Represents a complex type of an employee property that consits
	 * of a primary value and a list of secondary values.
	 * It is used for properties <em>positions</em> (where there is a primary
	 * position and a list of secondary positions), <em>responsibilities</em>
	 * (a primary responsibility and a list of secondary responsibilities)
	 * and <em>employee organization</em> (a primary organization and a list of
	 * secondary organizations).
	 */
	private static final class PrimarySecondaryEmployeeAttrValue {

		private static final List<String> EMPTY_LIST = emptyList();

		private String primary;

		private List<String> secondaryList = EMPTY_LIST;

		void setPrimary(final String primary) {
			this.primary = primary;
		}

		void addSecondary(final String secondary) {
			if (secondaryList == EMPTY_LIST) {
				secondaryList = new ArrayList<>();
			}
			secondaryList.add(secondary);
		}

		String getPrimary() {
			return primary;
		}

		List<String> getSecondary() {
			return secondaryList;
		}

		boolean isEmpty() {
			return (primary == null) && (secondaryList == EMPTY_LIST);
		}

		static boolean isEmpty(final PrimarySecondaryEmployeeAttrValue primarySecondary) {
			return (primarySecondary == null) || primarySecondary.isEmpty();
		}

	}

	/**
	 * Finds whether the given set contains any of the two given elements.
	 *
	 * @param  <T>  type of the elements
	 * @param  set  set to be checked for presents of the given elements
	 * @param  one  one of the two elements
	 * @param  two  the other of the two elements
	 * @return   {@code true} if the given set does contain any of the elements,
	 *           {@code false} otherwise
	 */
	private static <T> boolean containsAny(final Set<T> set,
	                                       final T one,
	                                       final T two) {
		return set.contains(one) || set.contains(two);
	}

}
