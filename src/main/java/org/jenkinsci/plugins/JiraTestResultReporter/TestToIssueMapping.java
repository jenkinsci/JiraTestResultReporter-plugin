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
package org.jenkinsci.plugins.JiraTestResultReporter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import hudson.matrix.MatrixProject;
import hudson.model.Job;
import jenkins.model.Jenkins;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tuicu.
 * The class the stores the mapping from tests to issues. It is implemented as a singleton pattern, that has a large
 * map from job names to smaller maps that store the actual links from tests to issue keys. Each time a change is made
 * to a small map (add/remove a mapping from a test to a issue), only that small map gets serialized.
 * The file can be found in ${JENKINS_HOME}/job/${JOB_NAME}/JiraIssueKeyToTestMap
 */
public class TestToIssueMapping {
    private static final TestToIssueMapping INSTANCE = new TestToIssueMapping();
    private static final Gson GSON = new Gson();
    private static final String MAP_FILE_NAME = "JiraIssueKeyToTestMap";
    /**
     * Getter for the singleton instance
     * @return the singleton instance.
     */
    public static TestToIssueMapping getInstance() {
        return INSTANCE;
    }

    private final Map<String, Map<String, String>> jobsMap;

    /**
     * Constructor. It will look into all jobs to see if there are any maps saved from previous Jenkins runs.
     */
    private TestToIssueMapping() {
        jobsMap = new HashMap<>();
        for(Job job : Jenkins.getInstance().getItems(Job.class)) {
            register(job);
        }
    }

    /**
     * Method for saving the test to issue HashMap for the job
     * @param job the job.
     * @param map the key value map.
     */
    private void saveMap(Job job, Map<String, String> map) {
        try {
            Gson gson = new Gson();
            try (FileOutputStream fileOut = new FileOutputStream(getPathToFileMap(job) + ".json");
                    JsonWriter writer = new JsonWriter(new OutputStreamWriter(fileOut, "UTF-8")))
            {
                writer.setIndent("  ");
                gson.toJson(map, HashMap.class, writer);
            }
        }
        catch (JsonIOException | IOException e) {
            JiraUtils.logError("ERROR: Could not save job map", e);
        }
    }

    /**
     * Method for constructing the path to the file map given a job object
     * @param job the job.
     * @return the path to the file map.
     */
    private String getPathToFileMap(Job job) {
        return job.getRootDir().toPath().resolve(MAP_FILE_NAME).toString();
    }


    /**
     * Looks for the issue map from a previous version of the plugin and tries to load it
     * and save it in the new format
     * @param job the job.
     * @return the loaded test to issue HashMap, or null if there was no file, or it could not be loaded
     */
    private Map<String, String> loadBackwardsCompatible(Job job) {
        try {
            Map<String, String> testToIssue;
            try (FileInputStream fileIn = new FileInputStream(getPathToFileMap(job));
                    ObjectInputStream in = new ObjectInputStream(fileIn))
            {
                testToIssue = (HashMap<String, String>) in.readObject();
                JiraUtils.log("Found and successfully loaded issue map from a previous version for job: "
                        + job.getFullName());
                saveMap(job, testToIssue);
            }
            return testToIssue;
        } catch (FileNotFoundException e) {
            JiraUtils.logError(e.getMessage(), e);
        } catch (Exception e) {
            JiraUtils.logError("ERROR: Found issue map from a previous version, but was unable to load it for job "
                    + job.getFullName(), e);
        }
        return null;
    }

    /**
     * Loads the test to issue HashMap from the file associated with the project
     * @param job the job.
     * @return the loaded test to issue HashMap
     */
    private Map<String, String> loadMap(Job job) {
        Map<String, String> testToIssue = null;
        try {
            Gson gson = new Gson();
            try (FileInputStream fileIn = new FileInputStream(getPathToFileMap(job) + ".json");
                    JsonReader reader = new JsonReader(new InputStreamReader(fileIn, "UTF-8")))
            {
                
                testToIssue = gson.fromJson(reader, HashMap.class);
            }
            return testToIssue;
        } catch (FileNotFoundException e) {
            testToIssue = loadBackwardsCompatible(job);
            if(testToIssue == null) {
                JiraUtils.log("No map found for job " + job.getFullName());
            } else {
                return testToIssue;
            }
        } catch (Exception e) {
            JiraUtils.logError("ERROR: Could not load map for job " + job.getFullName(), e);
            e.printStackTrace();
        }

        return new HashMap<>();
    }

    /**
     * Method for registering a job
     * @param job the job.
     */
    public void register(Job job) {
        if(job instanceof MatrixProject) {
            for(Job child : ((MatrixProject)job).getAllJobs()) {
                if(child instanceof MatrixProject) continue; //parent job
                register(child);
            }
            return;
        }

        if(jobsMap.containsKey(job.getFullName()))
            return;

        synchronized(jobsMap) {
            if(jobsMap.containsKey(job.getFullName()))
                return;

            jobsMap.put(job.getFullName(), loadMap(job));
        }
    }

    /**
     * Link an issue to a test
     * @param job the job.
     * @param testId the test id.
     * @param issueKey the issue key.
     */
    public void addTestToIssueMapping(Job job, String testId, String issueKey) {
        Map<String, String> jobMap = jobsMap.get(job.getFullName());
        if(jobMap == null) {
            JiraUtils.log("ERROR: Unregistered job " + job.getFullName());
            register(job);
            jobMap = jobsMap.get(job.getFullName());
        }

        synchronized (jobMap) {
            jobMap.put(testId, issueKey);
            saveMap(job, jobMap);
        }
    }

    /**
     * Unlink an issue from a test
     * @param job the job.
     * @param testId the test id.
     * @param issueKey the issue key.
     */
    public void removeTestToIssueMapping(Job job, String testId, String issueKey) {
        Map<String, String> jobMap = jobsMap.get(job.getFullName());
        if(jobMap == null) {
            JiraUtils.logError("ERROR: Unregistered job " + job.getFullName());
            return;
        }

        synchronized (jobMap) {
            if(jobMap.get(testId).equals(issueKey)) {
                jobMap.remove(testId);
                saveMap(job, jobMap);
            }
        }
    }

    /**
     * Get the issue key associated with a test
     * @param job the job.
     * @param testId the test id.
     * @return the issue key.
     */
    public String getTestIssueKey(Job job, String testId) {
        Map<String, String> jobMap = jobsMap.get(job.getFullName());
        if(jobMap == null) {
            JiraUtils.logWarning("WARNING: Unregistered job " + job.getFullName());
            register(job);
            return jobsMap.get(job.getFullName()) != null ? jobsMap.get(job.getFullName()).get(testId) : null;
        }
        return jobMap.get(testId);
    }

    public JsonElement getMap(MatrixProject matrixProject, String subJobName) {
        Job job = matrixProject.getItem(subJobName);
        if (job == null)
            return null;
        return getMap(job);
    }

    public JsonElement getMap(MatrixProject matrixProject) {
        JsonObject jsonObject = new JsonObject();
        for(Job job : matrixProject.getAllJobs()) {
            if(matrixProject == job)
                continue;
            jsonObject.add(job.getName(), getMap(job));
        }
        return jsonObject;
    }

    public JsonElement getMap(Job job) {
        if(job instanceof MatrixProject) {
            return getMap((MatrixProject)job);
        } else {
            Map<String, String> jobMap = jobsMap.get(job.getFullName());
            if(jobMap == null) {
                jobMap = new HashMap<>();
            }
            synchronized (jobMap) {
                return GSON.toJsonTree(jobMap);
            }
        }
    }
}