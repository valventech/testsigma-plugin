package com.testsigma.plugins.jenkins;

import com.testsigma.plugins.util.RestAPIUtil;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.IOException;

public class TestsigmaExecutionBuilder extends Builder {
	@DataBoundConstructor
	public TestsigmaExecutionBuilder(String apiKey,String testPlanId, String maxWaitInMinutes,String reportsFilePath) throws IOException {
		this.testPlanId = testPlanId;
		this.maxWaitInMinutes = maxWaitInMinutes;
		this.reportsFilePath= reportsFilePath;
		this.apiKey = apiKey.trim();
	}

	private String testPlanId;
	private String maxWaitInMinutes;
    private String reportsFilePath;
    private String apiKey;


	public String getTestPlanId() {
		return testPlanId;
	}

	public void setTestPlanId(String testPlanId) {
		this.testPlanId = testPlanId;
	}

	public String getMaxWaitInMinutes() {
		return maxWaitInMinutes;
	}

	public void setMaxWaitInMinutes(String maxWaitInMinutes) {
		this.maxWaitInMinutes = maxWaitInMinutes;
	}

	public String getReportsFilePath() {
		return reportsFilePath;
	}

	public void setReportsFilePath(String reportsFilePath) {
		this.reportsFilePath = reportsFilePath;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		RestAPIUtil restUtil = new RestAPIUtil(listener);
		listener.getLogger().println("Build ID:"+build.getId());
		listener.getLogger().println("************Started Testsigma Testplan execution*************");
		if (RestAPIUtil.isNullOrEmpty(testPlanId)) {
			listener.error("Testsigma TestPlan Id cannot be empty");
		}
		Double maxWaitTime = Double.parseDouble(maxWaitInMinutes);
		int pollingInterval = Integer.parseInt(Messages.TestsigmaExecutionBuilder_DescriptorImpl_pollingInterval_inMinutes());

		listener.getLogger().println("TestPlanID:" + testPlanId);
		listener.getLogger().println("Max wait time in minutes:" + maxWaitInMinutes);
		listener.getLogger().println("Polling Interval:" + pollingInterval+" minutes");
		listener.getLogger().println("Report file path:"+reportsFilePath);
		String runId = restUtil.startTestSuiteExecution(testPlanId.trim(),apiKey);


		if (runId == null || runId.isEmpty()) {
			listener.getLogger().println("Unable to start Testsigma test plan execution.");
			return false;

		}
		// Start Execution status check
		boolean isExecutionCompleted = restUtil.runExecutionStatusCheck(listener, apiKey,runId,
				maxWaitTime.intValue(), pollingInterval);
		if(isExecutionCompleted){
          restUtil.saveTestReports(listener.getLogger(),apiKey,runId,reportsFilePath);
		}else{
			listener.getLogger().println("Test Plan execution not completed,please increase wait time " +
					"OR visit https://app.testsigma for test plan execution results.");
		}
		listener.getLogger().println("************Completed Testsigma Testsuite execution*************");
		return true;
	}



	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {

			return "Testsigma Test Plan run";
		}
		@POST
		public FormValidation doCheckTestPlanId(@QueryParameter String testPlanId) {
			if (!RestAPIUtil.isNullOrEmpty(testPlanId)) {
				return FormValidation.ok();
			}
			return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidTestPlanId());

		}
		@POST
		public FormValidation doCheckApiKey(@QueryParameter String apiKey) {
			if (!RestAPIUtil.isNullOrEmpty(apiKey)) {
				return FormValidation.ok();
			}
			return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidApikey());

		}
		@POST
		public FormValidation doCheckMaxWaitInMinutes(@QueryParameter String maxWaitInMinutes) {
			if (RestAPIUtil.isNullOrEmpty(maxWaitInMinutes)) {
				return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidNumber());
			}
			try {
				Double val = Double.parseDouble(maxWaitInMinutes);
				if (val < 0) {
					return FormValidation
							.error(Messages.TestsigmaExecutionBuilder_DescriptorImpl_enterGreaterThanZero());
				}
			} catch (Exception e) {
				return FormValidation.error(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidNumber());
			}
			return FormValidation.ok();

		}
		@POST
		public FormValidation doCheckReportsFilePath(@QueryParameter String reportsFilePath){
			if (RestAPIUtil.isNullOrEmpty(reportsFilePath)) {
				return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invaliedReportFileName());
			}
			return FormValidation.ok();
		}

	}

}
