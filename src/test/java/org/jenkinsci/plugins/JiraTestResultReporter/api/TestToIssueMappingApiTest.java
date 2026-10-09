package org.jenkinsci.plugins.JiraTestResultReporter.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.jenkinsci.plugins.JiraTestResultReporter.TestToIssueMapping;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@WithJenkins
class TestToIssueMappingApiTest {

    private final TestToIssueMappingApi api = new TestToIssueMappingApi();

    @Test
    void reportsMissingJobParameter() throws Exception {
        ResponseCapture response = new ResponseCapture();

        api.doJson(request(null), response.proxy());

        assertEquals("You need to set the \"job\" parameter", response.body());
        assertNull(response.status);
    }

    @Test
    void returnsNotFoundForMissingTopLevelNestedAndMatrixJobs(JenkinsRule jenkins) throws Exception {
        ResponseCapture topLevel = new ResponseCapture();
        api.doJson(request("missing-" + UUID.randomUUID()), topLevel.proxy());
        assertEquals(404, topLevel.status);

        FreeStyleProject parent = jenkins.createFreeStyleProject(uniqueName("folder-parent"));
        ResponseCapture nested = new ResponseCapture();
        api.doJson(request(parent.getName() + "/missing-child"), nested.proxy());
        assertEquals(404, nested.status);

        MatrixProject matrix = jenkins.createProject(MatrixProject.class, uniqueName("matrix"));
        ResponseCapture matrixChild = new ResponseCapture();
        api.doJson(request(matrix.getName() + "/missing-axis"), matrixChild.proxy());
        assertEquals(404, matrixChild.status);
    }

    @Test
    void returnsIssueMappingAsJsonForAJob(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject(uniqueName("api-job"));
        TestToIssueMapping mapping = TestToIssueMapping.getInstance();
        mapping.register(job);
        mapping.addTestToIssueMapping(job, "test-id", "PROJ-123");
        ResponseCapture response = new ResponseCapture();

        api.doJson(request(job.getName()), response.proxy());

        assertEquals("application/json", response.contentType);
        assertEquals(
                "PROJ-123",
                JsonParser.parseString(response.body())
                        .getAsJsonObject()
                        .get("test-id")
                        .getAsString());
    }

    @Test
    void returnsJsonForMatrixProject(JenkinsRule jenkins) throws Exception {
        MatrixProject matrix = jenkins.createProject(MatrixProject.class, uniqueName("matrix"));
        ResponseCapture response = new ResponseCapture();

        api.doJson(request(matrix.getName()), response.proxy());

        assertEquals("application/json", response.contentType);
        assertEquals(
                0, JsonParser.parseString(response.body()).getAsJsonObject().size());
    }

    private StaplerRequest2 request(String jobName) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("getParameter") && args != null && "job".equals(args[0])) {
                return jobName;
            }
            return defaultValue(method.getReturnType());
        };
        return proxy(StaplerRequest2.class, handler);
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private class ResponseCapture {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private Integer status;
        private String contentType;

        private StaplerResponse2 proxy() {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().equals("getWriter")) {
                    return writer;
                }
                if (method.getName().equals("setContentType")) {
                    contentType = (String) args[0];
                    return null;
                }
                if (method.getName().equals("sendError")) {
                    status = (Integer) args[0];
                    return null;
                }
                return null;
            };
            return TestToIssueMappingApiTest.this.proxy(StaplerResponse2.class, handler);
        }

        private String body() {
            writer.flush();
            return body.toString();
        }
    }
}
