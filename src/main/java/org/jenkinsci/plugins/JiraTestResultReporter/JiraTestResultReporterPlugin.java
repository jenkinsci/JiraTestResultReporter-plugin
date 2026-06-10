package org.jenkinsci.plugins.JiraTestResultReporter;

import hudson.model.Api;
import org.jenkinsci.plugins.JiraTestResultReporter.api.TestToIssueMappingApi;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Created by tuicu on 05/08/16.
 */
@ExportedBean
public class JiraTestResultReporterPlugin {
    public Api getTestToIssueMapping() {
        return new TestToIssueMappingApi();
    }
}
