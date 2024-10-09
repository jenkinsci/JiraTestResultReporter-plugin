package org.jenkinsci.plugins.JiraTestResultReporter;

import hudson.EnvVars;
import java.util.HashMap;
import java.util.Map;

public class JiraTestDataRegistry {

    private static final String BUILD_URL = "BUILD_URL";

    private static JiraTestDataRegistry instance = new JiraTestDataRegistry();

    private Map<String, JiraTestData> jiraTestDataByUrl;

    private JiraTestDataRegistry() {
        jiraTestDataByUrl = new HashMap<String, JiraTestData>();
    }

    public static JiraTestDataRegistry getInstance() {
        return instance;
    }

    public JiraTestData getJiraTestData(EnvVars envVars) {
        String key = envVars.get(BUILD_URL);
        synchronized (key) {
            return jiraTestDataByUrl.get(key);
        }
    }

    public void putJiraTestData(EnvVars envVars) {
        String key = envVars.get(BUILD_URL);
        synchronized (key) {
            jiraTestDataByUrl.put(key, new JiraTestData(envVars));
        }
    }
}
