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
package org.jenkinsci.plugins.JiraTestResultReporter.config;

import com.atlassian.jira.rest.client.api.IdentifiableEntity;
import com.atlassian.jira.rest.client.api.NamedEntity;
import com.atlassian.jira.rest.client.api.domain.CustomFieldOption;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.tasks.test.TestResult;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tuicu.
 * Class for fields with predefined values, that can have multiple values (ex. Components, Fix Versions, etc.)
 */
public class SelectableArrayFields extends AbstractFields {
    public static final long serialVersionUID = 312389869891081321L;
    private final String fieldKey;
    private final List<Entry> values;
    private transient FieldInput fieldInput;

    /**
     * Constructor
     * @param fieldKey fieldKey
     * @param values values
     */
    @DataBoundConstructor
    public SelectableArrayFields(String fieldKey, List<Entry> values) {
        this.fieldKey = fieldKey;
        this.values = values;
        List<ComplexIssueInputFieldValue> valueList = new ArrayList<>();
        for(Entry v : values) {
            valueList.add(ComplexIssueInputFieldValue.with("id", v.getValue()));
        }
        fieldInput = new FieldInput(fieldKey, valueList);
    }

    /**
     * Getter for the field key
     * @return the field key.
     */
    public String getFieldKey() { return fieldKey; }

    /**
     * Getter for values
     * @return get the list of values.
     */
    public List<Entry> getValues() {
        return values;
    }

    @Override
    public Object readResolve()  {
        List<ComplexIssueInputFieldValue> valueList = new ArrayList<>();
        for(Entry v : values) {
            valueList.add(ComplexIssueInputFieldValue.with("id", v.getValue()));
        }
        fieldInput = new FieldInput(fieldKey, valueList);
        return this;
    }

    @Override
    public String toString() {
        return this.getClass().getName() +  " #" + fieldKey + " : " + values.toString() + "#";
    }

    /**
     * Getter for the FieldInput object
     * @param test test
     * @param envVars envVars
     * @return the FieldInput object.
     */
    @Override
    public FieldInput getFieldInput(TestResult test, EnvVars envVars) {
        return fieldInput;
    }

    /**
     * Descriptor, required for the hetero-list
     */
    @Extension
    public static class SelectableArrayFieldsDescriptor extends Descriptor<AbstractFields> {

        @Override
        public String getDisplayName() {
            return "Selectable Array Fields";
        }

        /**
         * Method for filling the field keys selectable
         * @param projectKey projectKey
         * @param issueType issueType
         * @return ListBoxModel
         */
        public ListBoxModel doFillFieldKeyItems(@QueryParameter @RelativePath("..") String projectKey,
                                                @QueryParameter @RelativePath("..") String issueType) {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
            try {
                return jiraDescriptor.getCacheEntry(projectKey, issueType).getSelectableArrayFieldBox();
            }
            catch (NullPointerException e) {
                return new ListBoxModel();
            }
        }

        /**
         * Method for filling the selectable with the allowed values
         * @param projectKey projectKey
         * @param issueType issueType
         * @param fieldKey fieldKey
         * @return ListBoxModel
         */
        public ListBoxModel doFillValueItems(@QueryParameter @RelativePath("../..") String projectKey,
                                             @QueryParameter @RelativePath("../..") String issueType,
                                             @QueryParameter @RelativePath("..") String fieldKey) {

            ListBoxModel listBox = new ListBoxModel();
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
            try {
                Iterable<Object> values = jiraDescriptor.getCacheEntry(projectKey, issueType).getFieldInfoMap().get(fieldKey).getAllowedValues();
                for (Object o : values) {
                    if(o instanceof CustomFieldOption) {
                        CustomFieldOption option = (CustomFieldOption) o;
                        listBox.add(option.getValue(), option.getId().toString());
                    } else if (o instanceof IdentifiableEntity && o instanceof NamedEntity) {
                        listBox.add(((NamedEntity) o).getName(), ((IdentifiableEntity<Long>) o).getId().toString());
                    //work-around for Components and Fix Versions
                    // even though they have ids, for some reason they don't implement IdentifiableEntity
                    // so I'm invoking the getter for the id using reflection
                    } else if (o instanceof  NamedEntity) {
                        try {
                            Method m = o.getClass().getMethod("getId");
                            Object id = (Long) m.invoke(o, null);
                            if(id != null) {
                                listBox.add(((NamedEntity) o).getName(), id.toString());
                            }
                        } catch (Exception e) {
                        }
                    }
                }
                return listBox;
            }
            catch (NullPointerException e) {
                return listBox;
            }
        }
    }
}
