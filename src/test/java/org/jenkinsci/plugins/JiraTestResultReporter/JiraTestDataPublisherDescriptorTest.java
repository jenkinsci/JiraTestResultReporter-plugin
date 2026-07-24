/**
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import hudson.util.Secret;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JiraTestDataPublisherDescriptorTest {

    private JenkinsRule j;
    private JiraTestDataPublisher.JiraTestDataPublisherDescriptor descriptor;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
        descriptor = (JiraTestDataPublisher.JiraTestDataPublisherDescriptor)
                Jenkins.get().getDescriptor(JiraTestDataPublisher.class);
    }

    @Test
    void shouldExportJiraTestResultReporterConfiguration() throws Exception {
        descriptor.setJiraUrl("https://jira.example.com");
        descriptor.setJiraBrowsableUrl("https://jira.example.com/browse");
        descriptor.setUsername("admin");
        descriptor.setPassword(Secret.fromString("secret"));
        descriptor.setUseBearerAuth(true);
        descriptor.setUseLatestRestApi(true);
        descriptor.setDefaultSummary("My summary");
        descriptor.setDefaultDescription("My description");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);
        String yaml = out.toString(StandardCharsets.UTF_8);

        assertThat(yaml, containsString("jiraTestResultReporter:"));
        assertThat(yaml, containsString("jiraUrl: \"https://jira.example.com\""));
        assertThat(yaml, containsString("jiraBrowsableUrl: \"https://jira.example.com/browse\""));
        assertThat(yaml, containsString("username: \"admin\""));
        assertThat(yaml, containsString("useBearerAuth: true"));
        assertThat(yaml, containsString("useLatestRestApi: true"));
        assertThat(yaml, containsString("defaultSummary: \"My summary\""));
        assertThat(yaml, containsString("defaultDescription: \"My description\""));
    }

    @Test
    void testDescriptorSettersAndGetters() {
        assertNotNull(descriptor, "Descriptor should be registered");

        descriptor.setJiraUrl("https://jira.example.com");
        descriptor.setJiraBrowsableUrl("https://jira-browse.example.com");
        descriptor.setUsername("testuser");
        descriptor.setPassword(Secret.fromString("secret123"));
        descriptor.setUseBearerAuth(true);
        descriptor.setUseLatestRestApi(true);
        descriptor.setDefaultSummary("Custom Summary ${TEST_NAME}");
        descriptor.setDefaultDescription("Custom Description ${BUILD_URL}");

        assertEquals("https://jira.example.com", descriptor.getJiraUrl());
        assertEquals("https://jira-browse.example.com", descriptor.getJiraBrowsableUrl());
        assertEquals("testuser", descriptor.getUsername());
        assertEquals("secret123", descriptor.getPassword().getPlainText());
        assertTrue(descriptor.getUseBearerAuth());
        assertTrue(descriptor.getUseLatestRestApi());
        assertEquals("Custom Summary ${TEST_NAME}", descriptor.getDefaultSummary());
        assertEquals("Custom Description ${BUILD_URL}", descriptor.getDefaultDescription());
    }

    @Test
    void testTrimmingAndEmptyValuesInSetters() {
        descriptor.setJiraUrl("  https://jira.example.com  ");
        descriptor.setUsername("  admin  ");

        assertEquals("https://jira.example.com", descriptor.getJiraUrl());
        assertEquals("admin", descriptor.getUsername());

        descriptor.setJiraUrl("");
        descriptor.setUsername("   ");

        assertNull(descriptor.getJiraUrl());
        assertNull(descriptor.getUsername());
    }

    @Test
    void testGlobalConfigWebUIRoundtrip() throws Exception {
        descriptor.setJiraUrl("https://jira-ui.example.com");
        descriptor.setUsername("ui-user");
        descriptor.setPassword(Secret.fromString("ui-password"));
        descriptor.setUseBearerAuth(false);
        descriptor.setDefaultSummary("UI Summary");

        j.configRoundtrip();

        assertEquals("https://jira-ui.example.com", descriptor.getJiraUrl());
        assertEquals("ui-user", descriptor.getUsername());
        assertEquals("ui-password", descriptor.getPassword().getPlainText());
        assertEquals("UI Summary", descriptor.getDefaultSummary());
    }

    @Test
    void testValidateProjectKeyWithoutClient() {
        FormValidation blankValidation = descriptor.doValidateProjectKey("");
        assertEquals(FormValidation.Kind.ERROR, blankValidation.kind);

        FormValidation noClientValidation = descriptor.doValidateProjectKey("PROJ");
        assertEquals(FormValidation.Kind.ERROR, noClientValidation.kind);
        assertTrue(noClientValidation.getMessage().contains("No jira site configured"));
    }
}
