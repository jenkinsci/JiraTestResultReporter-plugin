package JiraTestResultReporter;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.AbstractHttpClient;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import javax.json.*;
import javax.json.stream.*;

public class JiraReporter extends Notifier {

    public String projectKey;
    public String serverAddress;
    public String username;
    public String password;

    public boolean debugFlag;
    public boolean verboseDebugFlag;
    public boolean createAllFlag;

    private transient FilePath workspace;

    private static final int JIRA_SUCCESS_CODE = 201;

    private static final String PluginName = new String("[JiraTestResultReporter]");
    private final String pInfo = String.format("%s [INFO]", PluginName);
    private final String pDebug = String.format("%s [DEBUG]", PluginName);
    private final String pVerbose = String.format("%s [DEBUGVERBOSE]", PluginName);
    private final String prefixError = String.format("%s [ERROR]", PluginName);

    @DataBoundConstructor
    public JiraReporter(String projectKey,
                        String serverAddress,
                        String username,
                        String password,
                        boolean createAllFlag,
                        boolean debugFlag,
                        boolean verboseDebugFlag) {
        if (serverAddress.endsWith("/")) {
            this.serverAddress = serverAddress;
        } else {
            this.serverAddress = serverAddress + "/";
        }

        this.projectKey = projectKey;
        this.username = username;
        this.password = password;

        this.verboseDebugFlag = verboseDebugFlag;
        if (verboseDebugFlag) {
            this.debugFlag = true;
        } else {
            this.debugFlag = debugFlag;
        }
        
        this.createAllFlag = createAllFlag;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Override
    public boolean perform(final AbstractBuild build,
                           final Launcher launcher,
                           final BuildListener listener) {
        PrintStream logger = listener.getLogger();
        logger.printf("%s Examining test results...%n", pInfo);
        debugLog(listener,
                 String.format("Build result is %s%n",
                    build.getResult().toString())
                );
        this.workspace = build.getWorkspace();
        debugLog(listener,
                 String.format("%s Workspace is %s%n", pInfo, this.workspace.toString())
                );
        TestResultAction testResultAction = (TestResultAction)build.getAction(AbstractTestResultAction.class);
        if (testResultAction == null) {
            logger.printf("%s no test results found; nothing to do.%n", pInfo);
        }
        else {
            List<CaseResult> failedTests = testResultAction.getFailedTests();
            printResultItems(failedTests, listener);
            createJiraIssue(failedTests, build, listener);
            logger.printf("%s Done.%n", pInfo);
        }
        return true;
    }

    private void printResultItems(final List<CaseResult> failedTests,
                                  final BuildListener listener) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream out = listener.getLogger();
        for (CaseResult result : failedTests) {
            out.printf("%s projectKey: %s%n", pDebug, this.projectKey);
            out.printf("%s errorDetails: %s%n", pDebug, result.getErrorDetails());
            out.printf("%s fullName: %s%n", pDebug, result.getFullName());
            out.printf("%s simpleName: %s%n", pDebug, result.getSimpleName());
            out.printf("%s title: %s%n", pDebug, result.getTitle());
            out.printf("%s packageName: %s%n", pDebug, result.getPackageName());
            out.printf("%s name: %s%n", pDebug, result.getName());
            out.printf("%s className: %s%n", pDebug, result.getClassName());
            out.printf("%s failedSince: %d%n", pDebug, result.getFailedSince());
            out.printf("%s status: %s%n", pDebug, result.getStatus().toString());
            out.printf("%s age: %s%n", pDebug, result.getAge());
            out.printf("%s ErrorStackTrace: %s%n", pDebug, result.getErrorStackTrace());

            String affectedFile = result.getErrorStackTrace().replace(this.workspace.toString(), "");
            out.printf("%s affectedFile: %s%n", pDebug, affectedFile);
            out.printf("%s ----------------------------%n", pDebug);
        }
    }

    void debugLog(final BuildListener listener, final String message) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream logger = listener.getLogger();
        logger.printf("%s %s%n", pDebug, message);
    }

     void createJiraIssue(final List<CaseResult> failedTests,
                          final AbstractBuild build,
                          final BuildListener listener) {
        PrintStream logger = listener.getLogger();
        String url = this.serverAddress + "rest/api/2/issue/";

        for (CaseResult result : failedTests) {
            if ((result.getAge() == 1) || (this.createAllFlag)) {
//          if (result.getAge() > 0) {
                debugLog(listener,
                         String.format("Creating issue in project %s at URL %s%n",
                            this.projectKey, url)
                        );
                try {
                    DefaultHttpClient httpClient = new DefaultHttpClient();
                    Credentials creds = new UsernamePasswordCredentials(this.username, this.password);
                    ((AbstractHttpClient) httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY, creds);

                    HttpPost postRequest = new HttpPost(url);
                    String summary = "Test " + result.getName() + " failed";
                    String description = "Test class: " + result.getClassName() + "\n\n" +
                                         "Jenkins job: " + build.getAbsoluteUrl() + "\n\n" +
                                         "{noformat}\n" + result.getErrorDetails() + "\n{noformat}\n\n" +
                                         "{noformat}\n" + result.getErrorStackTrace().replace(this.workspace.toString(), "") + "\n{noformat}\n\n";
                    JsonObjectBuilder issuetype = Json.createObjectBuilder().add("name", "Bug");
                    JsonObjectBuilder project = Json.createObjectBuilder().add("key", this.projectKey);
                    JsonObjectBuilder fields = Json.createObjectBuilder().add("project", project)
                                                                  .add("summary", summary)
                                                                  .add("description", description)
                                                                  .add("issuetype", issuetype);
                    JsonObjectBuilder payload = Json.createObjectBuilder().add("fields", fields);
                    StringWriter stWriter = new StringWriter();
                    JsonWriter jsonWriter = Json.createWriter(stWriter);
                    jsonWriter.writeObject(payload.build());
                    jsonWriter.close();
                    String jsonPayLoad = stWriter.toString();
//                     logger.printf("%s JSON payload: %n", pVerbose, jsonPayLoad);
                    logger.printf("%s Reporting issue.%n", pInfo);
                    StringEntity params = new StringEntity(jsonPayLoad);
                    params.setContentType("application/json");
                    postRequest.setEntity(params);
                    try {
                        postRequest.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials(this.username, this.password), postRequest));
                    } catch (AuthenticationException a) {
                        a.printStackTrace();
                    }

                    HttpResponse response = httpClient.execute(postRequest);
                    debugLog(listener,
                             String.format("statusLine: %s%n",
                                response.getStatusLine())
                            );
                    debugLog(listener,
                             String.format("statusCode: %d%n",
                                response.getStatusLine().getStatusCode())
                            );
                    if (response.getStatusLine().getStatusCode() != JIRA_SUCCESS_CODE) {
                        throw new RuntimeException(this.prefixError + " Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                    }

                    JsonObject json = readJsonObject(response);
                    String key = json.getString("key");
                    if (key == null) {
                        logger.printf("%s Didn't get key back from Jira!%n", prefixError);
                    }
                    else {
                        logger.printf("%s Filed %sbrowse/%s%n", pInfo, serverAddress, key);
                    }

                    httpClient.getConnectionManager().shutdown();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (JsonParsingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                logger.printf("%s This issue is old; not reporting.%n", pInfo);
            }
        }
    }

    private JsonObject readJsonObject(HttpResponse response) throws IOException {
        JsonReader jsonReader = Json.createReader(response.getEntity().getContent());
        try {
            return jsonReader.readObject();
        }
        finally {
            jsonReader.close();
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Jira Test Result Reporter";
        }
        
        public FormValidation doCheckProjectKey(@QueryParameter String value) {
        	if (value.isEmpty()) {
        		return FormValidation.error("You must provide a project key.");
        	} else {
        		return FormValidation.ok();
        	}
        }

        public FormValidation doCheckServerAddress(@QueryParameter String value) {
        	if (value.isEmpty()) {
        		return FormValidation.error("You must provide an URL.");
        	}
        	
        	try {
        		new URL(value);
        	} catch (final MalformedURLException e) {
        		return FormValidation.error("This is not a valid URL.");
        	}
        	
        	return FormValidation.ok();
        }
    }
}

