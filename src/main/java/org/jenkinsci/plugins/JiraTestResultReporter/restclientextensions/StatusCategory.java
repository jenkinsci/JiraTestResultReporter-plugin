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

import java.net.URI;

/**
 * Created by tuicu.
 */
public class StatusCategory {

    private final URI self;
    private final Long id;
    private final String key;
    private final String colorName;

    public String getColorName() {
        return colorName;
    }

    public URI getSelf() { return self; }

    public Long getId()  { return id; }

    public String getKey() { return key; }

    public StatusCategory(URI self, Long id, String key, String colorName) {
        this.self = self;
        this.id = id;
        this.key = key;
        this.colorName = colorName;
    }
}
