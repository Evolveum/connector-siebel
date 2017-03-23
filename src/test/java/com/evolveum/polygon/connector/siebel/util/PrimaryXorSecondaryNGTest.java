package com.evolveum.polygon.connector.siebel.util;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 *
 * @author Marián Petráš
 */
public class PrimaryXorSecondaryNGTest {

	private static final String PRIMARY = "alpha";

	private static final String SECONDARY = "beta";

	private PrimaryXorSecondary inst;

	@BeforeMethod
	private void beforeMethod() {
		inst = new PrimaryXorSecondary(PRIMARY, SECONDARY);
	}
	
	@Test
	public void testNonePresent() {
		assertNull(inst.getMissing());
	}
	
	@Test
	public void testPrimaryPresent() {
		inst.markPresent(PRIMARY);
		assertEquals(inst.getMissing(), SECONDARY);
	}
	
	@Test
	public void testSecondaryPresent() {
		inst.markPresent(SECONDARY);
		assertEquals(inst.getMissing(), PRIMARY);
	}
	
	@Test
	public void testBothPresent_primaryFirst() {
		inst.markPresent(PRIMARY);
		inst.markPresent(SECONDARY);
		assertNull(inst.getMissing());
	}
	
	@Test
	public void testBothPresent_secondaryFirst() {
		inst.markPresent(SECONDARY);
		inst.markPresent(PRIMARY);
		assertNull(inst.getMissing());
	}

	@Test
	public void testInvalidArg() {
		try {
			inst.markPresent("something else");
			fail("IllegalArgumentException should be thrown.");
		} catch (IllegalArgumentException ex) {
			//OK
		}
	}
	
}
