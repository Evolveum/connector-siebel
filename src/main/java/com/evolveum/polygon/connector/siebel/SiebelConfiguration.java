package com.evolveum.polygon.connector.siebel;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;

import com.evolveum.polygon.connector.siebel.util.Pair;

import static org.identityconnectors.common.StringUtil.isEmpty;


/**
 * Configuration of the Siebel Connector.
 *
 * @author Marián Petráš
 */
public class SiebelConfiguration extends AbstractConfiguration implements StatefulConfiguration {

	private static final String[] SUPPORTED_WS_PROTOCOLS = {"HTTP", "HTTPS"};   //must be in uppercase

	static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private static final Log LOG = Log.getLog(SiebelConfiguration.class);

    private String wsUrlString = "";
	private URL    wsUrl = null;
	private URI    wsUri = null;
    private String username = "";
    private String password = "";
    private int    maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    private int    connectTimeout = 30_000;    //milliseconds
    private int    receiveTimeout = 60_000;    //milliseconds


    @ConfigurationProperty(order = 1,
	                       displayMessageKey = "wsUrl.display",
                           groupMessageKey = "basic.group",
	                       helpMessageKey = "wsUrl.help",
	                       required = true)
    public String getWsUrl() {
        return wsUrlString;
    }

    public void setWsUrl(final String urlString) {
		wsUrlString = trim(urlString);

		final Pair<URL, URI> parsedWsAddress = parseURL(wsUrlString);
		if (parsedWsAddress != null) {
			wsUrl = parsedWsAddress.a;
			wsUri = parsedWsAddress.b;
		} else {
			wsUrl = null;
			wsUri = null;
		}
    }

	URL getParsedWsUrl() {
		return wsUrl;
	}

	URI getParsedWsUri() {
		return wsUri;
	}

	/**
	 * Parses a URL string to a URL and a URI.
	 * 
	 * @param  urlString  URL string to be parsed
	 * @return  pair containing the parsed URL in two forms - a URL and a URI;
	 *          or {@code null} if the URL could not be parsed
	 */
	private static Pair<URL, URI> parseURL(final String urlString) {
		Pair<URL, URI> result = null;
		if (urlString != null) {
			try {
				URL url = new URL(urlString);
				URI uri = new URI(urlString);
				LOG.ok("The URL is valid: {0}", urlString);
				result = new Pair(url, uri);
			} catch (MalformedURLException | URISyntaxException urlException) {
				LOG.info("The URL of the WS is INVALID: {0}", urlString);
			}
		}
		return result;
	}

    @ConfigurationProperty(order = 2,
	                       displayMessageKey = "username.display",
                           groupMessageKey = "basic.group",
	                       helpMessageKey = "username.help",
	                       required = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = (username == null) ? null : username.trim();
    }

    @ConfigurationProperty(order = 3,
	                       displayMessageKey = "password.display",
                           groupMessageKey = "basic.group",
	                       helpMessageKey = "password.help",
	                       required = true,
                           confidential = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

	@ConfigurationProperty(order = 4,
	                       displayMessageKey = "maxPageSize.display",
	                       groupMessageKey = "basic.group",
	                       helpMessageKey = "maxPageSize.help",
	                       required = true,
	                       confidential = false)
	public int getMaxPageSize() {
		return maxPageSize;
	}

	public void setMaxPageSize(int maxPageSize) {
		this.maxPageSize = maxPageSize;
	}

	@ConfigurationProperty(order = 5,
	                       displayMessageKey = "connectTimeout.display",
	                       groupMessageKey = "basic.group",
	                       helpMessageKey = "connectTimeout.help",
	                       required = true,
	                       confidential = false)
	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	@ConfigurationProperty(order = 6,
	                       displayMessageKey = "receiveTimeout.display",
	                       groupMessageKey = "basic.group",
	                       helpMessageKey = "receiveTimeout.help",
	                       required = true,
	                       confidential = false)
	public int getReceiveTimeout() {
		return receiveTimeout;
	}

	public void setReceiveTimeout(int receiveTimeout) {
		this.receiveTimeout = receiveTimeout;
	}

	@Override
	public void release() {
		wsUrlString = null;
		wsUrl = null;
		wsUri = null;
		username = null;
		password = null;
	}

    @Override
    public void validate() {
		if (isEmpty(wsUrlString)) {
			throw new ConfigurationException("WSDL is not specified.");
		}

		if (wsUrl == null) {
			throw new ConfigurationException("WSDL URL is invalid.");
		}

		if (!isSupportedProtocol(wsUrl.getProtocol())) {
			throw new ConfigurationException("Protocol of the web service URL (" + wsUrl.getProtocol() + ") is not supported.");
		}

		if (isEmpty(username)) {
			throw new ConfigurationException("Username is not specified.");
		}

		if (isEmpty(password)) {
			throw new ConfigurationException("Password is not specified.");
		}

		if (maxPageSize <= 0) {
			throw new ConfigurationException("The maximum page size must be positive.");
		}

		if (connectTimeout < 0) {
			throw new ConfigurationException("The connection timeout must be positive.");
		}

		if (receiveTimeout < 0) {
			throw new ConfigurationException("The receive timeout must be positive.");
		}
    }

	private static boolean isSupportedProtocol(String protocol) {
		protocol = protocol.toUpperCase(Locale.US);
		for (String supported : SUPPORTED_WS_PROTOCOLS) {
			if (supported.equals(protocol)) {
				return true;
			}
		}
		return false;
	}

	private static String trim(final String str) {
		return (str != null) ? str.trim() : null;
	}

}
