/**
 Copyright 2015 Andrei Tuicu

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import com.atlassian.jira.rest.client.api.*;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousHttpClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;

import hudson.*;
import hudson.matrix.MatrixConfiguration;
import hudson.model.*;
import hudson.model.AbstractProject;
import hudson.tasks.junit.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.StringFields;
import org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions.FullStatus;
import org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions.JiraRestClientExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tuicu.
 */
public class JiraTestDataPublisher extends TestDataPublisher {

	public static final boolean DEBUG = false;
	
	

	/**
	 * Getter for the configured fields
	 * 
	 * @return a list with the configured fields
	 */
	public List<AbstractFields> getConfigs() {
		return JobConfigMapping.getInstance().getConfig(getJobName());
	}

	/**
	 * Getter for the default issue type
	 * 
	 * @return the default issue type
	 */
	public long getIssueType() {
		return JobConfigMapping.getInstance().getIssueType(getJobName());
	}

	/**
	 * Getter for the project key
	 * 
	 * @return the project key
	 */
	public String getProjectKey() {
		return JobConfigMapping.getInstance().getProjectKey(getJobName());
	}

        /**
         * Getter for auto raise issue
         * @return true if enabled, false otherwise.
         */
	public boolean getAutoRaiseIssue() {
		return JobConfigMapping.getInstance().getAutoRaiseIssue(getJobName());
	}

        /**
         * Getter for auto resolve issue.
         * @return true if enabled, false otherwise.
         */
	public boolean getAutoResolveIssue() {
		return JobConfigMapping.getInstance().getAutoResolveIssue(getJobName());
	}

        /**
         * Getter for prevent duplicate issue
         * @return true if enabled, false otherwise.
         */
	public boolean getPreventDuplicateIssue() {
		return JobConfigMapping.getInstance().getPreventDuplicateIssue(
				getJobName());
	}

	/**
         * Getter for max number of bugs.
         * @return the max number of bugs as a String.
         */
	public String getMaxNoOfBugs() {
		return JobConfigMapping.getInstance().getMaxNoOfBugs(getJobName());
	}
	
	/**
	 * Getter for the project associated with this publisher
	 * 
	 * @return the project.
	 */
	private AbstractProject getJobName() {
		return Stapler.getCurrentRequest().findAncestorObject(
				AbstractProject.class);
	}

	/**
	 * Constructor
	 * 
	 * @param configs
	 *            a list with the configured fields
	 * @param projectKey the project key.
	 * @param issueType the issuetype.
         * @param autoRaiseIssue true to auto raise issues.
         * @param autoResolveIssue true to auto resolve issues.
         * @param preventDuplicateIssue true to prevent duplicates
         * @param maxNoOfBugs the max number of bugs to submit in a day.
	 */
	@DataBoundConstructor
	public JiraTestDataPublisher(List<AbstractFields> configs,
			String projectKey, String issueType, boolean autoRaiseIssue,
			boolean autoResolveIssue, boolean preventDuplicateIssue,String maxNoOfBugs) {
		AbstractProject project = Stapler.getCurrentRequest()
				.findAncestorObject(AbstractProject.class);
		TestToIssueMapping.getInstance().register(project);
		long defaultIssueType;
		try {
			defaultIssueType = Long.parseLong(issueType);
		} catch (NumberFormatException e) {
			defaultIssueType = 1L;
		}
		JobConfigMapping.getInstance().saveConfig(project, projectKey,
				defaultIssueType, Util.fixNull(configs), autoRaiseIssue,
				autoResolveIssue, preventDuplicateIssue,maxNoOfBugs);
	}

	/**
	 * Method invoked for contributing data to this run, see Jenkins
	 * documentation for details about arguments
	 * 
	 * @param run run
	 * @param workspace workspace
	 * @param launcher launcher
	 * @param listener listener
	 * @param testResult the TestResult object
	 * @return a JiraTestData object
	 * @throws IOException error
	 * @throws InterruptedException error
	 */
	@Override
	public TestResultAction.Data contributeTestData(Run<?, ?> run,
			@Nonnull FilePath workspace, Launcher launcher,
			TaskListener listener, TestResult testResult) throws IOException,
			InterruptedException {
		EnvVars envVars = run.getEnvironment(listener);

		Job job = run.getParent();
		AbstractProject project;
		if (job instanceof MatrixConfiguration) {
			project = ((MatrixConfiguration) job).getParent();
		} else {
			project = (AbstractProject) job;
		}

		if (JobConfigMapping.getInstance().getAutoRaiseIssue(project)) {
			raiseIssues(listener, project, job, envVars,
					getTestCaseResults(testResult));
		}

		if (JobConfigMapping.getInstance().getAutoResolveIssue(project)) {
			resolveIssues(listener, project, job, envVars,
					getTestCaseResults(testResult));
		}
		return new JiraTestData(envVars);
	}

	private void resolveIssues(TaskListener listener, AbstractProject project,
			Job job, EnvVars envVars, List<CaseResult> testCaseResults) {

		for (CaseResult test : testCaseResults) {
			if (test.isPassed()
					&& test.getPreviousResult() != null
					&& test.getPreviousResult().isFailed()
					&& TestToIssueMapping.getInstance().getTestIssueKey(job,
							test.getId()) != null) {
				synchronized (test.getId()) {
					String issueKey = TestToIssueMapping.getInstance()
							.getTestIssueKey(job, test.getId());
					IssueRestClient issueRestClient = getDescriptor()
							.getRestClient().getIssueClient();
					Issue issue = issueRestClient.getIssue(issueKey).claim();
					boolean transitionExecuted = false;
					for (Transition transition : issueRestClient
							.getTransitions(issue).claim()) {
						if (transition.getName().toLowerCase()
								.contains("resolve")) {
							issueRestClient.transition(issue,
									new TransitionInput(transition.getId()));
							transitionExecuted = true;
							break;
						}
					}

					if (!transitionExecuted) {
						listener.getLogger().println(
								"Could not find transition to resolve issue "
										+ issueKey);
					}

				}
			}
		}
	}

	private void raiseIssues(TaskListener listener, AbstractProject project,
			Job job, EnvVars envVars, List<CaseResult> testCaseResults) {
		for (CaseResult test : testCaseResults) {
			if (test.isFailed()&& TestToIssueMapping.getInstance().getTestIssueKey(job,
					test.getId()) == null) {
				synchronized (test.getId()) { // avoid creating duplicated
												// issues
					if (TestToIssueMapping.getInstance().getTestIssueKey(job,
							test.getId()) != null) {
                                                listener.getLogger().println("Ignoring creating issue as it would be a duplicate. (from local cache)");
						continue;
					}
					try {
						boolean maxBugsForDay = false;
						String maxBugs =JobConfigMapping.getInstance().getMaxNoOfBugs(project);
								
						if(maxBugs!=null && !maxBugs.isEmpty())	
								{  
							        try{
							          
							            int maxNoBugs = Integer.parseInt(maxBugs);
									    int bugs = JiraUtils.bugsPerDay(project, test,JiraUtils.getJiraDescriptor().getUsername());
		                                if(bugs >= maxNoBugs)
										maxBugsForDay = true;
									}catch(NumberFormatException e){
										maxBugsForDay = false;
								    }									
								}
						
							if(maxBugsForDay){
									listener.getLogger().println("Max Number of Bugs already logged for the day : " + maxBugs +" hence ignoring creating issue");}
                            else{
                            	   boolean foundDuplicate = false;
								      if (JobConfigMapping.getInstance().getPreventDuplicateIssue(project)) {
										  SearchResult searchResult = JiraUtils.findIssues(project, test, envVars);
										  			if (searchResult != null) {
															for (Issue issue : searchResult.getIssues()) {
																foundDuplicate = true;
																listener.getLogger()
																		.println("Duplicate Issue which currently exists:" +issue.getKey());
																
															}
								                          }
							                           }
	
							if (foundDuplicate) {listener.getLogger().println("Ignoring creating issue as it would be a duplicate. (from JIRA server)");}
							else {
								String issueKey = JiraUtils.createIssueInput(project, test, envVars);
								TestToIssueMapping.getInstance().addTestToIssueMapping(job, test.getId(),issueKey);
								listener.getLogger().println(
										"Created issue " + issueKey + " for test "
												+ test.getFullDisplayName());
							  }
						}
					} catch (RestClientException e) {
							listener.error("Could not create issue for test "
									+ test.getFullDisplayName() + "\n");
							e.printStackTrace(listener.getLogger());
				   }
				}
			}
		}
	}

	
	
	private List<CaseResult> getTestCaseResults(TestResult testResult) {
		List<CaseResult> results = new ArrayList<>();

		Collection<PackageResult> packageResults = testResult.getChildren();
		for (PackageResult pkgResult : packageResults) {
			Collection<ClassResult> classResults = pkgResult.getChildren();
			for (ClassResult cr : classResults) {
				results.addAll(cr.getChildren());
			}
		}

		return results;
	}

	/**
	 * Getter for the Descriptor
	 * 
	 * @return singleton instance of the Descriptor
	 */
	@Override
	public JiraTestDataPublisherDescriptor getDescriptor() {
		return (JiraTestDataPublisherDescriptor) Jenkins.getInstance()
				.getDescriptorOrDie(getClass());
	}

	/**
	 * Getter for the jira url, called from config.jelly to determine if the
	 * global configurations were done
	 * 
	 * @return the Jira URL
	 */
	public String getJiraUrl() {
		return getDescriptor().getJiraUrl();
	}

	@Extension
	public static class JiraTestDataPublisherDescriptor extends
			Descriptor<TestDataPublisher> {
		/**
		 * Constructor loads the serialized descriptor from the previous run
		 */
		public JiraTestDataPublisherDescriptor() {
			load();
		}

		private static final String DEFAULT_SUMMARY = "${TEST_FULL_NAME} : ${TEST_ERROR_DETAILS}";
		private static final String DEFAULT_DESCRIPTION = "${BUILD_URL}${CRLF}${TEST_STACK_TRACE}";
		public static final List<AbstractFields> TEMPLATES;
		static {
			TEMPLATES = new ArrayList<>();
			TEMPLATES.add(new StringFields("summary", "${DEFAULT_SUMMARY}"));
			TEMPLATES.add(new StringFields("description",
					"${DEFAULT_DESCRIPTION}"));
		}

		private transient Map<String, FullStatus> statuses;
		private transient JiraRestClient restClient;
		private transient JiraRestClientExtension restClientExtension;
		private final transient MetadataCache metadataCache = new MetadataCache();
		private URI jiraUri = null;
		private String username = null;
		private Secret password = null;
		private String defaultSummary;
		private String defaultDescription;

		public URI getJiraUri() {
			return jiraUri;
		}

		public String getUsername() {
			return username;
		}
		

		public Secret getPassword() {
			return password;
		}

		public String getJiraUrl() {
			return jiraUri != null ? jiraUri.toString() : null;
		}

		public JiraRestClient getRestClient() {
			return restClient;
		}

		/**
		 * Getter for the summary template
		 * 
		 * @return summary template
		 */
		public String getDefaultSummary() {
			return defaultSummary != null && !defaultSummary.equals("") ? defaultSummary
					: DEFAULT_SUMMARY;
		}

		/**
		 * Getter for the description template
		 * 
		 * @return description template
		 */
		public String getDefaultDescription() {
			return defaultDescription != null && !defaultDescription.equals("") ? defaultDescription
					: DEFAULT_DESCRIPTION;
		}

		/**
		 * Getter for the statuses map, contains information about status
		 * category of each status
		 * 
		 * @return the the Map of statuses
		 */
		public Map<String, FullStatus> getStatusesMap() {
			return statuses;
		}		

		/**
		 * Getter for the cache entry
		 * 
		 * @param projectKey projectKey
		 * @param issueType issueType
		 * @return a metadata cache entry
		 */
		public MetadataCache.CacheEntry getCacheEntry(String projectKey,
				String issueType) {
			return metadataCache.getCacheEntry(projectKey, issueType);
		}

		/**
		 * Method for resolving transient objects after deserialization. Called
		 * by the JVM. See Java documentation for more details.
		 * 
		 * @return this object
		 */
		public Object readResolve() {
			if (jiraUri != null && username != null && password != null) {
				AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
				restClient = factory.createWithBasicHttpAuthentication(jiraUri,
						username, password.getPlainText());
				restClientExtension = new JiraRestClientExtension(jiraUri,
						new AsynchronousHttpClientFactory().createClient(
								jiraUri, new BasicHttpAuthenticationHandler(
										username, password.getPlainText())));
				tryCreatingStatusToCategoryMap();
			}
			return this;
		}

		/**
		 * Getter for the display name
		 * 
		 * @return the display name of JiraTestResultReporter.
		 */
		@Override
		public String getDisplayName() {
			return "JiraTestResultReporter";
		}

		/**
		 * Method for obtaining the global configurations (global.jelly), when
		 * save/apply is clicked
		 * 
		 * @param req
		 *            current request
		 * @param json
		 *            form in json format
		 * @return true if successful
		 * @throws FormException error
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {

			if (json.getString("jiraUrl").equals("")
					|| json.getString("username").equals("")
					|| json.getString("password").equals("")) {
				return false;
			}

			try {
				jiraUri = new URI(json.getString("jiraUrl"));
			} catch (URISyntaxException e) {
				JiraUtils.logError("Invalid server URI", e);
			}

			username = json.getString("username");
			password = Secret.fromString(json.getString("password"));

			AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
			restClient = factory.createWithBasicHttpAuthentication(jiraUri,
					username, password.getPlainText());
			restClientExtension = new JiraRestClientExtension(jiraUri,
					new AsynchronousHttpClientFactory().createClient(jiraUri,
							new BasicHttpAuthenticationHandler(username,
									password.getPlainText())));
			defaultSummary = json.getString("summary");
			defaultDescription = json.getString("description");
			tryCreatingStatusToCategoryMap();
			save();
			return super.configure(req, json);
		}

		/**
		 * method for creating the status category map, if the Jira server knows
		 * about categories
		 */
		private void tryCreatingStatusToCategoryMap() {
			try {
				Iterable<FullStatus> currStatuses = restClientExtension
						.getStatuses().claim();
				Map<String, FullStatus> statusHashMap = new HashMap<>();
				for (FullStatus status : currStatuses) {
					statusHashMap.put(status.getName(), status);
				}
				this.statuses = statusHashMap;
			} catch (RestClientException e) {
				// status categories not available, either the server doesn't
				// have the dark feature enabled, or
				// this version of Jira cannot be queried for this info
				JiraUtils.logWarning(
						"Jira server does not support status categories", e);
			}
		}

		/**
		 * Method for creating a new, configured JiraTestDataPublisher. Override
		 * for removing cache entries when configuration is finished. Called
		 * when save/apply is clicked in the job config page
		 * 
		 * @param req
		 *            current request
		 * @param json
		 *            form in json format
		 * @return the TestDataPlublisher
		 * @throws FormException error
		 */
		@Override
		public TestDataPublisher newInstance(StaplerRequest req, JSONObject json)
				throws FormException {
			String projectKey = json.getString("projectKey");
			String issueType = json.getString("issueType");
			metadataCache.removeCacheEntry(projectKey, issueType);
			return super.newInstance(req, json);
		}

		/**
		 * Validation for the global configuration, called when Validate
		 * Settings is clicked (global.jelly)
		 * 
		 * @param jiraUrl the JIRA Url
		 * @param username the username
		 * @param password the password
		 * @return FormValidation
		 */
		public FormValidation doValidateGlobal(@QueryParameter String jiraUrl,
				@QueryParameter String username, @QueryParameter String password) {

			String serverName;
			try {
				new URL(jiraUrl);
				URI uri = new URI(jiraUrl);
				Secret pass = Secret.fromString(password);
				// JIRA does not offer ways to validate username and password,
				// so we try to query some server
				// metadata, to see if the configured user is authorized on this
				// server
				AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
				JiraRestClient localRestClient = factory
						.createWithBasicHttpAuthentication(uri, username,
								pass.getPlainText());
				MetadataRestClient client = localRestClient.getMetadataClient();
				Promise<ServerInfo> serverInfoPromise = client.getServerInfo();
				ServerInfo serverInfo = serverInfoPromise.claim();
				serverName = serverInfo.getServerTitle();
			} catch (MalformedURLException e) {
				return FormValidation.error("Invalid URL");
			} catch (URISyntaxException e) {
				return FormValidation.error("Invalid URL");
			} catch (RestClientException e) {
				JiraUtils.logError("ERROR: Unknown error", e);
				return FormValidation.error("ERROR " + e.getStatusCode().get());
			} catch (Exception e) {
				JiraUtils.logError("ERROR: Unknown error", e);
				return FormValidation.error("ERROR Unknown: " + e.getMessage());
			}

			return FormValidation.ok(serverName);
		}

		/**
		 * Validation for the project key
		 * 
		 * @param projectKey projectKey
		 * @return FormValidation
		 */
		public FormValidation doValidateProjectKey(
				@QueryParameter String projectKey) {
			if (projectKey == null || projectKey.length() == 0)
				return FormValidation.error("Invalid Project Key");

			if (getRestClient() == null)
				return FormValidation.error("No jira site configured");

			ProjectRestClient projectClient = getRestClient()
					.getProjectClient();
			Project project;
			try {
				project = projectClient.getProject(projectKey).claim();
				if (project == null)
					return FormValidation.error("Invalid Project Key");
			} catch (RestClientException e) {
				JiraUtils.logWarning("Invalid Project Key", e);
				return FormValidation.error("Invalid Project Key");
			}
			return FormValidation.ok(project.getName());
		}

		/**
		 * Method for filling the issue type select control in the job
		 * configuration page
		 * 
		 * @param projectKey projectKey
		 * @return ListBoxModel
		 */
		public ListBoxModel doFillIssueTypeItems(
				@QueryParameter String projectKey) {
			ListBoxModel m = new ListBoxModel();
			if (projectKey.equals(""))
				return m;

			ProjectRestClient projectRestClient = getRestClient()
					.getProjectClient();
			try {
				Promise<Project> projectPromise = projectRestClient
						.getProject(projectKey);
				Project project = projectPromise.claim();
				OptionalIterable<IssueType> issueTypes = project
						.getIssueTypes();

				for (IssueType issueType : issueTypes) {
					m.add(new ListBoxModel.Option(issueType.getName(),
							issueType.getId().toString(),
							issueType.getName().equals("Bug")));
				}
			} catch (Exception e) {
				JiraUtils.logError("ERROR: Unknown error", e);
				return m;
			}
			return m;
		}

		/**
		 * Ugly hack (part 2, see config.jelly for part1) for validating the
		 * configured values for fields. This method will try to create an issue
		 * using the configured fields and delete it afterwards.
		 * 
		 * @param jsonForm jsonForm
		 * @return FormValidation
		 * @throws FormException error
		 * @throws InterruptedException error
		 */
		@JavaScriptMethod
		public FormValidation validateFieldConfigs(String jsonForm)
				throws FormException, InterruptedException {
			// extracting the configurations for associated with this plugin (we
			// receive the entire form)
			StaplerRequest req = Stapler.getCurrentRequest();
			JSONObject jsonObject = JSONObject.fromObject(jsonForm);
			JSONObject publishers = jsonObject.getJSONObject("publisher");
			JSONObject jiraPublisherJSON = null;

			for (Object o : publishers.keySet()) {
				if (o.toString().contains(
						JiraTestDataPublisher.class.getSimpleName())) {
					jiraPublisherJSON = (JSONObject) publishers.get(o);
					break;
				}
			}

			// constructing the objects from json
			List<AbstractFields> configs = newInstancesFromHeteroList(req,
					jiraPublisherJSON.get("configs"), getListDescriptors());
			if (configs == null) {
				// nothing to validate
				return FormValidation.ok("OK!");
			}
			String projectKey = jiraPublisherJSON.getString("projectKey");
			Long issueType = jiraPublisherJSON.getLong("issueType");

			// trying to create the issue
			final IssueRestClient issueClient = getRestClient()
					.getIssueClient();
			final IssueInputBuilder newIssueBuilder = new IssueInputBuilder(
					projectKey, issueType);
			newIssueBuilder.setSummary("Test summary");
			newIssueBuilder.setDescription("Test Description");
			for (AbstractFields f : configs) {
				newIssueBuilder.setFieldInput(f.getFieldInput(null, null));
			}

			BasicIssue newCreatedIssue;
			try {
				IssueInput newIssue = newIssueBuilder.build();
				newCreatedIssue = issueClient.createIssue(newIssue).claim();
			} catch (RestClientException e) {
				JiraUtils.logError("Error when creating issue", e);
				return FormValidation.error(JiraUtils.getErrorMessage(e, "\n"));
			}

			// if the issue was created successfully, try to delete it
			try {
				restClientExtension.deteleIssue(newCreatedIssue.getKey())
						.claim();
			} catch (RestClientException e) {
				JiraUtils.logError("Error when deleting issue", e);
				return FormValidation.warning(JiraUtils
						.getErrorMessage(e, "\n"));
			}

			return FormValidation.ok("OK!");
		}
		

		/**
		 * Getter for the descriptors required for the hetero-list in job config
		 * page (config.jelly)
		 * 
		 * @return List of descriptors
		 */
		public List getListDescriptors() {
			return Jenkins.getInstance()
					.getDescriptorList(AbstractFields.class);
		}
	}
}
