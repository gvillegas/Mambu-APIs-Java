package com.mambu.apisdk.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;


import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.mambu.apisdk.MambuAPIFactory;
import com.mambu.apisdk.exception.MambuApiException;

/**
 * Implementation of executing url requests with basic authorization
 * 
 * @author edanilkis
 * 
 */
@Singleton
public class RequestExecutorImpl implements RequestExecutor {

	private URLHelper urlHelper;
	private String encodedAuthorization;
	private String charset = "UTF-8";

	private final String APPLICATION_KEY = "appkey"; // as per JIRA issue MBU-3236

	private final static Logger LOGGER = Logger.getLogger(RequestExecutorImpl.class.getName());

	@Inject
	public RequestExecutorImpl(URLHelper urlHelper) {
		this.urlHelper = urlHelper;
	}

	@Override
	public String executeRequest(String urlString, Method method) throws MambuApiException {
		return executeRequest(urlString, null, method);
	}

	@Override
	public String executeRequest(String urlString, ParamsMap params, Method method) throws MambuApiException {
		// Add 'Application Key', if it was set by the application
		// Mambu may handle API requests differently for different Application Keys

		String applicationKey = MambuAPIFactory.getApplicationKey();
		if (applicationKey != null) {
			// add application key to the params map
			if (params == null)
				params = new ParamsMap();
			params.addParam(APPLICATION_KEY, applicationKey);
			LOGGER.info("Added Application key=" + applicationKey);
		}

		String response = "";
		try {
			switch (method) {
			case GET:
				response = executeGetRequest(urlString, params);
				break;
			case POST:
				response = executePostRequest(urlString, params);
				break;
			}
		} catch (MalformedURLException e) {
			LOGGER.severe("MalformedURLException: " + e.getMessage());
			throw new MambuApiException(e);
		} catch (IOException e) {
			LOGGER.warning("IOException: message= " + e.getMessage());
			throw new MambuApiException(e);
		}
		return response;
	}

	/**
	 * Executes a POST request as per the interface specification
	 */
	private String executePostRequest(String urlString, ParamsMap params) throws MalformedURLException, IOException,
			MambuApiException {

		String response = "";
		Integer errorCode = null;

		HttpClient httpClient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(urlString);
		httpPost.addHeader("Authorization", "Basic " + encodedAuthorization);

		LOGGER.info(" Input Params string:" + params.getURLString());

		if (params != null && params.size() > 0) {
			// convert parms to a list for HttpEntity
			List<NameValuePair> httpParams = getListFromParams(params);
			HttpEntity postEntity = new UrlEncodedFormEntity(httpParams);
			httpPost.setEntity(postEntity);

		}

		// execute
		HttpResponse httpResponse = httpClient.execute(httpPost);
		// get status code
		int status = httpResponse.getStatusLine().getStatusCode();

		InputStream content = null;
		HttpEntity entity = httpResponse.getEntity();

		// if there is an entity - get content
		if (entity != null) {
			content = entity.getContent();
			if (content != null)
				response = readStream(content);
		}

		if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_CREATED) {

			errorCode = status;
			LOGGER.info("Error status=" + status + " Error response=" + response);
		}

		if (errorCode == null)
			// For logging only: log successful response
			LOGGER.info("Status=" + status + "\nResponse=" + response);

		// check if we hit an error
		if (errorCode != null) {
			//  pass to MambuApiException the content that goes with the error code
			LOGGER.warning("Creating exception, error code=" + errorCode + " response=" + response);
			throw new MambuApiException(errorCode, response);
		}

		return response;
	}

	/***
	 * Execute a GET request as per the interface specification
	 * 
	 * @param urlString
	 */
	private String executeGetRequest(String urlString, ParamsMap params) throws MalformedURLException, IOException,
			MambuApiException {
		String response = "";
		Integer errorCode = null;

		if (params != null && params.size() > 0) {
			urlString = urlHelper.createUrlWithParams(urlString, params);
		}
		LOGGER.info("Url string with params from Helper=" + urlString);

		HttpClient httpClient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(urlString);
		// add Authorozation header
		httpGet.addHeader("Authorization", "Basic " + encodedAuthorization);
		// execute
		HttpResponse httpResponse = httpClient.execute(httpGet);
		// get status
		int status = httpResponse.getStatusLine().getStatusCode();

		InputStream content = null;
		// Get the response Entity
		HttpEntity entity = httpResponse.getEntity();
		if (entity != null) {
			content = entity.getContent();
			if (content != null)
				response = readStream(content);
		}
		// if status is not Ok - set error code
		if (status != HttpURLConnection.HTTP_OK) {
			errorCode = status;
			LOGGER.info("Error status=" + status + " Error response=" + response);
		}

		if (errorCode == null)
			// For logging only: log successful response
			LOGGER.info("Status=" + status + "\nResponse=" + response);

		// check if we hit an error
		if (errorCode != null) {
			//  pass to MambuApiException the content that goes with the error code
			LOGGER.warning("Creating exception, error code=" + errorCode + " response=" + response);
			throw new MambuApiException(errorCode, response);
		}

		return response;
	}

	/**
	 * Reads a stream into a String
	 * 
	 * @param content
	 * @return
	 * @throws IOException
	 */
	private String readStream(InputStream content) throws IOException {
		String response = "";
		// read the response content
		BufferedReader in = new BufferedReader(new InputStreamReader(content));
		String line;
		while ((line = in.readLine()) != null) {
			response += line;
		}
		return response;
	}

	@Override
	public void setAuthorization(String username, String password) {
		// encode the username and password
		String userNamePassword = username + ":" + password;
		encodedAuthorization = new String(Base64.encodeBase64(userNamePassword.getBytes()));

	}

	/**
	 * Convert Params Map into a List<NameValuePair> for HttpPpost
	 * 
	 * @param params
	 * @return List<NameValuePair>
	 * @throws
	 */
	private List<NameValuePair> getListFromParams(ParamsMap params) {

		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(params.size());

		for (Map.Entry<String, String> entry : params.entrySet()) {
			// only put the parameter in the URL if its value is not null
			if (entry.getValue() != null) {
				nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));

			}
		}
		return nameValuePairs;
	}

}