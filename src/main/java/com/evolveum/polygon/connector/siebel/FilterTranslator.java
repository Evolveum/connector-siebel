package com.evolveum.polygon.connector.siebel;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.framework.common.objects.filter.StringFilter;

import static com.evolveum.polygon.connector.siebel.Filter.Mode.RETURN_ALL;

/**
 *
 * @author  Marián Petráš
 */
public class FilterTranslator extends AbstractFilterTranslator<Filter> {

	private static enum StringExpressionType {
		STARTS_WITH,
		ENDS_WITH,
		CONTAINS,
		;
	}

	@Override
	protected Filter createEqualsExpression(final EqualsFilter filter,
	                                        final boolean not) {
		final Filter result;
		if (not) {
			result = null;
		} else {
			final Attribute attr = filter.getAttribute();
			if (Filter.isSupportedAttribute(attr)) {
				result = Filter.byIdOrLoginName(filter.getName(), AttributeUtil.getStringValue(attr));
			} else {
				result = null;
			}
		}
		return result;
	}

	@Override
	protected Filter createStartsWithExpression(final StartsWithFilter filter,
	                                            final boolean not) {
		return createSimpleSearchExpression(filter, StringExpressionType.STARTS_WITH, not);
	}

	@Override
	protected Filter createEndsWithExpression(final EndsWithFilter filter,
	                                          final boolean not) {
		return createSimpleSearchExpression(filter, StringExpressionType.ENDS_WITH, not);
	}

	@Override
	protected Filter createContainsExpression(final ContainsFilter filter,
	                                          final boolean not) {
		return createSimpleSearchExpression(filter, StringExpressionType.CONTAINS, not);
	}

	private Filter createSimpleSearchExpression(final StringFilter filter,
	                                            final StringExpressionType expressionType,
	                                            final boolean not) {
		Filter result;
		if (not) {
			result = null;
		} else {
			final String attrName = filter.getName();
			final String attrValueExpression = createStringExpression(filter.getValue(), expressionType);
			result = Filter.byIdOrLoginName(attrName, attrValueExpression);
		}
		return result;
	}

	private static String createStringExpression(final String fixedPart,
	                                             final StringExpressionType exprType) {
		switch (exprType) {
			case STARTS_WITH: return       fixedPart + '*';
			case ENDS_WITH:   return '*' + fixedPart;
			case CONTAINS:    return '*' + fixedPart + '*';
			default:
				throw new IllegalArgumentException("Unsupported expression type: " + exprType);
		}
	}

	@Override
	protected Filter createAndExpression(final Filter leftExpression,
	                                     final Filter rightExpression) {
		final Filter result;
		if (leftExpression.mode == RETURN_ALL) {
			result = rightExpression;
		} else if (rightExpression.mode == RETURN_ALL) {
			result = leftExpression;
		} else {
			result = null;
		}
		return result;
	}

	@Override
	protected Filter createOrExpression(final Filter leftExpression,
	                                    final Filter rightExpression) {
		final Filter result;
		if (leftExpression.mode == RETURN_ALL) {
			result = leftExpression;
		} else if (rightExpression.mode == RETURN_ALL) {
			result = rightExpression;
		} else {
			result = null;
		}
		return result;
	}

}
