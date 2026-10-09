package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.test.PipelineTestDetails;
import java.util.List;
import org.jenkinsci.plugins.JiraTestResultReporter.config.Entry;
import org.jenkinsci.plugins.JiraTestResultReporter.config.SelectableArrayFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.SelectableFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.StringArrayFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.StringFields;
import org.junit.jupiter.api.Test;

class ConfigFieldsTest {

    @Test
    void expandsStringFieldValues() {
        StringFields field = new StringFields("summary", "Build ${BUILD_NUMBER}");
        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_NUMBER", "42");

        FieldInput input = field.getFieldInput(newCaseResult(), envVars);

        assertEquals("summary", input.getId());
        assertEquals("Build 42", input.getValue());
        assertEquals("summary", field.getFieldKey());
        assertEquals("Build ${BUILD_NUMBER}", field.getValue());
        assertEquals(field, field.readResolve());
    }

    @Test
    void convertsSelectableFieldToAnIdValue() {
        SelectableFields field = new SelectableFields("priority", "3");

        FieldInput input = field.getFieldInput(null, new EnvVars());
        ComplexIssueInputFieldValue value = assertInstanceOf(ComplexIssueInputFieldValue.class, input.getValue());

        assertEquals("priority", input.getId());
        assertEquals("3", value.getValuesMap().get("id"));
        assertEquals(field, field.readResolve());
    }

    @Test
    void expandsStringArrayEntries() {
        StringArrayFields field =
                new StringArrayFields("labels", List.of(new Entry("build-${BUILD_NUMBER}"), new Entry("regression")));
        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_NUMBER", "42");

        FieldInput input = field.getFieldInput(newCaseResult(), envVars);

        assertEquals("labels", input.getId());
        assertEquals(List.of("build-42", "regression"), input.getValue());
        assertEquals("build-${BUILD_NUMBER}", field.getValues().get(0).getValue());
        assertEquals(field, field.readResolve());
    }

    @Test
    void convertsSelectableArrayValuesToIds() {
        SelectableArrayFields field = new SelectableArrayFields("components", List.of(new Entry("1"), new Entry("2")));

        FieldInput input = field.getFieldInput(null, new EnvVars());
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> values = (List<ComplexIssueInputFieldValue>) input.getValue();

        assertEquals("components", input.getId());
        assertEquals(
                List.of("1", "2"),
                values.stream().map(value -> value.getValuesMap().get("id")).toList());
        assertEquals(field, field.readResolve());
    }

    @Test
    void exposesArrayEntryValue() {
        Entry entry = new Entry("value");

        assertEquals("value", entry.getValue());
        assertEquals("value", entry.toString());
    }

    private CaseResult newCaseResult() {
        SuiteResult suiteResult = new SuiteResult("SuiteResult", "", "", new PipelineTestDetails());
        return new CaseResult(suiteResult, "", "");
    }
}
