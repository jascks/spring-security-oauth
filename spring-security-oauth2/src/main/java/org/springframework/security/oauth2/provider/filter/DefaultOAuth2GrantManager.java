package org.springframework.security.oauth2.provider.filter;

import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.oauth2.provider.OAuth2GrantManager;
import org.springframework.security.oauth2.provider.client.ClientCredentialsAuthenticationToken;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.provider.password.ClientPasswordAuthenticationToken;
import org.springframework.security.oauth2.provider.refresh.RefreshAuthenticationToken;

/**
 * Default implementation of the OAuth 2 grant manager.
 * 
 * @author Ryan Heaton
 */
public class DefaultOAuth2GrantManager implements OAuth2GrantManager {

	private String credentialsCharset = "UTF-8";

	public enum GrantType {
		authorization_code, password, client_credentials, refresh_token,
	}

	public Authentication setupAuthentication(String grantType, HttpServletRequest request) {
		try {
			GrantType type = GrantType.valueOf(grantType);
			String[] clientValues = findClientSecret(request);
			String clientId = clientValues[0];
			String clientSecret = clientValues[1];
			Set<String> scope = OAuth2Utils.parseScope(request.getParameter("scope"));
			switch (type) {
			case authorization_code:
				String authCode = request.getParameter("code");
				String state = request.getParameter("state");
				String redirectUri = request.getParameter("redirect_uri");
				return new AuthorizationCodeAuthenticationToken(clientId, clientSecret, scope, authCode, state, redirectUri);
			case password:
				String username = request.getParameter("username");
				String password = request.getParameter("password");
				return new ClientPasswordAuthenticationToken(clientId, clientSecret, scope, username, password);
			case refresh_token:
				String refreshToken = request.getParameter("refresh_token");
				return new RefreshAuthenticationToken(clientId, clientSecret, refreshToken, scope);
			case client_credentials:
				return new ClientCredentialsAuthenticationToken(clientId, clientSecret, scope);
			default:
				// todo: support absolute uri identifying an assertion format?
				return null;
			}
		} catch (IllegalArgumentException e) {
			// fall through...
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	/**
	 * Finds the client secret for the given client id and request. See the OAuth 2 spec, section 2.1.
	 * 
	 * @param request The request.
	 * @return The client secret, or null if none found in the request.
	 */
	protected String[] findClientSecret(HttpServletRequest request) throws UnsupportedEncodingException {
		String clientSecret = request.getParameter("client_secret");
		String clientId = request.getParameter("client_id");
		if (clientSecret == null) {
			Enumeration headers = request.getHeaders("Authorization");
			if (headers != null) {

				while (headers.hasMoreElements()) {

					String header = (String) headers.nextElement();

					if (header.startsWith("Basic ")) {

						byte[] base64Token = header.substring(6).trim().getBytes("UTF-8");
						String token = new String(Base64.decode(base64Token), getCredentialsCharset());

						String username = "";
						String password = "";
						int delim = token.indexOf(":");

						if (delim != -1) {
							username = token.substring(0, delim);
							password = token.substring(delim + 1);
						}

						if (clientId != null && !username.equals(clientId)) {
							continue;
						}
						clientId = username;
						clientSecret = password;
						break;

					}
				}
			}
		}
		return new String[] { clientId, clientSecret };
	}

	public String getCredentialsCharset() {
		return credentialsCharset;
	}

	public void setCredentialsCharset(String credentialsCharset) {
		if (credentialsCharset == null) {
			throw new NullPointerException("credentials charset must not be null.");
		}

		this.credentialsCharset = credentialsCharset;
	}
}
