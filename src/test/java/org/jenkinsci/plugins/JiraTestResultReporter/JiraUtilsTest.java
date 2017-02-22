package org.jenkinsci.plugins.JiraTestResultReporter;

import org.junit.Test;
import static org.junit.Assert.*;

public class JiraUtilsTest
{
    @Test
    public void testJQLEscapeBang()
    {
        String escapeJQL = JiraUtils.escapeJQL("Engine Crashed!");
        assertEquals("Engine Crashed\\\\!", escapeJQL);
    }
}
