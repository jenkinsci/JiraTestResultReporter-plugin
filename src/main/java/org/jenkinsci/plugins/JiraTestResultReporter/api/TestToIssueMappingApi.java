package org.jenkinsci.plugins.JiraTestResultReporter.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import hudson.matrix.MatrixProject;
import hudson.model.Api;
import hudson.model.Item;
import hudson.model.Job;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.JiraTestResultReporter.TestToIssueMapping;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Created by tuicu on 12/08/16.
 */
public class TestToIssueMappingApi extends Api {
    private static final Gson GSON =
            new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public Api getApi() {
        return this;
    }

    public TestToIssueMappingApi() {
        super(null);
    }

    @Override
    public void doJson(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String jobName = req.getParameter("job");
        JsonElement result;

        if (jobName == null) {
            rsp.getWriter().write("You need to set the \"job\" parameter");
            return;
        }

        // sub job of a matrix project or a folder
        if (jobName.contains("/")) {
            String matrixJobName = jobName.split("/")[0];
            String matrixSubJobName = jobName.split("/")[1];
            Item item = Jenkins.get().getItem(matrixJobName);

            // check if it is matrix project
            if (item instanceof MatrixProject) {
                MatrixProject matrixProject = (MatrixProject) item;
                result = TestToIssueMapping.getInstance().getMap(matrixProject, matrixSubJobName);
            }
            // else consider job resides in a sub-folder
            else {
                Job<?, ?> job = (Job<?, ?>) Jenkins.get().getItemByFullName(jobName);
                if (job == null) {
                    rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                result = TestToIssueMapping.getInstance().getMap(job);
            }
            // top level job (either matrix, freestyle or maven
        } else {
            Job<?, ?> job = (Job<?, ?>) Jenkins.get().getItem(jobName);
            if (job == null) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            result = TestToIssueMapping.getInstance().getMap(job);
        }

        if (result != null) {
            rsp.setContentType("application/json");
            rsp.getWriter().write(GSON.toJson(result));
        } else {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
