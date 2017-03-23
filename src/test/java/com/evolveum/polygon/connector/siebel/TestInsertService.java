package com.evolveum.polygon.connector.siebel;

import java.util.Iterator;
import java.util.List;

import com.siebel.customui.SiebelEmployeeInsert1Input;
import com.siebel.customui.SiebelEmployeeInsert1Output;
import com.siebel.customui.SiebelEmployeeUpdate1Input;
import com.siebel.customui.SiebelEmployeeUpdate1Output;
import com.siebel.customui.SiebelSpcEmployee;
import com.siebel.xml.employee_20interface.Employee;
import com.siebel.xml.employee_20interface.ListOfEmployeeInterface;

/**
 *
 * @author Marián Petráš
 */
class TestInsertService implements SiebelSpcEmployee {

	TestInsertService() {
	}

	private List<Employee> insertedEmployees;

	@Override
	public SiebelEmployeeInsert1Output siebelEmployeeInsert1(final SiebelEmployeeInsert1Input input) {
		if (input == null) {
			throw new IllegalArgumentException("The input is null.");
		}

		final ListOfEmployeeInterface inputListOfEmployeeObj = input.getListOfEmployeeInterface();
		if (inputListOfEmployeeObj == null) {
			throw new IllegalArgumentException("The input does not contain an instance of class ListOfEmployeeInterface.");
		}

		insertedEmployees = inputListOfEmployeeObj.getEmployee();

		final int employeeCount = insertedEmployees.size();

		final ListOfEmployeeInterface outputListOfEmployeeObj = new ListOfEmployeeInterface();
		for (int i = 0; i < employeeCount; i++) {
			final Employee outputEmployee = new Employee();
			outputEmployee.setId(Integer.toString(i));
			outputListOfEmployeeObj.getEmployee().add(outputEmployee);
		}
		final SiebelEmployeeInsert1Output output = new SiebelEmployeeInsert1Output();
		output.setListOfEmployeeInterface(outputListOfEmployeeObj);

		return output;
	}

	List<Employee> getInsertedEmployees() {
		return insertedEmployees;
	}

	Employee getInsertedEmployee() {
		final Iterator<Employee> iterator = insertedEmployees.iterator();
		if (!iterator.hasNext()) {
			throw new IllegalStateException("There was no employee inserted.");
		}
		final Employee first = iterator.next();
		if (iterator.hasNext()) {
			throw new IllegalStateException("There were multiple employees inserted (" + insertedEmployees.size() + ").");
		}
		return first;
	}

	@Override
	public SiebelEmployeeUpdate1Output siebelEmployeeUpdate1(final SiebelEmployeeUpdate1Input input) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
