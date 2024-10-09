package org.jenkinsci.plugins.JiraTestResultReporter;

import hudson.EnvVars;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.PipelineTestDetails;
import hudson.tasks.test.TestObject;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JiraTestDataTest {

    private SuiteResult suiteResult;

    private JiraTestData target;

    @Before
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
        Assert.assertEquals(1, actionList.size());
        Assert.assertEquals(JiraTestAction.class, actionList.get(0).getClass());
    }

    @Test
    public void getTestAction_failToParseToCaseResult_shouldReturnEmptyList() {
        TestObject testObject = new TestResult();
        List<?> actionList = target.getTestAction(testObject);
        Assert.assertTrue(actionList.isEmpty());
    }
}
