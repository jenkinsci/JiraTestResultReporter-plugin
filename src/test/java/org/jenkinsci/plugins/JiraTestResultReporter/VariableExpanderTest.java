package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.EnvVars;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.test.PipelineTestDetails;
import org.junit.jupiter.api.Test;

class VariableExpanderTest {

    @Test
    void expandsEnvironmentVariablesAndPreservesUnknownVariables() {
        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_NUMBER", "42");

        String expanded =
                VariableExpander.expandVariables(newCaseResult(), envVars, "Build ${BUILD_NUMBER}: ${UNKNOWN}");

        assertEquals("Build 42: ${UNKNOWN}", expanded);
    }

    @Test
    void expandsPluginVariablesAndMultipleOccurrences() {
        String expanded =
                VariableExpander.expandVariables(newCaseResult(), new EnvVars(), "line${CRLF}break${CRLF}done");

        assertEquals("line\nbreak\ndone", expanded);
    }

    @Test
    void expandsCaseResultNames() {
        CaseResult test = newCaseResult("org.example.SampleTest", "shouldFail");

        assertEquals("shouldFail", VariableExpander.expandVariables(test, new EnvVars(), "${TEST_NAME}"));
        assertEquals("(root)", VariableExpander.expandVariables(test, new EnvVars(), "${TEST_PACKAGE_NAME}"));
        assertEquals(
                test.getClassName() + "." + test.getName(),
                VariableExpander.expandVariables(test, new EnvVars(), "${TEST_PACKAGE_CLASS_METHOD_NAME}"));
    }

    @Test
    void returnsTextUnchangedWhenTestIsNull() {
        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_NUMBER", "42");

        assertEquals(
                "${BUILD_NUMBER} ${CRLF}", VariableExpander.expandVariables(null, envVars, "${BUILD_NUMBER} ${CRLF}"));
    }

    private CaseResult newCaseResult() {
        return newCaseResult("", "");
    }

    private CaseResult newCaseResult(String className, String testName) {
        SuiteResult suiteResult = new SuiteResult("SuiteResult", "", "", new PipelineTestDetails());
        return new CaseResult(suiteResult, testName, className);
    }
}
