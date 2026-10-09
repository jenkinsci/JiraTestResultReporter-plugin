package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.jenkinsci.plugins.JiraTestResultReporter.JobConfigMapping.JobConfigEntry;
import org.jenkinsci.plugins.JiraTestResultReporter.JobConfigMapping.JobConfigEntryBuilder;
import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.StringFields;
import org.junit.jupiter.api.Test;

class JobConfigEntryBuilderTest {

    @Test
    void requiresProjectKeyAndIssueType() {
        assertThrows(IllegalStateException.class, () -> new JobConfigEntryBuilder().build());
        assertThrows(
                IllegalStateException.class,
                () -> new JobConfigEntryBuilder().withProjectKey("PROJ").build());
    }

    @Test
    void buildsEntryWithDefaultsAndConfiguredOptions() {
        JobConfigEntry entry = new JobConfigEntryBuilder()
                .withProjectKey("PROJ")
                .withIssueType(10001L)
                .withAutoRaiseIssues(true)
                .withAutoResolveIssues(true)
                .withAutoUnlinkIssues(true)
                .withOverrideResolvedIssues(true)
                .withAdditionalAttachments(true)
                .withManualAddIssues(true)
                .build();

        assertEquals("PROJ", entry.getProjectKey());
        assertEquals(10001L, entry.getIssueType());
        assertTrue(entry.getAutoRaiseIssue());
        assertTrue(entry.getAutoResolveIssue());
        assertTrue(entry.getAutoUnlinkIssue());
        assertTrue(entry.getOverrideResolvedIssues());
        assertTrue(entry.getAdditionalAttachments());
        assertTrue(entry.getManualAddIssue());
        assertEquals(2, entry.getConfigs().size());
        assertTrue(entry.getIssueKeyPattern().matcher("PROJ-123").matches());
        assertFalse(entry.getIssueKeyPattern().matcher("OTHER-123").matches());
    }

    @Test
    void preservesProvidedConfigsAndAllowsMissingProjectKeyOnDirectEntry() {
        JobConfigEntry entry =
                new JobConfigEntry(null, 10001L, new ArrayList<>(), false, false, false, false, false, false);

        assertEquals(null, entry.getIssueKeyPattern());
        assertEquals(10001L, entry.getIssueType());
    }

    @Test
    void doesNotAppendDefaultsWhenSummaryAndDescriptionAreProvided() {
        ArrayList<AbstractFields> configs = new ArrayList<>();
        configs.add(new StringFields("summary", "Configured summary"));
        configs.add(new StringFields("description", "Configured description"));

        JobConfigEntry entry = new JobConfigEntryBuilder()
                .withProjectKey("PROJ")
                .withIssueType(10001L)
                .withConfigs(configs)
                .build();

        assertEquals(2, entry.getConfigs().size());
        assertEquals("Configured summary", ((StringFields) entry.getConfigs().get(0)).getValue());
        assertEquals(
                "Configured description", ((StringFields) entry.getConfigs().get(1)).getValue());
    }
}
