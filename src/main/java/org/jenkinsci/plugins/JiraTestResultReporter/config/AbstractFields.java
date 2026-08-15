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
package org.jenkinsci.plugins.JiraTestResultReporter.config;

import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.tasks.test.TestResult;
import java.io.Serial;
import java.io.Serializable;
import jenkins.model.Jenkins;

/**
 * Created by tuicu.
 * Base class for the hetero-list entries in the job configuration page (config.jelly)
 */
public abstract class AbstractFields implements Describable<AbstractFields>, ExtensionPoint, Serializable {
    @Serial
    private static final long serialVersionUID = 6634175180307435394L;

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<AbstractFields> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    public abstract FieldInput getFieldInput(TestResult test, EnvVars envVars);

    @Serial
    public abstract Object readResolve();
}
