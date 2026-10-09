package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import java.nio.file.Files;
import org.jenkinsci.plugins.JiraTestResultReporter.JobConfigMapping.JobConfigEntry;
import org.jenkinsci.plugins.JiraTestResultReporter.JobConfigMapping.JobConfigEntryBuilder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MappingPersistenceTest {

    @Test
    void persistsIssueLinksAndJobConfiguration(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("mapping-persistence");
        TestToIssueMapping issueMapping = TestToIssueMapping.getInstance();
        issueMapping.register(project);

        assertNull(issueMapping.getTestIssueKey(project, "test-id"));
        issueMapping.addTestToIssueMapping(project, "test-id", "PROJ-123");

        assertEquals("PROJ-123", issueMapping.getTestIssueKey(project, "test-id"));
        assertTrue(Files.exists(project.getRootDir().toPath().resolve("JiraIssueKeyToTestMap.json")));
        assertEquals(
                "PROJ-123",
                issueMapping.getMap(project).getAsJsonObject().get("test-id").getAsString());

        issueMapping.removeTestToIssueMapping(project, "test-id", "PROJ-999");
        assertEquals("PROJ-123", issueMapping.getTestIssueKey(project, "test-id"));
        issueMapping.removeTestToIssueMapping(project, "test-id", "PROJ-123");
        assertNull(issueMapping.getTestIssueKey(project, "test-id"));
        issueMapping.register(null);

        JobConfigMapping configMapping = JobConfigMapping.getInstance();
        JobConfigEntry entry = new JobConfigEntryBuilder()
                .withProjectKey("PROJ")
                .withIssueType(10001L)
                .withAutoRaiseIssues(true)
                .withAutoResolveIssues(true)
                .withAutoUnlinkIssues(true)
                .withAdditionalAttachments(true)
                .withOverrideResolvedIssues(true)
                .withManualAddIssues(true)
                .build();
        configMapping.saveConfig(project, entry);

        assertEquals("PROJ", configMapping.getProjectKey(project));
        assertEquals(10001L, configMapping.getIssueType(project));
        assertEquals(2, configMapping.getConfig(project).size());
        assertTrue(configMapping.getAutoRaiseIssue(project));
        assertTrue(configMapping.getAutoResolveIssue(project));
        assertTrue(configMapping.getAutoUnlinkIssue(project));
        assertTrue(configMapping.getAdditionalAttachments(project));
        assertTrue(configMapping.getOverrideResolvedIssues(project));
        assertTrue(configMapping.getManualAddIssue(project));
        assertTrue(configMapping.getIssueKeyPattern(project).matcher("PROJ-123").matches());
        assertFalse(
                configMapping.getIssueKeyPattern(project).matcher("INVALID-123").matches());
        assertTrue(Files.exists(project.getRootDir().toPath().resolve("JiraIssueJobConfigs.json")));

        configMapping.saveConfig(null, entry);
        assertNull(configMapping.getProjectKey(null));
        assertNull(configMapping.getIssueType(null));
        assertNull(configMapping.getConfig(null));
        assertFalse(configMapping.getAutoRaiseIssue(null));
        assertFalse(configMapping.getAutoResolveIssue(null));
        assertFalse(configMapping.getAutoUnlinkIssue(null));
        assertFalse(configMapping.getAdditionalAttachments(null));
        assertFalse(configMapping.getOverrideResolvedIssues(null));
        assertFalse(configMapping.getManualAddIssue(null));
        assertNull(configMapping.getIssueKeyPattern(null));
    }
}
