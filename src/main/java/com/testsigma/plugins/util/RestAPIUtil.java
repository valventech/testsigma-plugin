package com.testsigma.plugins.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import hudson.model.BuildListener;

public class RestAPIUtil {
	public static final String HTTP_GET = "GET";
	public static final String HTTP_POST = "POST";
	public static final String USERNAME = "USERNAME";
	public static final String PASSWORD = "PASSWORD";
	public static final String HTTPMETHOD = "HTTPMETHOD";
	public static final String URL = "URL";
	public static final String RESPONSE = "RESPONSE";

	public static boolean isNullOrEmpty(String val) {
		return (val == null || val.trim().length() == 0);
	}

	public static Object executeRestCall(PrintStream consoleOut, String prefixUrl, String userName, String password,
			String httpMethod) throws IOException {
		CloseableHttpClient httpclient = HttpClients.custom().build();
		HttpResponse response = null;
		HttpRequestBase request = null;
		try {
			if (httpMethod.equalsIgnoreCase(HTTP_POST)) {
				request = new HttpPost(UrlEscapers.urlFragmentEscaper().escape(prefixUrl));
			} else {
				request = new HttpGet(UrlEscapers.urlFragmentEscaper().escape(prefixUrl));

			}

			String authHeader = getBasicAuthString(userName + ":" + password);

			request.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, "Basic " + authHeader);
			request.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json; " + StandardCharsets.UTF_8);// +"charset=utf-8"

			response = httpclient.execute(request);
			if (response != null) {
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					String jsonString = EntityUtils.toString(response.getEntity());
					return new Gson().fromJson(jsonString, Object.class);
				} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
					return response.getStatusLine().getStatusCode() + " UnAuthorized access " + prefixUrl;
				} else {
					String message = EntityUtils.toString(response.getEntity());
					return message;
				}

			}else {
				throw new RuntimeException("Http response is null");
			}
		} catch (MalformedURLException e) {
			throw e;
		} catch (Exception e) {
			throw e;
		} finally {
			HttpClientUtils.closeQuietly(httpclient);
		}
	}

	public static String getBasicAuthString(String s) {
		try {
			byte[] encodedAuth = Base64.encodeBase64(s.getBytes(StandardCharsets.ISO_8859_1));
			return new String(encodedAuth,StandardCharsets.ISO_8859_1);
		} catch (Exception ignore) {
			return "";
		}
	}

	public static HashMap<String, Object> parseCurlCmdAndStartExecution(BuildListener listener, String executionRestURL)
			throws IOException {

		PrintStream consoleOut = listener.getLogger();
		HashMap<String, Object> restParamsMap = parseRestURL(consoleOut, executionRestURL);
		consoleOut.println("Execution URL:" + restParamsMap.get(URL));
		consoleOut.println("Http Method:" + restParamsMap.get(HTTPMETHOD));
		consoleOut.println("userName:" + restParamsMap.get(USERNAME));
		consoleOut.println("password:" + restParamsMap.get(PASSWORD));
		Object responseObj = executeRestCall(consoleOut, (String) restParamsMap.get(URL),
				(String) restParamsMap.get(USERNAME), (String) restParamsMap.get(PASSWORD),
				(String) restParamsMap.get(HTTPMETHOD));
		restParamsMap.put(RESPONSE, responseObj);
		consoleOut.println("Rest API Output:" + responseObj);
		return restParamsMap;
	}

	public static HashMap<String, Object> runExecutionStatusCheck(BuildListener listener,
			HashMap<String, Object> execResultMap, int runID, int maxWaitTimeInMinutes, int pollIntervalInMins,String reportFilePath)
			throws IOException, InterruptedException {
		// Safe check, if max build wait time is less than pre-defined poll interval
		pollIntervalInMins = (maxWaitTimeInMinutes < pollIntervalInMins) ? maxWaitTimeInMinutes : pollIntervalInMins;
		PrintStream consoleOut = listener.getLogger();
		String execURL = (String) execResultMap.get(URL);
		String statusURL = String.format("%s/%s/status", execURL, runID);
		Object responseObj = null;
		executeRestCall(consoleOut, statusURL, (String) execResultMap.get(USERNAME),
				(String) execResultMap.get(PASSWORD), HTTP_GET);
		int noOfPolls = maxWaitTimeInMinutes / pollIntervalInMins;
		String reportURL = "";
		consoleOut.println("Execution status check URL:"+statusURL);
		for (int i = 1; i <= noOfPolls; i++) {
			responseObj = executeRestCall(consoleOut, statusURL, (String) execResultMap.get(USERNAME),
					(String) execResultMap.get(PASSWORD), HTTP_GET);
			consoleOut.println("Rest API Output:" + responseObj);
			consoleOut.println("Total time waited so far(in minutes)::" + ((i - 1) * pollIntervalInMins));
			LinkedTreeMap<?, ?> jsonResult = (LinkedTreeMap<?, ?>) responseObj;
			Double statusCode = Double.parseDouble(jsonResult.get("status").toString());
			consoleOut.println("Status::" + statusCode);
			reportURL = jsonResult.get("app_url").toString();
			if (statusCode != 2) {
				consoleOut.println("Execution completed:");
				String summary = jsonResult.get("summary").toString();
				consoleOut.println("Summary:" + summary);
				consoleOut.println("Testsigma Results URL:" + jsonResult.get("app_url").toString());
				 writeJsonToTempFile(responseObj,reportFilePath);
				consoleOut.println("Json Report In File:" + reportFilePath);
				execResultMap.put("RESULT", responseObj.toString());
				break;
			}
			try {
				Thread.sleep(pollIntervalInMins * 1000 * 60L);
			} catch (InterruptedException e) {
				consoleOut.println("Thread interrupted by Jenkins..");
				throw e;
			}
		}
        if(execResultMap.get("RESULT") == null) {
        	writeJsonToTempFile("Max wait time crossed, exiting build step.\n report_URl:"+reportURL,reportFilePath);
			consoleOut.println("Json Report In File:" + reportFilePath);
        }
		return execResultMap;
	}

	private static void writeJsonToTempFile(Object responseObj,String reportFilePath) throws IOException {
			FileUtils.writeStringToFile(new File(reportFilePath), responseObj.toString());
		
	}

	public static HashMap<String, Object> parseRestURL(PrintStream consoleOut, String executionRestURL) {
		HashMap<String, Object> restParamsMap = new HashMap<String, Object>();
		String userAndURLStr = executionRestURL.substring(executionRestURL.toUpperCase().indexOf("-U"));
		String[] userURLArr = userAndURLStr.trim().split(" ");
		String[] userPwdArr = userURLArr[1].trim().split(":");
		String httpMethSubstr = executionRestURL.substring(executionRestURL.toUpperCase().indexOf("-X"),
				executionRestURL.toUpperCase().indexOf("-H"));
		String httpMethod = httpMethSubstr.trim().split(" ")[1].trim();
		restParamsMap.put(HTTPMETHOD, httpMethod);
		restParamsMap.put(USERNAME, userPwdArr[0].trim());
		restParamsMap.put(PASSWORD, userPwdArr[1].trim());
		restParamsMap.put(URL, userURLArr[2]);
		return restParamsMap;
	}

	public static String getReportsFilePath(BuildListener listener,String reportsFolder, Long buildID,
			String reportFileName) {
		String tempDir = System.getProperty("java.io.tmpdir");
		if(!isNullOrEmpty(reportsFolder)) {
			File givenDir = new File(reportsFolder);
			if(givenDir.exists() && givenDir.isDirectory()) {
				return String.format("%s%s%s%s%s", reportsFolder,File.separator,buildID,File.separator,reportFileName);
			}
		}
		listener.getLogger().println("System Temp dir:"+tempDir);
		return String.format("%s%s%s%s%s", tempDir,File.separator,buildID,File.separator,reportFileName);
	}

}
