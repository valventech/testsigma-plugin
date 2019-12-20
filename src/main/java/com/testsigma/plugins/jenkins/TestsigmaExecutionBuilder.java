package com.testsigma.plugins.jenkins;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.testsigma.plugins.util.RestAPIUtil;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

public class TestsigmaExecutionBuilder extends Builder {
	@DataBoundConstructor
	public TestsigmaExecutionBuilder(String userName,String password,String executionRestURL, String maxWaitInMinutes,String reportsFolder) {
		this.executionRestURL = executionRestURL;
		this.maxWaitInMinutes = maxWaitInMinutes;
		this.reportsFolder = reportsFolder;
		this.userName = userName;
		this.password = password;
	}

	private String executionRestURL;
	private String maxWaitInMinutes;
    private String reportsFolder;
    private String userName;
    private String password;
    
    
	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getReportsFolder() {
		return reportsFolder;
	}

	public void setReportsFolder(String reportsFolder) {
		this.reportsFolder = reportsFolder;
	}

	public String getMaxWaitInMinutes() {
		return maxWaitInMinutes;
	}

	public void setMaxWaitInMinutes(String maxWaitInMinutes) {
		this.maxWaitInMinutes = maxWaitInMinutes;
	}

	public String getExecutionRestURL() {
		return executionRestURL;
	}

	public void setExecutionRestURL(String executionRestURL) {
		this.executionRestURL = executionRestURL;
	}

	@Override
	public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
        
		Long buildID = Long.parseLong(build.getId()); 
		listener.getLogger().println("Build ID:"+build.getId());
		listener.getLogger().println("Build details:"+build.toString());
		
		listener.getLogger().println("************Started Testsigma Testsuite execution*************");
		if (RestAPIUtil.isNullOrEmpty(executionRestURL)) {
			listener.error("Testsigma REST API URL cannot be empty");
		}
		Double maxWaitTime = Double.parseDouble(maxWaitInMinutes);
		int pollingInterval = Integer.parseInt(Messages.TestsigmaExecutionBuilder_DescriptorImpl_pollingInterval_inMinutes());
        String reportAbsPath = RestAPIUtil.getReportsFilePath(listener,reportsFolder,buildID,Messages.TestsigmaExecutionBuilder_DescriptorImpl_reportFileName());
		
        listener.getLogger().println("Testsigma UserName:" + userName);
        listener.getLogger().println("REST API URL:" + executionRestURL);
		listener.getLogger().println("Max wait time in minutes:" + maxWaitInMinutes);
		listener.getLogger().println("Polling Interval:" + pollingInterval+" minutes");
		listener.getLogger().println("Report file path:"+reportAbsPath);
		String response = RestAPIUtil.startTestSuiteExecution(listener,
				executionRestURL.trim(),userName,password);
		
		// We should get a Run-ID as response which is a double value.If not a double
		// value, we will throw an error.
		int runID = -1;
		try {
			Double runIDFromResp = Double.parseDouble(response);
			runID = runIDFromResp.intValue();
			listener.getLogger().println("Testsigma Run-ID::" + runID);
		} catch (RuntimeException re) {
			listener.error(response);
			return false;
		}
		if (runID <= 0) {
			listener.error(response);
			return false;

		}
		// Start Execution status check
		listener.getLogger().println("Started Testsigma testsuite execution status check");
		RestAPIUtil.runExecutionStatusCheck(listener, executionRestURL,userName,password,runID,
				maxWaitTime.intValue(), pollingInterval,reportAbsPath);
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

		public FormValidation doCheckExecutionRestURL(@QueryParameter String executionRestURL) {
			if (!RestAPIUtil.isNullOrEmpty(executionRestURL)) {
				return FormValidation.ok();
			}
			return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidURL());

		}
		public FormValidation doCheckUserName(@QueryParameter String userName) {
			if (!RestAPIUtil.isNullOrEmpty(userName)) {
				return FormValidation.ok();
			}
			return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidUserName());

		}
		public FormValidation doCheckPassword(@QueryParameter String password) {
			if (!RestAPIUtil.isNullOrEmpty(password)) {
				return FormValidation.ok();
			}
			return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_invalidPassord());

		}

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
		public FormValidation doCheckReportsFolder(@QueryParameter String reportsFolder) {
			if (RestAPIUtil.isNullOrEmpty(reportsFolder)) {
				return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_defaultFolder());
			}
			File f = new File(reportsFolder);
			if(!f.exists()) {
				return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_folderNotExist());
			}else if(!f.isDirectory()){
				return FormValidation.warning(Messages.TestsigmaExecutionBuilder_DescriptorImpl_notAFolder());	
			}
			return FormValidation.ok(String.format("Report will be copied to %s%s{Build_no}%s directory", reportsFolder,File.separator,File.separator));

		}
		

	}

}
