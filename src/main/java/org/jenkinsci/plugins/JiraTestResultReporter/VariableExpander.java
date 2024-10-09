/**
 * Copyright 2015 Andrei Tuicu
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

import hudson.EnvVars;
import hudson.Util;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.TestResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by tuicu.
 * Class for expanding environment variables and variables defined by this plugin
 */
public class VariableExpander {
    private interface Delegate {
        String expand(TestResult test, EnvVars envVars);
    }

    static Pattern varPattern = java.util.regex.Pattern.compile("\\$\\{([\\w\\_]+)\\}");
    static HashMap<String, Delegate> expanders = new HashMap<String, Delegate>();

    static {
        expanders.put("CRLF", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return "\n";
            }
        });

        expanders.put("TEST_RESULT", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                if (test instanceof CaseResult && ((CaseResult) test).isSkipped()) {
                    return "SKIPPED";
                }
                return test.isPassed() ? "FAILED" : "PASSED";
            }
        });

        expanders.put("TEST_NAME", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getDisplayName();
            }
        });

        expanders.put("TEST_FULL_NAME", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getFullDisplayName();
            }
        });

        expanders.put("TEST_STACK_TRACE", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getErrorStackTrace();
            }
        });

        expanders.put("TEST_ERROR_DETAILS", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getErrorDetails();
            }
        });

        expanders.put("TEST_DURATION", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getDurationString();
            }
        });

        expanders.put("TEST_PACKAGE_NAME", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                if (test instanceof CaseResult) {
                    return ((CaseResult) test).getPackageName();
                }
                return "{TEST_PACKAGE_NAME}";
            }
        });

        expanders.put("TEST_STDERR", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getStderr();
            }
        });

        expanders.put("TEST_STDOUT", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getStdout();
            }
        });

        expanders.put("TEST_OVERVIEW", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.toPrettyString();
            }
        });

        expanders.put("TEST_AGE", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                if (test instanceof CaseResult) {
                    return String.valueOf(((CaseResult) test).getAge());
                }
                return "{TEST_AGE}";
            }
        });

        expanders.put("TEST_PASS_COUNT", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return String.valueOf(test.getPassCount());
            }
        });

        expanders.put("TEST_FAIL_COUNT", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return String.valueOf(test.getFailCount());
            }
        });

        expanders.put("TEST_SKIPPED_COUNT", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return String.valueOf(test.getSkipCount());
            }
        });

        expanders.put("TEST_FAIL_SINCE", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return String.valueOf(test.getFailedSince());
            }
        });

        expanders.put("TEST_IS_REGRESSION", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                if (test instanceof CaseResult) {
                    return String.valueOf(((CaseResult) test).getStatus().isRegression());
                }
                return "{TEST_IS_REGRESSION}";
            }
        });

        expanders.put("TEST_PACKAGE_CLASS_METHOD_NAME", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                if (test instanceof CaseResult) {
                    CaseResult t = (CaseResult) test;
                    return String.format("%s.%s", t.getClassName(), t.getName());
                }
                return "{TEST_PACKAGE_CLASS_METHOD_NAME}";
            }
        });

        expanders.put("BUILD_RESULT", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return test.getBuildResult().toString();
            }
        });

        expanders.put("DEFAULT_SUMMARY", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return expandVariables(
                        test, envVars, JiraUtils.getJiraDescriptor().getDefaultSummary());
            }
        });

        expanders.put("DEFAULT_DESCRIPTION", new Delegate() {
            @Override
            public String expand(TestResult test, EnvVars envVars) {
                return expandVariables(
                        test, envVars, JiraUtils.getJiraDescriptor().getDefaultDescription());
            }
        });
    }

    /**
     * Expands the variables from the test paramater, given a TestResult instance for extracting the
     * necessary information
     * @param test
     * @param text
     * @return
     */
    public static String expandVariables(TestResult test, EnvVars envVars, String text) {
        if (test == null) {
            return text;
        }

        Matcher matcher = varPattern.matcher(text);
        ArrayList<String> varsFound = new ArrayList<String>();
        while (matcher.find()) {
            varsFound.add(matcher.group(1));
        }

        for (String varName : varsFound) {
            if (envVars.containsKey(varName)) {
                text = text.replace(
                        new StringBuilder()
                                .append("${")
                                .append(varName)
                                .append("}")
                                .toString(),
                        envVars.get(varName));
                continue;
            }

            if (expanders.containsKey(varName)) {
                text = text.replace(
                        new StringBuilder()
                                .append("${")
                                .append(varName)
                                .append("}")
                                .toString(),
                        Util.fixNull(expanders.get(varName).expand(test, envVars)));
            }
        }

        return text;
    }
}
