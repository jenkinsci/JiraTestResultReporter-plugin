package org.jenkinsci.plugins.JiraTestResultReporter;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;

import org.jvnet.hudson.reactor.ReactorException;

import hudson.model.Hudson;

public class JenkinsTest extends Hudson {

	public JenkinsTest(File root, ServletContext context)
			throws IOException, InterruptedException, ReactorException {
		super(root, context);
	}

}
