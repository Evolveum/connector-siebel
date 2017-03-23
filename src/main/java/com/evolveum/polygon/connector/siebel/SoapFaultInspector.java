package com.evolveum.polygon.connector.siebel;

import java.util.Objects;

import javax.xml.soap.Detail;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.identityconnectors.common.logging.Log;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.evolveum.polygon.connector.siebel.xml.NamespaceContextImpl;

import static javax.xml.xpath.XPathConstants.NODESET;
import static org.w3c.dom.Node.TEXT_NODE;

/**
 * Helper class for handling of SOAP faults.
 *
 * <p>A separate instance is needed for each instance of Siebel connector
 * because the class is not thread-safe due to use of {@code XPathExpression}.</p>
 *
 * @author  Marián Petráš
 */
final class SoapFaultInspector {

    private static final Log LOG = Log.getLog(SiebelConnector.class);

	/** namespace URI of detail elements of the Siebel SOAP fault */
	private static final String SIEBEL_FAULT_DETAIL_NS_URI = "http://www.siebel.com/ws/fault";

	/**
	 * namespace prefix used in the XPath to the error symbol
	 *
	 * @see  #SIEBEL_FAULT_ERROR_XPATH
	 */
	private static final String SIEBEL_FAULT_DETAIL_NS_PREFIX = "sf";

	/**
	 * XPath to the error symbol of the SOAP fault coming from Siebel
	 *
	 * @see  #SIEBEL_FAULT_DETAIL_NS_PREFIX
	 */
	private static final String SIEBEL_FAULT_ERROR_XPATH
			= "descendant::sf:siebdetail/sf:errorstack/sf:error";

	private static final String NODE_NAME_ERRORCODE   = "errorcode";

	private static final String NODE_NAME_ERRORSYMBOL = "errorsymbol";

	private static final String NODE_NAME_ERRORMSG    = "errormsg";


	/**
	 * XPath expression for inspection of incoming SOAP faults
	 */
	private final XPathExpression errorXpathExpr;


	/**
	 * Creates an instance of {@code SoapFaultInspector}.
	 * There should be one instance per each connector.
	 *
	 * @throws  XPathExpressionException  if the XPath used internally could
	 *                                    not be compiled (should not happen)
	 */
	SoapFaultInspector() throws XPathExpressionException {
		final XPath xPath = XPathFactory.newInstance().newXPath();
		xPath.setNamespaceContext(new NamespaceContextImpl(SIEBEL_FAULT_DETAIL_NS_PREFIX,
		                                                   SIEBEL_FAULT_DETAIL_NS_URI));
		errorXpathExpr = xPath.compile(SIEBEL_FAULT_ERROR_XPATH);
	}

	/**
	 * Inspects the given SOAP fault exception and returns detailed information
	 * about additional information contained in it.
	 *
	 * @param  exception  SOAP fault exception to be inspected
	 * @return  detailed information about the exception
	 * @exception  IllegalArgumentException  if the exception is {@code null}
	 */
	SOAPFaultInfo getSOAPErrorInfo(final SOAPFaultException exception) {
		if (exception == null) {
			throw new IllegalArgumentException("The exception is null.");
		}

		final SOAPFault fault = exception.getFault();
		final SOAPFaultInfo result = new SOAPFaultInfo(fault.getFaultString());

		final Detail detail = fault.getDetail();
		if (detail == null) {
			LOG.warn("The SOAP fault doesn't contain the detail node.");
			return result;
		}

		final NodeList errorNodes = findErrorNodes(detail);
		if (isEmpty(errorNodes)) {
			LOG.warn("The SOAP fault detail does not contain more information about the fault (<error> node(s) missing).");
			return result;
		}

		final String nsURI = errorNodes.item(0).getNamespaceURI();

		final int errNodesCount = errorNodes.getLength();
		for (int i = 0; i < errNodesCount; i++) {

			String errorCode = null;
			String errorSymbol = null;
			String errorMsg = null;

			final NodeList errSubnodes = errorNodes.item(i).getChildNodes();
			final int errSubnodesCount = errSubnodes.getLength();
			for (int j = 0; j < errSubnodesCount; j++) {
				final Node errSubnode = errSubnodes.item(j);
				if (Objects.equals(nsURI, errSubnode.getNamespaceURI())) {
					switch (errSubnode.getLocalName()) {
						case NODE_NAME_ERRORCODE:
							errorCode = getText(errSubnode);
							break;
						case NODE_NAME_ERRORSYMBOL:
							errorSymbol = getText(errSubnode);
							break;
						case NODE_NAME_ERRORMSG:
							errorMsg = getText(errSubnode);
							break;
					}
				}
			}
			result.addError(errorCode, errorSymbol, errorMsg);
		}

		return result;
	}

	private NodeList findErrorNodes(final Detail detailNode) {
		NodeList errorNodes;
		try {
			errorNodes = (NodeList) errorXpathExpr.evaluate(detailNode, NODESET);
		} catch (XPathExpressionException xPathException) {
			LOG.warn(xPathException, "Could not evaluate a SOAP fault detail.");
			errorNodes = null;
		}
		return errorNodes;
	}

	private static String getText(final Node node) {
		final Node child = node.getFirstChild();
		if ((child != null) && (child.getNodeType() == TEXT_NODE)) {
			return child.getNodeValue();
		}
		return null;
	}

	private static boolean isEmpty(final NodeList nodeList) {
		return (getCount(nodeList) == 0);
	}

	private static int getCount(final NodeList nodeList) {
		return (nodeList == null) ? 0 : nodeList.getLength();
	}

}
