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
package org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions;

import com.atlassian.jira.rest.client.api.domain.Status;

/**
 * Created by tuicu.
 * Extension of the Status object, that has information about status category
 */
public class FullStatus extends Status {

    private final StatusCategory statusCategory;

    /**
     * Constructor
     * @param status the status.
     * @param statusCategory the status category.
     */
    public FullStatus(Status status, StatusCategory statusCategory) {
        super(status.getSelf(), status.getId(), status.getName(), status.getDescription(), status.getIconUrl());
        this.statusCategory = statusCategory;
    }

    /**
     * Getter for the color name
     * @return the color name.
     */
    public String getColorName() {
        return statusCategory.getColorName();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FullStatus) {
            return super.equals(other);
        }

        return false;
    }
}
