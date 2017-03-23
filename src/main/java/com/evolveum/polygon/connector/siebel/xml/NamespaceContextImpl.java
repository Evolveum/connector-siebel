package com.evolveum.polygon.connector.siebel.xml;

import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.NULL_NS_URI;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
import static javax.xml.XMLConstants.XML_NS_PREFIX;
import static javax.xml.XMLConstants.XML_NS_URI;

/**
 * Simple implementation of interface {@code NamespaceContext} that is limited
 * to a single pair &lt;prefix, namespace-URI&gt;.
 *
 * @author  Marián Petráš
 */
public class NamespaceContextImpl implements NamespaceContext {

	private static final Iterator EMPTY_ITERATOR = emptySet().iterator();

	private final String prefix;
	private final String namespaceURI;

	/**
	 * Creates an instance that contains a single binding of the given prefix
	 * with the given namespace URI.
	 *
	 * @param  prefix  prefix to be bound with the namespace URI
	 * @param  namespaceURI  namespace URI to be bound with the prefix
	 * @exception  IllegalArgumentException
	 *             if either the prefix or the namespace URI is {@code null}
	 */
	public NamespaceContextImpl(final String prefix,
	                            final String namespaceURI) {
		if (prefix == null) {
			throw new IllegalArgumentException("The prefix is null.");
		}
		if (namespaceURI == null) {
			throw new IllegalArgumentException("The namespace URI is null.");
		}
		this.prefix = prefix;
		this.namespaceURI = namespaceURI;
	}

	@Override
	public String getNamespaceURI(final String prefix) {
		if (prefix == null) {
			throw new IllegalArgumentException("The prefix is null.");
		}
		if (prefix.equals(this.prefix)) {
			return namespaceURI;
		}
		switch (prefix) {
			case DEFAULT_NS_PREFIX:   // ""
				return NULL_NS_URI;
			case XML_NS_PREFIX:       // "xml"
				return XML_NS_URI;
			case XMLNS_ATTRIBUTE:     // "xmlns"
				return XMLNS_ATTRIBUTE_NS_URI;
		}
		return NULL_NS_URI;
	}

	@Override
	public String getPrefix(final String namespaceURI) {
		if (namespaceURI == null) {
			throw new IllegalArgumentException("The namespace URI is null.");
		}
		if (namespaceURI.equals(this.namespaceURI)) {
			return prefix;
		}
		switch (namespaceURI) {
			case NULL_NS_URI:
				return DEFAULT_NS_PREFIX;   // ""
			case XML_NS_URI:
				return XML_NS_PREFIX;       // "xml"
			case XMLNS_ATTRIBUTE_NS_URI:
				return XMLNS_ATTRIBUTE;     // "xmlns"
		}
		return null;
	}

	@Override
	public Iterator getPrefixes(final String namespaceURI) {
		if (namespaceURI == null) {
			throw new IllegalArgumentException("The namespace URI is null.");
		}
		if (namespaceURI.equals(this.namespaceURI)) {
			return singleton(prefix).iterator();
		}
		switch (namespaceURI) {
			case NULL_NS_URI:
				return singleton(DEFAULT_NS_PREFIX).iterator();   // ""
			case XML_NS_URI:
				return singleton(XML_NS_PREFIX).iterator();       // "xml"
			case XMLNS_ATTRIBUTE_NS_URI:
				return singleton(XMLNS_ATTRIBUTE).iterator();     // "xmlns"
		}
		return EMPTY_ITERATOR;
	}

}
