package com.evolveum.polygon.connector.siebel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.siebel.asi.SWIEmployeeServicesQueryPageInput;
import com.siebel.asi.SWIEmployeeServicesQueryPageOutput;
import com.siebel.asi.SWISpcEmployeeSpcService;
import com.siebel.xml.swiemployeeio.Employee;
import com.siebel.xml.swiemployeeio.ListOfSwiemployeeio;

import static java.lang.Integer.parseInt;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.testng.collections.CollectionUtils.hasElements;
import static org.testng.util.Strings.isNullOrEmpty;

/**
 *
 * @author  Marián Petráš
 */
class TestQueryService implements SWISpcEmployeeSpcService {

	private static final int MAX_PAGE_SIZE = 100;

	/**
	 * special value meaning &quot;no override&quot;
	 *
	 * @see  #setPageSizeOverride(int) setPageSizeOverride(...)
	 */
	static final int NO_OVERRIDE = -1;

	private final List<Employee> employees = new ArrayList<>();

	private final Map<String, Employee> indexById = new HashMap<>();

	private final Map<String, Employee> indexByLoginName = new HashMap<>();

	private List<QueryRecord> queryRecords;

	/**
	 * Used to emulate the (strange) behaviour that the service returns
	 * fewer employees than requested, although more are available.
	 *
	 * The default value is {@value #NO_OVERRIDE}.
	 */
	private int pageSizeOverride = NO_OVERRIDE;

	TestQueryService() {
	}

	Employee addEmployee(final String id,
	                 final String loginName) {
		final Employee employee = new Employee();
		employee.setId(id);
		employee.setLoginName(loginName);

		addEmployee(employee);

		return employee;
	}

	void addEmployee(final Employee employee) {
		employees.add(employee);
		indexById       .put(employee.getId       (), employee);
		indexByLoginName.put(employee.getLoginName(), employee);
	}

	/**
	 * Used to emulate the (strange) behaviour that the service returns
	 * fewer employees than requested, although more are available.
	 *
	 * @param  pageSizeOverride  hard limit for the number of records that the
	 *                           service returns, ignoring the page size
	 *                           requested by the client of the service,
	 *                           or {@link #NO_OVERRIDE} to disable the override
	 */
	void setPageSizeOverride(final int pageSizeOverride) {
		if ((pageSizeOverride < 1) && (pageSizeOverride != NO_OVERRIDE)) {
			throw new IllegalArgumentException("Invalid page size: " + pageSizeOverride);
		}
		if (pageSizeOverride > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
					"Requested page size (" + pageSizeOverride + ") is greater than the maximum allowed page size (" + MAX_PAGE_SIZE + ").");
		}

		this.pageSizeOverride = pageSizeOverride;
	}

	@Override
	public SWIEmployeeServicesQueryPageOutput swiEmployeeServicesQueryPage(final SWIEmployeeServicesQueryPageInput queryInput) {

		final int maxPageSize = (pageSizeOverride == NO_OVERRIDE)
								? MAX_PAGE_SIZE
								: pageSizeOverride;
		final int pageSize = min(max(1, parseInt(queryInput.getPageSize())), maxPageSize);
		final int startRowNum = parseInt(queryInput.getStartRowNum());

		String employeeId = null;
		String employeeLoginName = null;

		final ListOfSwiemployeeio listOfSWIEmployeeIO = queryInput.getListOfSwiemployeeio();
		if (listOfSWIEmployeeIO != null) {
			final List<Employee> empls = listOfSWIEmployeeIO.getEmployee();
			if (hasElements(empls)) {
				final Employee employee = empls.get(0);
				employeeId        = employee.getId();
				employeeLoginName = employee.getLoginName();
			}
		}

		final SWIEmployeeServicesQueryPageOutput result;
		if (isNullOrEmpty(employeeId) && isNullOrEmpty(employeeLoginName)) {
			final int matchingEmployeesCount = employees.size();
			final int startIndex = min(startRowNum, matchingEmployeesCount);
			final int matchingEmployeesCountSinceStartIndex = matchingEmployeesCount - startIndex;
			final int resultPageSize = min(pageSize, matchingEmployeesCountSinceStartIndex);
			final int endIndex = startIndex + resultPageSize;
			final Collection<Employee> employeesToReturn = employees.subList(startIndex, endIndex);
			final boolean lastPage = (matchingEmployeesCountSinceStartIndex <= resultPageSize);
			maybeRecordQuery(startIndex, endIndex, lastPage);
			result = createResult(employeesToReturn, lastPage);
		} else {
			final Employee employee;
			if (startRowNum > 0) {
				employee = null;
			} else if (!isNullOrEmpty(employeeId)) {
				employee = indexById.get(employeeId);
			} else if (!isNullOrEmpty(employeeLoginName)) {
				employee = indexByLoginName.get(employeeLoginName);
			} else {
				employee = null;
			}
			result = createResult(employee);
		}
		return result;
	}

	private static SWIEmployeeServicesQueryPageOutput createResult(final Collection<Employee> matchingEmployees,
	                                                               final boolean lastPage) {
		final SWIEmployeeServicesQueryPageOutput result = prepareQueryOutput();
		if (!isEmpty(matchingEmployees)) {
			result.getListOfSwiemployeeio().getEmployee().addAll(matchingEmployees);
		}
		setLastPage(result, lastPage);
		return result;
	}

	private static SWIEmployeeServicesQueryPageOutput createResult(final Employee matchingEmployee) {
		final SWIEmployeeServicesQueryPageOutput result = prepareQueryOutput();
		if (matchingEmployee != null) {
			result.getListOfSwiemployeeio().getEmployee().add(matchingEmployee);
		}
		setLastPage(result, true);
		return result;
	}

	private static SWIEmployeeServicesQueryPageOutput prepareQueryOutput() {
		final SWIEmployeeServicesQueryPageOutput result = new SWIEmployeeServicesQueryPageOutput();
		result.setListOfSwiemployeeio(new ListOfSwiemployeeio());
		return result;
	}

	private static void setLastPage(final SWIEmployeeServicesQueryPageOutput queryOutput,
	                                final boolean lastPage) {
		queryOutput.setLastPage(lastPage ? "true" : "false");
	}

	void startRecordingQueries() {
		queryRecords = new ArrayList<>();
	}

	List<QueryRecord> stopRecordingQueries() {
		final List<QueryRecord> recordedQueries = queryRecords;
		queryRecords = null;
		return recordedQueries;
	}

	List<QueryRecord> getRecordedQueries() {
		return queryRecords;
	}

	boolean isRecordingQueries() {
		return (queryRecords != null);
	}

	void maybeRecordQuery(final int startIndex,
	                      final int endIndex,
	                      final boolean wasLastPage) {
		if (isRecordingQueries()) {
			queryRecords.add(new QueryRecord(startIndex, endIndex, wasLastPage));
		}
	}

	static final class QueryRecord {

		final int startIndex;

		final int endIndex;

		final boolean wasLastPage;

		public QueryRecord(final int startIndex,
		                   final int endIndex,
		                   final boolean wasLastPage) {
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.wasLastPage = wasLastPage;
		}

		@Override
		public String toString() {
			final StringBuilder buf = new StringBuilder();
			buf.append(getClass().getSimpleName())
			   .append('(').append(startIndex).append(", ")
			               .append(endIndex).append(", ")
			               .append(wasLastPage).append(')');
			return buf.toString();
		}

		@Override
		public int hashCode() {
			int hash = 7;
			hash = 17 * hash + this.startIndex;
			hash = 17 * hash + this.endIndex;
			hash = 17 * hash + (this.wasLastPage ? 1 : 0);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final QueryRecord other = (QueryRecord) obj;
			return (other.startIndex == this.startIndex)
			    && (other.endIndex == this.endIndex)
			    && (other.wasLastPage == this.wasLastPage);
		}

	}

}
