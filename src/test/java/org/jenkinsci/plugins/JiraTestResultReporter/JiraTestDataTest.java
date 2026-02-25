package org.jenkinsci.plugins.JiraTestResultReporter;

import hudson.EnvVars;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.PipelineTestDetails;
import hudson.tasks.test.TestObject;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JiraTestDataTest {

    private SuiteResult suiteResult;

    private JiraTestData target;

    @BeforeEach
    public void setup() {
        EnvVars envVars = new EnvVars();
        PipelineTestDetails pipelineTestDetails = new PipelineTestDetails();
        suiteResult = new SuiteResult("SuiteResult", StringUtils.EMPTY, StringUtils.EMPTY, pipelineTestDetails);
        this.target = new JiraTestData(envVars);
    }

    @Test
    public void getTestAction_canParseToCaseResult_shouldReturnListOfActions() {
        CaseResult testObject = new CaseResult(suiteResult, StringUtils.EMPTY, StringUtils.EMPTY);
        List<?> actionList = target.getTestAction(testObject);
        Assertions.assertEquals(1, actionList.size());
        Assertions.assertEquals(JiraTestAction.class, actionList.get(0).getClass());
    }

    @Test
    public void getTestAction_failToParseToCaseResult_shouldReturnEmptyList() {
        TestObject testObject = new TestResult();
        List<?> actionList = target.getTestAction(testObject);
        Assertions.assertTrue(actionList.isEmpty());
    }
}
