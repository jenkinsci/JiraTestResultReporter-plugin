package org.jenkinsci.plugins.JiraTestResultReporter;

import org.junit.Test;
import static org.junit.Assert.*;


public class JiraUtilsTest
{

    /*
     * Test escapes of special characters in JQL query
     *
     * Currently:
     *  + - & | ! ( ) { } [ ] ^ ~ * ? \ :
     *
     * Reference:
     *  https://confluence.atlassian.com/jiracoreserver073/search-syntax-for-text-fields-861257223.html
     */

    @Test
    public void testJQLEscapePlus()
    {
        String escapeJQL = JiraUtils.escapeJQL("1+1");
        assertEquals("1\\\\+1", escapeJQL);
    }

    @Test
    public void testJQLEscapeMinus()
    {
        String escapeJQL = JiraUtils.escapeJQL("1-1");
        assertEquals("1\\\\-1", escapeJQL);
    }

    @Test
    public void testJQLEscapeAmpersand()
    {
        String escapeJQL = JiraUtils.escapeJQL("Component & Priority");
        assertEquals("Component \\\\& Priority", escapeJQL);
    }

    @Test
    public void testJQLEscapeVerticalBar()
    {
        String escapeJQL = JiraUtils.escapeJQL("Epic || Story");
        assertEquals("Epic \\\\|\\\\| Story", escapeJQL);
    }

    @Test
    public void testJQLEscapeBang()
    {
        String escapeJQL = JiraUtils.escapeJQL("assignee != null");
        assertEquals("assignee \\\\!= null", escapeJQL);
    }

    @Test
    public void testJQLEscapeParensLeft()
    {
        String escapeJQL = JiraUtils.escapeJQL("bugs AND (atlassian OR jira");
        assertEquals("bugs AND \\\\(atlassian OR jira", escapeJQL);
    }

    @Test
    public void testJQLEscapeParensRight()
    {
        String escapeJQL = JiraUtils.escapeJQL("atlassian OR jira)");
        assertEquals("atlassian OR jira\\\\)", escapeJQL);
    }

    @Test
    public void testJQLEscapeBraceLeft()
    {
        String escapeJQL = JiraUtils.escapeJQL("{1");
        assertEquals("\\\\{1", escapeJQL);
    }
    @Test
    public void testJQLEscapeBraceRight()
    {
        String escapeJQL = JiraUtils.escapeJQL("1}");
        assertEquals("1\\\\}", escapeJQL);
    }

    @Test
    public void testJQLEscapeBracketLeft()
    {
        String escapeJQL = JiraUtils.escapeJQL("[INFO");
        assertEquals("\\\\[INFO", escapeJQL);
    }

    @Test
    public void testJQLEscapeBracketRight()
    {
        String escapeJQL = JiraUtils.escapeJQL("INFO]");
        assertEquals("INFO\\\\]", escapeJQL);
    }

    @Test
    public void testJQLEscapeCarat()
    {
        String escapeJQL = JiraUtils.escapeJQL("atlassian^4 jira");
        assertEquals("atlassian\\\\^4 jira", escapeJQL);
    }

    @Test
    public void testJQLEscapeTilde()
    {
        String escapeJQL = JiraUtils.escapeJQL("roam~");
        assertEquals("roam\\\\~", escapeJQL);
    }

    @Test
    public void testJQLEscapeAsterisk()
    {
        String escapeJQL = JiraUtils.escapeJQL("jira && issue");
        assertEquals("jira \\\\&\\\\& issue", escapeJQL);
    }

    @Test
    public void testJQLEscapeQuestionMark()
    {
        String escapeJQL = JiraUtils.escapeJQL("te?t");
        assertEquals("te\\\\?t", escapeJQL);
    }

    @Test
    public void testJQLEscapeBackslash()
    {
        String escapeJQL = JiraUtils.escapeJQL("1\\2\\00");
        assertEquals("1\\\\2\\\\00", escapeJQL);
    }

    @Test
    public void testJQLEscapeColon()
    {
        String escapeJQL = JiraUtils.escapeJQL("Start: Q4");
        assertEquals("Start\\\\: Q4", escapeJQL);
    }

}
