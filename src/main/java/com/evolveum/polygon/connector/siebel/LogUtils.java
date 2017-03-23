package com.evolveum.polygon.connector.siebel;

import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributesAccessor;

/**
 *
 * @author  Marián Petráš
 */
final class LogUtils {

	private LogUtils() {}

	static Set<String> getAttributeNames(final Set<Attribute> attributes) {
		return new AttributesAccessor(attributes).listAttributeNames();
	}

}
