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

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * Created by tuicu.
 * Needed a class that has a annotated constructor with DataBoundConstructor and a string parameter
 */
public class Entry extends AbstractDescribableImpl<Entry> implements Serializable {
    public static final long serialVersionUID = -2123529202949140774L;
    private String value;

    @Exported
    public String getValue() {
        return value;
    }

    @DataBoundConstructor
    public Entry(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Symbol("jiraArrayEntry")
    @Extension
    public static class EntryDescriptor extends Descriptor<Entry> {}
}
