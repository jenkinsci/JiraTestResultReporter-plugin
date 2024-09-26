/**
 Copyright 2015 Andrei Tuicu

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import java.util.Collections;
import java.util.List;

import hudson.EnvVars;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResultAction;


/**
 * Created by tuicu.
 */
public class JiraTestData extends TestResultAction.Data {
    private EnvVars envVars;

    /**
     * Constructor
     * @param envVars environment variables associated with this build
     */
    public JiraTestData(EnvVars envVars) {
        this.envVars = envVars;
    }

    /**
     * Getter for the environment variables
     * @return environment variables map
     */
    public EnvVars getEnvVars() {
        return envVars;
    }


    /**
     * Method for creating test actions associated with tests
     * @param testObject
     * @return a test action
     */
    @Override
    public List<? extends TestAction> getTestAction(TestObject testObject) {
        if (testObject instanceof CaseResult) {
            CaseResult test = (CaseResult) testObject;
            return Collections.singletonList(new JiraTestAction(this, test));
        }

        return Collections.emptyList();
    }

}
