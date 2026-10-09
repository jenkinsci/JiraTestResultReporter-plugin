package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.EnvVars;
import org.junit.jupiter.api.Test;

class JiraTestDataRegistryTest {

    @Test
    void storesAndReplacesTestDataByBuildUrl() {
        JiraTestDataRegistry registry = JiraTestDataRegistry.getInstance();
        EnvVars firstBuild = envVars("https://jenkins.example/job/example/101/");
        EnvVars sameBuild = envVars("https://jenkins.example/job/example/101/");

        assertNull(registry.getJiraTestData(firstBuild));

        registry.putJiraTestData(firstBuild);
        JiraTestData firstData = registry.getJiraTestData(sameBuild);
        assertNotNull(firstData);
        assertSame(firstBuild, firstData.getEnvVars());

        registry.putJiraTestData(sameBuild);
        JiraTestData replacement = registry.getJiraTestData(firstBuild);
        assertNotNull(replacement);
        assertNotSame(firstData, replacement);
        assertSame(sameBuild, replacement.getEnvVars());
    }

    private EnvVars envVars(String buildUrl) {
        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_URL", buildUrl);
        return envVars;
    }
}
