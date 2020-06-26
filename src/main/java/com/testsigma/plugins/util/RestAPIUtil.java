package com.testsigma.plugins.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hudson.model.BuildListener;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class RestAPIUtil {
    private Properties properties = new Properties();
    BuildListener listener = null;

    public RestAPIUtil(BuildListener listener) throws IOException {
        this.listener = listener;
        InputStream is = null;
        try {
            is = getClass().getResourceAsStream("/testsigma.properties");
            properties.load(is);

        } finally {
            if (is != null) is.close();
        }
    }

    public static boolean isNullOrEmpty(String val) {
        return (val == null || val.trim().length() == 0);
    }


    public Object getDataFromResponse(CloseableHttpResponse response, boolean isReport) throws IOException {

        if (response != null) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                String jsonString = EntityUtils.toString(response.getEntity());

                if (isReport) {
                    return jsonString;
                } else {
                    return new JsonParser().parse(jsonString).getAsJsonObject();
                }
            } else {
                listener.getLogger().println(EntityUtils.toString(response.getEntity()));
                throw new RuntimeException("Failed : HTTP error code : "
                        + response.getStatusLine().getStatusCode());
            }

        } else {
            throw new RuntimeException("Http response is null");
        }
    }

    public String startTestSuiteExecution(String testPlanId, String apiKey)
            throws IOException {
        String executionTriggerURL = properties.getProperty("testsigma.execution.trigger.restapi");
        JsonObject jsonData = new JsonObject();
        jsonData.addProperty("executionId", testPlanId);
        CloseableHttpClient httpclient = null;
        JsonObject dataObject = null;
        try {
            httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(executionTriggerURL);
            httpPost.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
            httpPost.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json; " + StandardCharsets.UTF_8);
            httpPost.setHeader(HttpHeaders.ACCEPT, "application/json; " + StandardCharsets.UTF_8);
            HttpEntity stringEntity = new StringEntity(jsonData.toString(), ContentType.APPLICATION_JSON);
            httpPost.setEntity(stringEntity);
            CloseableHttpResponse response = httpclient.execute(httpPost);
            dataObject = (JsonObject) getDataFromResponse(response, false);




        } finally {
            if (httpclient != null) httpclient.close();
        }

        listener.getLogger().println("Execution started:" + dataObject.toString());
        return dataObject.get("id").toString();
    }

    public boolean runExecutionStatusCheck(BuildListener listener, String apiKey, String runId, int maxWaitTimeInMinutes, int pollIntervalInMins)
            throws IOException, InterruptedException {
        // Safe check, if max build wait time is less than pre-defined poll interval
        pollIntervalInMins = (maxWaitTimeInMinutes < pollIntervalInMins) ? maxWaitTimeInMinutes : pollIntervalInMins;
        PrintStream consoleOut = listener.getLogger();
        String statusURL = String.format("%s/%s", properties.getProperty("testsigma.execution.trigger.restapi"), runId);
        JsonObject responseObj = null;
        int noOfPolls = maxWaitTimeInMinutes / pollIntervalInMins;
        for (int i = 1; i <= noOfPolls; i++) {
            responseObj = (JsonObject) getTestPlanExecutionStatus(statusURL, apiKey);
            String status = responseObj.get("status").toString();
            consoleOut.println("Test execution Status..." + status);
            if (status.trim().contains("STATUS_IN_PROGRESS")) {
                try {
                    Thread.sleep(pollIntervalInMins * 1000 * 60L);
                    consoleOut.println("Total time waited so far(in minutes)::" + ((i) * pollIntervalInMins));
                } catch (InterruptedException e) {
                    consoleOut.println("Thread interrupted by Jenkins..");
                    throw e;
                }
            } else {
                consoleOut.println("Test suites Execution completed");
                return true;
            }

        }

        return false;

    }

    private Object getTestPlanExecutionStatus(String statusURL, String apiKey) throws IOException {
        CloseableHttpClient httpclient = null;
        Object responseObj;
        try {
            httpclient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(statusURL);
            httpGet.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
            httpGet.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json; " + StandardCharsets.UTF_8);
            httpGet.setHeader(HttpHeaders.ACCEPT, "application/json; " + StandardCharsets.UTF_8);
            CloseableHttpResponse response = httpclient.execute(httpGet);
            responseObj = getDataFromResponse(response, false);
        } finally {
            if (httpclient != null) httpclient.close();
        }

        return responseObj;
    }


    public String getReportsFilePath(BuildListener listener, String reportsFolder, Long buildID,
                                     String reportFileName) {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (!isNullOrEmpty(reportsFolder)) {
            File givenDir = new File(reportsFolder);
            if (givenDir.exists() && givenDir.isDirectory()) {
                return String.format("%s%s%s%s%s", reportsFolder, File.separator, buildID, File.separator, reportFileName);
            }
        }
        listener.getLogger().println("System Temp dir:" + tempDir);
        return String.format("%s%s%s%s%s", tempDir, File.separator, buildID, File.separator, reportFileName);
    }

    public void saveTestReports(PrintStream logger, String apiKey, String runId, String reportsFilePath) throws IOException {
        CloseableHttpClient httpclient = null;
        Object responseObj = null;
        String reportsAPI = String.format("%s/%s", properties.getProperty("testsigma.reports.junit.restapi"), runId);
        try {
            httpclient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(reportsAPI);
            httpGet.setHeader(org.apache.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
            httpGet.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json; " + StandardCharsets.UTF_8);
            httpGet.setHeader(HttpHeaders.ACCEPT, "application/xml; " + StandardCharsets.UTF_8);
            CloseableHttpResponse response = httpclient.execute(httpGet);
            responseObj = getDataFromResponse(response, true);
            File reportsFile = new File(reportsFilePath);
            FileUtils.writeStringToFile(reportsFile, responseObj.toString());
            listener.getLogger().println("Reports saved to:"+reportsFile.getAbsolutePath());
        } finally {
            if (httpclient != null) httpclient.close();
        }

    }
}
