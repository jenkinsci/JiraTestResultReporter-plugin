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
package org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions;

import com.atlassian.jira.rest.client.internal.json.JsonObjectParser;
import com.atlassian.jira.rest.client.internal.json.JsonParseUtil;
import java.net.URI;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Created by tuicu.
 */
public class StatusCategoryJsonParser implements JsonObjectParser<StatusCategory> {

    @Override
    public StatusCategory parse(JSONObject jsonObject) throws JSONException {
        URI self = JsonParseUtil.getSelfUri(jsonObject);
        Long id = JsonParseUtil.getOptionalLong(jsonObject, "id");
        String key = jsonObject.getString("key");
        String colorName = jsonObject.getString("colorName");
        return new StatusCategory(self, id, key, colorName);
    }
}
