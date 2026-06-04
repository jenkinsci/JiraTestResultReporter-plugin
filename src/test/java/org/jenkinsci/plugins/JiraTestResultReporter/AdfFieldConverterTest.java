package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.*;

import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for wiki markup to ADF conversion.
 */
public class AdfFieldConverterTest {

    @Test
    public void testHeadingConversion() {
        String wikiText = "h2. Release Notes\n\nSome content here.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        assertEquals(1, doc.get("version"));
        assertEquals("doc", doc.get("type"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");
        assertEquals(2, content.size());

        // First should be heading
        Map<String, Object> heading = content.get(0).getValuesMap();
        assertEquals("heading", heading.get("type"));

        ComplexIssueInputFieldValue attrsValue = (ComplexIssueInputFieldValue) heading.get("attrs");
        Map<String, Object> attrs = attrsValue.getValuesMap();
        assertEquals(2, attrs.get("level"));
    }

    @Test
    public void testCodeBlockConversion() {
        String wikiText = "h3. How to reproduce\n{noformat}\nfoo\nbar\n{noformat}";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        // Should have heading and code block
        assertEquals(2, content.size());

        Map<String, Object> heading = content.get(0).getValuesMap();
        assertEquals("heading", heading.get("type"));

        Map<String, Object> codeBlock = content.get(1).getValuesMap();
        assertEquals("codeBlock", codeBlock.get("type"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> codeContent = (List<ComplexIssueInputFieldValue>) codeBlock.get("content");
        Map<String, Object> codeText = codeContent.get(0).getValuesMap();
        assertEquals("foo\nbar", codeText.get("text"));
    }

    @Test
    public void testBulletListConversion() {
        String wikiText = "* Item one\n* Item two\n* Item three";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        assertEquals(1, content.size());
        Map<String, Object> bulletList = content.get(0).getValuesMap();
        assertEquals("bulletList", bulletList.get("type"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> items = (List<ComplexIssueInputFieldValue>) bulletList.get("content");
        assertEquals(3, items.size());
    }

    @Test
    public void testNumberedListConversion() {
        String wikiText = "# First step\n# Second step\n# Third step";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        assertEquals(1, content.size());
        Map<String, Object> orderedList = content.get(0).getValuesMap();
        assertEquals("orderedList", orderedList.get("type"));
    }

    @Test
    public void testInlineFormattingBold() {
        String wikiText = "This is *bold* text.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> inlineContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should have: "This is ", "bold" with mark, " text."
        assertEquals(3, inlineContent.size());

        Map<String, Object> boldText = inlineContent.get(1).getValuesMap();
        assertEquals("bold", boldText.get("text"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) boldText.get("marks");
        Map<String, Object> mark = marks.get(0).getValuesMap();
        assertEquals("strong", mark.get("type"));
    }

    @Test
    public void testInlineFormattingItalic() {
        String wikiText = "This is _italic_ text.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> inlineContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        Map<String, Object> italicText = inlineContent.get(1).getValuesMap();
        assertEquals("italic", italicText.get("text"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) italicText.get("marks");
        Map<String, Object> mark = marks.get(0).getValuesMap();
        assertEquals("em", mark.get("type"));
    }

    @Test
    public void testInlineFormattingMonospace() {
        String wikiText = "Use {{code}} for monospace.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> inlineContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        Map<String, Object> codeText = inlineContent.get(1).getValuesMap();
        assertEquals("code", codeText.get("text"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) codeText.get("marks");
        Map<String, Object> mark = marks.get(0).getValuesMap();
        assertEquals("code", mark.get("type"));
    }

    @Test
    public void testLinkConversion() {
        String wikiText = "Visit [Google|https://google.com] or [https://example.com]";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> inlineContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should have: "Visit ", link, " or ", link
        Map<String, Object> firstLink = inlineContent.get(1).getValuesMap();
        assertEquals("Google", firstLink.get("text"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) firstLink.get("marks");
        Map<String, Object> mark = marks.get(0).getValuesMap();
        assertEquals("link", mark.get("type"));

        ComplexIssueInputFieldValue attrsValue = (ComplexIssueInputFieldValue) mark.get("attrs");
        Map<String, Object> attrs = attrsValue.getValuesMap();
        assertEquals("https://google.com", attrs.get("href"));
    }

    @Test
    public void testComplexExample() {
        String wikiText =
                "h3. How to reproduce\n{noformat}\nfoo\n{noformat}\n\nh2. This is the AC\n\n* Step one\n* Step two";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        // Should have: h3, codeBlock, h2, bulletList
        assertEquals(4, content.size());
        assertEquals("heading", content.get(0).getValuesMap().get("type"));
        assertEquals("codeBlock", content.get(1).getValuesMap().get("type"));
        assertEquals("heading", content.get(2).getValuesMap().get("type"));
        assertEquals("bulletList", content.get(3).getValuesMap().get("type"));
    }

    @Test
    public void testHorizontalRule() {
        String wikiText = "Above\n----\nBelow";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        assertEquals(3, content.size());
        assertEquals("paragraph", content.get(0).getValuesMap().get("type"));
        assertEquals("rule", content.get(1).getValuesMap().get("type"));
        assertEquals("paragraph", content.get(2).getValuesMap().get("type"));
    }

    @Test
    public void testPlainTextFallback() {
        String wikiText = "Just plain text without any markup.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        assertEquals(1, content.size());
        Map<String, Object> paragraph = content.get(0).getValuesMap();
        assertEquals("paragraph", paragraph.get("type"));
    }

    @Test
    public void testEmptyString() {
        String wikiText = "";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        // Should have at least one empty paragraph
        assertEquals(1, content.size());
        assertEquals("paragraph", content.get(0).getValuesMap().get("type"));
    }

    @Test
    public void testCodeBlockWithLanguage() {
        String wikiText = "{code:java}\npublic void test() {}\n{code}";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        assertEquals(1, content.size());
        Map<String, Object> codeBlock = content.get(0).getValuesMap();
        assertEquals("codeBlock", codeBlock.get("type"));
    }
}
