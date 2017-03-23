package com.evolveum.polygon.connector.siebel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.testng.annotations.Test;

import com.evolveum.polygon.connector.siebel.util.Pair;

import static org.testng.Assert.assertTrue;

/**
 *
 * @author Marián Petráš
 */
public class SiebelConfigurationNGTest {
	
	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_initial() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.validate();
	}
	
	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_usernameOnly() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.validate();
	}
	
	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_urlMissing() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.validate();
	}
	
	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_invalidURL() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.setWsUrl("blabla");
		cfg.validate();
	}
	
	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_fileURL() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.setWsUrl("file://foobar.txt");
		cfg.validate();
	}
	
	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_negativePageSize() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.setWsUrl("http://foo.bar.baz/someWS");
		cfg.setMaxPageSize(-5);
		cfg.validate();
	}

	@Test(expectedExceptions = ConfigurationException.class)
	public void testValidate_zeroPageSize() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.setWsUrl("http://foo.bar.baz/someWS");
		cfg.setMaxPageSize(0);
		cfg.validate();
	}

	@Test
	public void testValidate_http() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.setWsUrl("http://foo.bar.baz/someWS");
		cfg.validate();
	}
	
	@Test
	public void testValidate_https() {
		final SiebelConfiguration cfg = new SiebelConfiguration();
		cfg.setUsername("someUser");
		cfg.setPassword("1234");
		cfg.setWsUrl("https://foo.bar.baz/someWS");
		cfg.validate();
	}

	@Test
	public void testPropertyMessageKeys() {
		final ResourceBundle bundle = ResourceBundle.getBundle(SiebelConfiguration.class.getPackage().getName() + ".Messages");
		for (Map.Entry<String, Method> getterInfo : getDeclaredGetters(SiebelConfiguration.class).entrySet()) {
			final String propertyName = getterInfo.getKey();
			final Method propertyGetter = getterInfo.getValue();
			final ConfigurationProperty annotation = propertyGetter.getAnnotation(ConfigurationProperty.class);
			if (annotation != null) {
				testPropertyMessageKeys(bundle, propertyName, annotation);
			}
		}
	}

	private void testPropertyMessageKeys(final ResourceBundle bundle,
	                                     final String propertyName,
	                                     final ConfigurationProperty configurationAnnotation) {
		System.out.println("testing message keys for configuration property \"" + propertyName + '"');
		testMessageKey(bundle, propertyName, "group",   configurationAnnotation.groupMessageKey());
		testMessageKey(bundle, propertyName, "display", configurationAnnotation.displayMessageKey());
		testMessageKey(bundle, propertyName, "help",    configurationAnnotation.helpMessageKey());
	}

	private static void testMessageKey(final ResourceBundle bundle,
	                                   final String propertyName,
	                                   final String messagePurpose,
	                                   final String annotationParam) {
		final String msgKey = (annotationParam != null)
		                      ? annotationParam
		                      : propertyName + '.' + messagePurpose;
		assertTrue(bundle.containsKey(msgKey), "The message bundle doesn't contain key \"" + msgKey + "\".");
	}

	private static Map<String, Method> getDeclaredGetters(final Class<?> clazz) {
		final Map<String, Method> result = new HashMap<>();
		final Method[] declaredMethods = clazz.getDeclaredMethods();
		for (Method declaredMethod : declaredMethods) {
			final Pair<String, Method> getterInfo = analyzeGetter(declaredMethod);
			if (getterInfo != null) {
				result.put(getterInfo.a, getterInfo.b);
			}
		}
		return result;
	}

	private static Pair<String, Method> analyzeGetter(final Method method) {
		final int modifiers = method.getModifiers();
		if (Modifier.isStatic(modifiers)) {
			return null;
		}
		if (!Modifier.isPublic(modifiers)) {
			return null;
		}
		if (method.getParameterTypes().length != 0) {
			return null;
		}
		final String methodName = method.getName();
		final int methodNameLength = methodName.length();
		final int prefixLength;
		if (methodName.startsWith("is")) {
			prefixLength = 2;
		} else if (methodName.startsWith("get")) {
			prefixLength = 3;
		} else {
			return null;
		}
		if (methodNameLength == prefixLength) {
			return null;
		}
		final char firstLetterAfterPrefix = methodName.charAt(prefixLength);
		final char propNameFirstLetter = toLowercase(firstLetterAfterPrefix);
		if (propNameFirstLetter == firstLetterAfterPrefix) {
			return null;     //first letter after "is" or "get" is not uppercase
		}

		final String propertyName = (methodNameLength == prefixLength + 1)
		                            ? String.valueOf(propNameFirstLetter)
		                            : propNameFirstLetter + methodName.substring(prefixLength + 1);
		return new Pair<>(propertyName, method);
	}

	private static char toLowercase(final char ch) {
		return (char) (ch | 0x20);
	}
	
}
