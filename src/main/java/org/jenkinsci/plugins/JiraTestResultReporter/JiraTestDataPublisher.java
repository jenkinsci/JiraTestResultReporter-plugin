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

import com.atlassian.httpclient.api.ResponseTransformationException;
import com.atlassian.httpclient.api.UnexpectedResponseException;
import com.atlassian.jira.rest.client.api.*;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousHttpClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.atlassian.util.concurrent.Promise;
import hudson.*;
import hudson.matrix.MatrixConfiguration;
import hudson.model.*;
import hudson.plugins.junitattachments.GetTestDataMethodObject;
import hudson.tasks.junit.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.StringFields;
import org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions.FullStatus;
import org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions.JiraRestClientExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tuicu.
 */
public class JiraTestDataPublisher extends TestDataPublisher {

	public static final boolean DEBUG = false;
	
        /** Attachments obtained from junit-attachments plugin indexed by className and test method name **/
	private Map<String, Map<String, List<String>>> attachments = new HashMap<>();

    /**
     * Getter for the configured fields
     * @return a list with the configured fields
     */
    public List<AbstractFields> getConfigs() {
        return JobConfigMapping.getInstance().getConfig(getJobName());
    }

    /**
     * Getter for the default issue type
     * @return the default issue type
     */
    public long getIssueType() {
        return JobConfigMapping.getInstance().getIssueType(getJobName());
    }

    /**
     * Getter for the project key
     * @return the project key
     */
    public String getProjectKey() {
        return JobConfigMapping.getInstance().getProjectKey(getJobName());
    }

    public boolean getAutoRaiseIssue() {
        return JobConfigMapping.getInstance().getAutoRaiseIssue(getJobName());
    }

    public boolean getAutoResolveIssue() {
        return JobConfigMapping.getInstance().getAutoResolveIssue(getJobName());
    }

    public boolean getAutoUnlinkIssue() {
        return JobConfigMapping.getInstance().getAutoUnlinkIssue(getJobName());
    }
    
    /**
     * Getter for list of attachments by test method identified by its classname and name
     * @param className
     * @param name
     * @return the list of attachments
     */
    public List<String> getAttachments(String className, String name) {
        if (!this.attachments.containsKey(className)) {
            return Collections.emptyList();
        }
        Map<String, List<String>> attachmentsByClassname = this.attachments.get(className);
        if (!attachmentsByClassname.containsKey(name)) { 
            return Collections.emptyList();
        }
        return attachmentsByClassname.get(name);
    }
    
    /**
     * Getter for the project associated with this publisher
     * @return
     */
    private AbstractProject getJobName() {
        return Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
    }

    private boolean pipelineInvocation = false;
    private JobConfigMapping.JobConfigEntry jobConfig;

    private boolean isPipelineInvocation() {
        return pipelineInvocation;
    }

    private JobConfigMapping.JobConfigEntry getJobConfig() {
        return jobConfig;
    }
    
    @CheckForNull
    public boolean getAdditionalAttachments() {
        return jobConfig.getAdditionalAttachments();
    }
    
    @DataBoundSetter
    public void setAdditionalAttachments(boolean additionalAttachments) {
        JiraUtils.log(String.format("Additional attachments field configured as %s", additionalAttachments));
        this.jobConfig = new JobConfigMapping.JobConfigEntryBuilder()
                .withProjectKey(this.jobConfig.getProjectKey())
                .withIssueType(this.jobConfig.getIssueType())
                .withAutoRaiseIssues(this.jobConfig.getAutoRaiseIssue())
                .withOverrideResolvedIssues(this.jobConfig.getOverrideResolvedIssues())
                .withAutoResolveIssues(this.jobConfig.getAutoResolveIssue())
                .withAutoUnlinkIssues(this.jobConfig.getAutoUnlinkIssue())
                .withAdditionalAttachments(additionalAttachments)
                .withConfigs(this.jobConfig.getConfigs())
                .build();
    }
    

    /**
     * Constructor
     * @param configs a list with the configured fields
     * @param projectKey
     * @param issueType
     * @param autoRaiseIssue
     * @param autoResolveIssue
     * @param autoUnlinkIssue
     * @param overrideResolvedIssues
     */
	@DataBoundConstructor
	public JiraTestDataPublisher(List<AbstractFields> configs, String projectKey, String issueType,
                                 boolean autoRaiseIssue, boolean autoResolveIssue, boolean autoUnlinkIssue, boolean overrideResolvedIssues) {

        long defaultIssueType;
        try {
            defaultIssueType = Long.parseLong(issueType);
        } catch (NumberFormatException e) {
            defaultIssueType = 1L;
        }

        this.jobConfig = new JobConfigMapping.JobConfigEntryBuilder()
                .withProjectKey(projectKey)
                .withIssueType(defaultIssueType)
                .withAutoRaiseIssues(autoRaiseIssue)
                .withOverrideResolvedIssues(overrideResolvedIssues)
                .withAutoResolveIssues(autoResolveIssue)
                .withAutoUnlinkIssues(autoUnlinkIssue)
                .withConfigs(Util.fixNull(configs))
                .build();

        if (Stapler.getCurrentRequest() != null) {
            //classic job - e.g. Freestyle project, Matrix project, etc.
            AbstractProject project = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
            TestToIssueMapping.getInstance().register(project);
            JobConfigMapping.getInstance()
                    .saveConfig(project, getJobConfig());
        } else {
            //pipeline invocation
            pipelineInvocation = true;
        }
    }

    /**
     * Method invoked for contributing data to this run, see Jenkins documentation for details about arguments
     * @param run
     * @param workspace
     * @param launcher
     * @param listener
     * @param testResult
     * @return a JiraTestData object
     * @throws IOException
     * @throws InterruptedException
     */
	@Override
	public TestResultAction.Data contributeTestData(Run<?, ?> run, @Nonnull FilePath workspace, Launcher launcher,
                                                    TaskListener listener, TestResult testResult)
                                                    throws IOException, InterruptedException {
        
	EnvVars envVars = run.getEnvironment(listener);
        Job job = run.getParent();
        Job project;
        if (job instanceof MatrixConfiguration) {
            project = ((MatrixConfiguration)job).getParent();
        }
        else {
            project = job;
        }

        if(isPipelineInvocation()) {
            TestToIssueMapping.getInstance().register(project);
            JobConfigMapping.getInstance().saveConfig(project, getJobConfig());
        }

        boolean hasTestData = false;
        if(JobConfigMapping.getInstance().getOverrideResolvedIssues(project)) {
            hasTestData |= cleanJobCacheFile(listener, job, getTestCaseResults(testResult));
        }

        if(JobConfigMapping.getInstance().getAutoRaiseIssue(project)) {
            if (JobConfigMapping.getInstance().getAdditionalAttachments(project)) {
                JiraUtils.log("Obtaining junit-attachments ...");
                GetTestDataMethodObject methodObject = new GetTestDataMethodObject(run, workspace, launcher, listener, testResult);
                this.attachments = methodObject.getAttachments();
                JiraUtils.log("junit-attachments successfully retrieved");
            }
            hasTestData |= raiseIssues(listener, project, job, envVars, getTestCaseResults(testResult));
        }

        if(JobConfigMapping.getInstance().getAutoResolveIssue(project)) {
            hasTestData |= resolveIssues(listener, project, job, envVars, getTestCaseResults(testResult));
        }

        if(JobConfigMapping.getInstance().getAutoUnlinkIssue(project)) {
            hasTestData |= unlinkIssuesForPassedTests(listener, project, job, envVars, getTestCaseResults(testResult));
        }
        
        if (hasTestData) {
            // Workaround to make feasible to use the publisher in parallel executions
            if (!reportedTestDataBefore(envVars)) {
                JiraTestDataRegistry.getInstance().putJiraTestData(envVars);
                return JiraTestDataRegistry.getInstance().getJiraTestData(envVars);
            } else {
                return null;
            }
        } else {
            return null;
        }
	}

    private boolean reportedTestDataBefore(EnvVars envVars) {
        return JiraTestDataRegistry.getInstance().getJiraTestData(envVars) != null;
    }

    private boolean unlinkIssuesForPassedTests(TaskListener listener, Job project, Job job, EnvVars envVars, List<CaseResult> testCaseResults) {
        boolean unlinked = false;
        for(CaseResult test : testCaseResults) {
            if(test.isPassed() &&  TestToIssueMapping.getInstance().getTestIssueKey(job, test.getId()) != null) {
                synchronized (test.getId()) {
                    String issueKey = TestToIssueMapping.getInstance().getTestIssueKey(job, test.getId());
                    TestToIssueMapping.getInstance().removeTestToIssueMapping(job, test.getId(), issueKey);
                    unlinked = true;
                }
            }
        }
        return unlinked;
    }

    private boolean resolveIssues(TaskListener listener, Job project, Job job,
                               EnvVars envVars, List<CaseResult> testCaseResults) {

        boolean solved = false;
        try {
            for(CaseResult test : testCaseResults) {
                if(test.isPassed() && test.getPreviousResult() != null && test.getPreviousResult().isFailed()) {
                    synchronized (test.getId()) {
                        for (String issueKey: JiraUtils.searchIssueKeys(job, envVars, test)) {
                            IssueRestClient issueRestClient = getDescriptor().getRestClient().getIssueClient();
                            Issue issue = issueRestClient.getIssue(issueKey).claim();
                            boolean transitionExecuted = false;
                            for (Transition transition : issueRestClient.getTransitions(issue).claim()) {
                                if (transition.getName().toLowerCase().contains("resolve")) {
                                    issueRestClient.transition(issue, new TransitionInput(transition.getId()));
                                    transitionExecuted = true;
                                    solved = true;
                                    break;
                                }
                            }
        
                            if (!transitionExecuted) {
                                listener.getLogger().println("Could not find transition to resolve issue " + issueKey);
                            }
                        }
                    }
                }
            }
        } catch (RestClientException | ResponseTransformationException | UnexpectedResponseException e) {
            listener.error("Could not connect properly to Jira server. Please review config details\n");
            e.printStackTrace(listener.getLogger());
            solved = false;
        }
        return solved;
    }
    private boolean cleanJobCacheFile(TaskListener listener, Job job,
                                      List<CaseResult> testCaseResults) {
        boolean cleaUp = false;
        try {
            cleaUp = JiraUtils.cleanJobCacheFile(testCaseResults, job);
        } catch (RestClientException e){
            listener.error("Could not do the clean up of the JiraIssueJobConfigs.json\n");
            e.printStackTrace(listener.getLogger());
            throw e;
        }
        return cleaUp;
    }

    private boolean raiseIssues(TaskListener listener, Job project, Job job,
                             EnvVars envVars,List<CaseResult> testCaseResults) {
        boolean raised = false;
        try {
            for(CaseResult test : testCaseResults) {
                if(test.isFailed()) {
                    try {
                        JiraUtils.createIssue(job, project, envVars, test, JiraIssueTrigger.JOB, getAttachments(test.getClassName(), test.getName()));
                        raised = true;
                    } catch (RestClientException e) {
                        listener.error("Could not create issue for test " + test.getFullDisplayName() + "\n");
                        e.printStackTrace(listener.getLogger());
                        throw e;
                    }
                }
            }
        } catch (RestClientException | ResponseTransformationException | UnexpectedResponseException e) {
            listener.error("Could not connect properly to Jira server. Please review config details\n");
            e.printStackTrace(listener.getLogger());
            raised = false;
        }
        return raised;
    }

    private List<CaseResult> getTestCaseResults(TestResult testResult) {
        List<CaseResult> results = new ArrayList<CaseResult>();

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
     * @return singleton instance of the Descriptor
     */
	@Override
    public JiraTestDataPublisherDescriptor getDescriptor() {
        return (JiraTestDataPublisherDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Getter for the jira url, called from config.jelly to determine if the global configurations were done
     * @return
     */
    public String getJiraUrl() {
        return getDescriptor().getJiraUrl();
    }

    @Symbol("jiraTestResultReporter")
	@Extension
	public static class JiraTestDataPublisherDescriptor extends Descriptor<TestDataPublisher> {
        /**
         * Constructor
         * loads the serialized descriptor from the previous run
         */
        public JiraTestDataPublisherDescriptor() {
            load();
        }

        public static final String SUMMARY_FIELD_NAME = "summary";
        public static final String DESCRIPTION_FIELD_NAME = "description";

        private static final String DEFAULT_SUMMARY = "${TEST_FULL_NAME} : ${TEST_ERROR_DETAILS}";
        private static final String DEFAULT_DESCRIPTION = "${BUILD_URL}${CRLF}${TEST_STACK_TRACE}";
        public static final List<AbstractFields> templates;
        public static final StringFields DEFAULT_SUMMARY_FIELD;
        public static final StringFields DEFAULT_DESCRIPTION_FIELD;
        static{
            templates = new ArrayList<AbstractFields>();
            DEFAULT_SUMMARY_FIELD = new StringFields(SUMMARY_FIELD_NAME, "${DEFAULT_SUMMARY}");
            DEFAULT_DESCRIPTION_FIELD = new StringFields(DESCRIPTION_FIELD_NAME, "${DEFAULT_DESCRIPTION}");
            templates.add(DEFAULT_SUMMARY_FIELD);
            templates.add(DEFAULT_DESCRIPTION_FIELD);
        }

        private transient HashMap<String, FullStatus> statuses;
        private transient JiraRestClient restClient;
        private transient JiraRestClientExtension restClientExtension;
        private transient MetadataCache metadataCache = new MetadataCache();
		private URI jiraUri = null;
		private String username = null;
		private Secret password = null;
        private String defaultSummary;
        private String defaultDescription;


        public URI getJiraUri()     { return jiraUri;  }
        public String getUsername() { return username; }
        public Secret getPassword() { return password; }
        public String getJiraUrl()  { return jiraUri != null ? jiraUri.toString() : null;  }
        public JiraRestClient getRestClient() { return restClient; }

        /**
         * Getter for the summary template
         * @return
         */
        public String getDefaultSummary() {
             return defaultSummary != null && !defaultSummary.equals("") ? defaultSummary : DEFAULT_SUMMARY;
        }

        /**
         * Getter for the description template
         * @return
         */
        public String getDefaultDescription() {
            return defaultDescription != null && !defaultDescription.equals("") ? defaultDescription : DEFAULT_DESCRIPTION;
        }

        /**
         * Getter for the statuses map, contains information about status category of each status
         * @return
         */
        public HashMap<String, FullStatus> getStatusesMap() {
            return statuses;
        }

        /**
         * Getter for the cache entry
         * @param projectKey
         * @param issueType
         * @return a metadata cache entry
         */
        public MetadataCache.CacheEntry getCacheEntry(String projectKey, String issueType) {
            return metadataCache.getCacheEntry(projectKey, issueType);
        }

        /**
         * Method for resolving transient objects after deserialization. Called by the JVM.
         * See Java documentation for more details.
         * @return this object
         */
        public Object readResolve()  {
            if(jiraUri != null && username != null && password != null) {
                AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
                restClient = factory.createWithBasicHttpAuthentication(jiraUri, username, password.getPlainText());
                restClientExtension = new JiraRestClientExtension(jiraUri,
                        new AsynchronousHttpClientFactory().createClient(jiraUri, new BasicHttpAuthenticationHandler(username, password.getPlainText())));
                tryCreatingStatusToCategoryMap();
            }
            return this;
        }

        /**
         * Getter for the display name
         * @return
         */
		@Override
		public String getDisplayName() {
			return "JiraTestResultReporter";
		}

        /**
         * Method for obtaining the global configurations (global.jelly), when save/apply is clicked
         * @param req current request
         * @param json form in json format
         * @return
         * @throws FormException
         */
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {

            try {
                jiraUri  = new URI(json.getString("jiraUrl"));
            } catch (URISyntaxException e) {
                JiraUtils.logError("Invalid server URI", e);
            }


            username = json.getString("username");
			password = Secret.fromString(json.getString("password"));
            defaultSummary = json.getString("summary");
            defaultDescription = json.getString("description");

            if (json.getString("jiraUrl").equals("")
                    || json.getString("username").equals("")
                    || json.getString("password").equals("")) {
                restClient = null;
                restClientExtension = null;
                save();
                return true;
            }

            AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
            restClient = factory.createWithBasicHttpAuthentication(jiraUri, username, password.getPlainText());
            restClientExtension = new JiraRestClientExtension(jiraUri,
                    new AsynchronousHttpClientFactory().createClient(jiraUri, new BasicHttpAuthenticationHandler(username, password.getPlainText())));
            tryCreatingStatusToCategoryMap();
			save();
            return super.configure(req, json);
		}

        /**
         * method for creating the status category map, if the Jira server knows about categories
         */
        private void tryCreatingStatusToCategoryMap() {
            try {
                Iterable<FullStatus> statuses = restClientExtension.getStatuses().claim();
                HashMap<String, FullStatus> statusHashMap = new HashMap<String, FullStatus>();
                for(FullStatus status : statuses) {
                    statusHashMap.put(status.getName(), status);
                }
                this.statuses = statusHashMap;
            }
            catch (RestClientException e) {
                //status categories not available, either the server doesn't have the dark feature enabled, or
                //this version of Jira cannot be queried for this info
                JiraUtils.logWarning("Jira server does not support status categories", e);
            }
        }

        /**
         * Method for creating a new, configured JiraTestDataPublisher. Override for removing cache entries when
         * configuration is finished. Called when save/apply is clicked in the job config page
         * @param req current request
         * @param json form in json format
         * @return
         * @throws FormException
         */
        @Override
        public TestDataPublisher newInstance(StaplerRequest req, JSONObject json) throws FormException {
            String projectKey = json.getString("projectKey");
            String issueType  = json.getString("issueType");
            metadataCache.removeCacheEntry(projectKey, issueType);
            return super.newInstance(req, json);
        }

        /**
         * Validation for the global configuration, called when Validate Settings is clicked (global.jelly)
         * @param jiraUrl
         * @param username
         * @param password
         * @return
         */
        @RequirePOST
        public FormValidation doValidateGlobal(@QueryParameter String jiraUrl,
                                               @QueryParameter String username,
                                               @QueryParameter String password
                                              ) {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            String serverName;
            try {
                new URL(jiraUrl);
                URI uri = new URI(jiraUrl);
                if(uri == null)
                    return FormValidation.error("Invalid URL");
                Secret pass = Secret.fromString(password);
                // JIRA does not offer ways to validate username and password, so we try to query some server
                // metadata, to see if the configured user is authorized on this server
                AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
                JiraRestClient restClient = factory.createWithBasicHttpAuthentication(uri, username, pass.getPlainText());
                MetadataRestClient client = restClient.getMetadataClient();
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
         * @param projectKey
         * @return
         */
        public FormValidation doValidateProjectKey(@QueryParameter String projectKey) {
            if(projectKey == null || projectKey.length() == 0)
                return FormValidation.error("Invalid Project Key");

            if(getRestClient() == null)
                return FormValidation.error("No jira site configured");

            ProjectRestClient projectClient = getRestClient().getProjectClient();
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
         * Method for filling the issue type select control in the job configuration page
         * @param projectKey
         * @return
         */
        public ListBoxModel doFillIssueTypeItems(@QueryParameter String projectKey) {
            ListBoxModel m = new ListBoxModel();
            if(projectKey.equals(""))
                return m;

            ProjectRestClient projectRestClient = getRestClient().getProjectClient();
            try {
                Promise<Project> projectPromise = projectRestClient.getProject(projectKey);
                Project project = projectPromise.claim();
                OptionalIterable<IssueType> issueTypes = project.getIssueTypes();

                for(IssueType issueType : issueTypes) {
                    m.add(new ListBoxModel.Option(issueType.getName(), issueType.getId().toString(), issueType.getName() == "Bug"));
                }
            }
            catch (Exception e) {
                JiraUtils.logError("ERROR: Unknown error", e);
                return m;
            }
            return m;
        }

        /**
         * Ugly hack (part 2, see config.jelly for part1) for validating the configured values for fields.
         * This method will try to create an issue using the configured fields and delete it afterwards.
         * @param jsonForm
         * @return
         * @throws FormException
         * @throws InterruptedException
         */
        @JavaScriptMethod
        public FormValidation validateFieldConfigs(String jsonForm) throws FormException, InterruptedException {
            // extracting the configurations for associated with this plugin (we receive the entire form)
            StaplerRequest req = Stapler.getCurrentRequest();
            JSONObject jsonObject = JSONObject.fromObject(jsonForm);
            JSONObject publishers = jsonObject.getJSONObject("publisher");
            JSONObject jiraPublisherJSON = null;

            for(Object o : publishers.keySet()) {
                if(o.toString().contains(JiraTestDataPublisher.class.getSimpleName())) {
                    jiraPublisherJSON = (JSONObject) publishers.get(o);
                    break;
                }
            }

            // constructing the objects from json
            List<AbstractFields> configs = newInstancesFromHeteroList(req, jiraPublisherJSON.get("configs"), getListDescriptors());
            if(configs == null) {
                //nothing to validate
                return FormValidation.ok("OK!");
            }
            String projectKey = jiraPublisherJSON.getString("projectKey");
            Long issueType  = jiraPublisherJSON.getLong("issueType");

            // trying to create the issue
            final IssueRestClient issueClient = getRestClient().getIssueClient();
            final IssueInputBuilder newIssueBuilder = new IssueInputBuilder(projectKey, issueType);
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

            //if the issue was created successfully, try to delete it
            try {
                restClientExtension.deteleIssue(newCreatedIssue.getKey()).claim();
            } catch (RestClientException e) {
                JiraUtils.logError("Error when deleting issue", e);
                return FormValidation.warning(JiraUtils.getErrorMessage(e, "\n"));
            }

            return FormValidation.ok("OK!");
        }

        /**
         * Getter for the descriptors required for the hetero-list in job config page (config.jelly)
         * @return
         */
        public List getListDescriptors() {
            return Jenkins.getInstance().getDescriptorList(AbstractFields.class);
        }
    }
}
