package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.*;

import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    @Test
    public void testCarriageReturnNormalization() {
        // Test that \r\n is normalized to \n and \r is removed
        String wikiText = "Line one\r\nLine two\rLine three\nLine four";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        // Should create a single paragraph with hardBreaks
        assertEquals(1, content.size());
        Map<String, Object> paragraph = content.get(0).getValuesMap();
        assertEquals("paragraph", paragraph.get("type"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> paraContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should have: text, hardBreak, text, hardBreak, text, hardBreak, text
        assertEquals(7, paraContent.size());
        assertEquals("text", paraContent.get(0).getValuesMap().get("type"));
        assertEquals("hardBreak", paraContent.get(1).getValuesMap().get("type"));
    }

    @Test
    public void testStrikethroughNotOnHyphens() {
        // Test that hyphens in hyphenated words are not treated as strikethrough
        String wikiText = "Use auto-resolve and re-test the fix.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> paraContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should be a single text node without any marks
        assertEquals(1, paraContent.size());
        Map<String, Object> textNode = paraContent.get(0).getValuesMap();
        assertEquals("text", textNode.get("type"));
        assertEquals("Use auto-resolve and re-test the fix.", textNode.get("text"));
        assertNull(textNode.get("marks"), "Hyphens should not create strike marks");
    }

    @Test
    public void testStrikethroughWithWhitespace() {
        // Test that strikethrough works when preceded by whitespace
        String wikiText = "This is -strikethrough- text.";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> paraContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should have: "This is ", strikethrough text, " text."
        assertEquals(3, paraContent.size());

        Map<String, Object> strikeText = paraContent.get(1).getValuesMap();
        assertEquals("strikethrough", strikeText.get("text"));
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) strikeText.get("marks");
        assertEquals("strike", marks.get(0).getValuesMap().get("type"));
    }

    @Test
    public void testUnderscoreInVariableReference() {
        // Test that underscores in variable names are not treated as italic delimiters
        String wikiText = "This failed in ${BUILD_NUMBER} on ${HOST_NAME}";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> paraContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should be a single text node without any marks
        assertEquals(1, paraContent.size());
        Map<String, Object> textNode = paraContent.get(0).getValuesMap();
        assertEquals("text", textNode.get("type"));
        assertEquals("This failed in ${BUILD_NUMBER} on ${HOST_NAME}", textNode.get("text"));
        assertNull(textNode.get("marks"), "Underscores in variable names should not create italic marks");
    }

    @Test
    public void testMixedUnderscoresAndItalics() {
        // Test that both italic underscores and variable underscores work correctly
        String wikiText = "This is _italic_ text with ${BUILD_NUMBER} variable";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> paraContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should have: "This is ", italic text with mark, " text with ${BUILD_NUMBER} variable"
        assertEquals(3, paraContent.size());

        // First text node
        Map<String, Object> textNode1 = paraContent.get(0).getValuesMap();
        assertEquals("This is ", textNode1.get("text"));

        // Italic text node
        Map<String, Object> italicText = paraContent.get(1).getValuesMap();
        assertEquals("italic", italicText.get("text"));
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) italicText.get("marks");
        assertEquals("em", marks.get(0).getValuesMap().get("type"));

        // Final text node with variable
        Map<String, Object> textNode2 = paraContent.get(2).getValuesMap();
        assertEquals(" text with ${BUILD_NUMBER} variable", textNode2.get("text"));
        assertNull(textNode2.get("marks"), "Variable underscore should not create marks");
    }

    @Test
    public void testNewlinesConvertedToHardBreak() {
        // Test that newlines within paragraphs are converted to hardBreak nodes
        String wikiText = "Line 1\nLine 2\nLine 3";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        assertEquals(1, content.size());
        Map<String, Object> paragraph = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> paraContent = (List<ComplexIssueInputFieldValue>) paragraph.get("content");

        // Should have: text, hardBreak, text, hardBreak, text
        assertEquals(5, paraContent.size());
        assertEquals("text", paraContent.get(0).getValuesMap().get("type"));
        assertEquals("hardBreak", paraContent.get(1).getValuesMap().get("type"));
        assertEquals("text", paraContent.get(2).getValuesMap().get("type"));
        assertEquals("hardBreak", paraContent.get(3).getValuesMap().get("type"));
        assertEquals("text", paraContent.get(4).getValuesMap().get("type"));
    }

    @Test
    public void testNoEmptyTextNodes() {
        // Test that empty strings don't create empty text nodes
        String wikiText = "h1. Title";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        Map<String, Object> heading = content.get(0).getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> headingContent = (List<ComplexIssueInputFieldValue>) heading.get("content");

        // Verify all text nodes have non-empty text
        for (ComplexIssueInputFieldValue node : headingContent) {
            Map<String, Object> nodeMap = node.getValuesMap();
            if ("text".equals(nodeMap.get("type"))) {
                String text = (String) nodeMap.get("text");
                assertFalse(text.isEmpty(), "Text nodes should not be empty");
            }
        }
    }

    @Test
    public void testUnclosedCodeBlock() {
        // Test that unclosed code blocks don't swallow all content
        String wikiText = "{code}\nSome code\nMore content after";
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);

        Map<String, Object> doc = result.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) doc.get("content");

        // Should have one code block containing all remaining content
        assertEquals(1, content.size());
        Map<String, Object> codeBlock = content.get(0).getValuesMap();
        assertEquals("codeBlock", codeBlock.get("type"));

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> codeContent = (List<ComplexIssueInputFieldValue>) codeBlock.get("content");
        String code = (String) codeContent.get(0).getValuesMap().get("text");
        assertTrue(code.contains("Some code"), "Code block should contain the code");
        assertTrue(code.contains("More content after"), "Unclosed block should include remaining content");
    }

    @Test
    public void testComprehensivePipelineDescription() throws Exception {
        // This tests the full description from a realistic Jenkins pipeline configuration
        // with all 6 heading levels, code blocks, lists, links, and inline formatting
        String wikiText = "h1. Test Failure Report\n"
                + "This test failed in build ${BUILD_NUMBER}\n\n"
                + "h2. Error Details\n"
                + "The following error was encountered:\n\n"
                + "{noformat}\n"
                + "java.lang.AssertionError: Expected value did not match\n"
                + "    at test.Example.testMethod(Example.java:42)\n"
                + "{noformat}\n\n"
                + "h3. How to Reproduce\n"
                + "Follow these steps to reproduce the issue:\n\n"
                + "# Clone the repository from [GitHub|https://github.com/imonteroperez/simple-maven-project-with-tests]\n"
                + "# Run {{mvn clean test}}\n"
                + "# Observe the *failure* in the test suite\n\n"
                + "h4. Environment Information\n\n"
                + "* *Operating System:* Linux\n"
                + "* *Java Version:* 17\n"
                + "* *Maven Version:* 3.8.6\n"
                + "* *Jenkins URL:* [${BUILD_URL}]\n\n"
                + "h5. Code Sample\n\n"
                + "Example code that triggers the issue:\n\n"
                + "{code:java}\n"
                + "public void testExample() {\n"
                + "    assertEquals(\"expected\", actual);\n"
                + "}\n"
                + "{code}\n\n"
                + "h6. Additional Notes\n\n"
                + "The issue appears to be related to _timing_ or -configuration- problems.\n\n"
                + "Text formatting examples:\n"
                + "* *Bold text* for emphasis\n"
                + "* _Italic text_ for notes\n"
                + "* {{monospace}} for code references\n"
                + "* -Strikethrough- for deprecated items\n\n"
                + "----\n\n"
                + "For more details, see the [Jenkins Build|${BUILD_URL}] or contact the development team.\n";

        // Convert wiki text to ADF
        ComplexIssueInputFieldValue result = AdfFieldConverter.convertToADF(wikiText);
        Map<String, Object> actualDoc = result.getValuesMap();

        // Unwrap ComplexIssueInputFieldValue objects to get plain Map structure
        Map<String, Object> unwrappedActual = unwrapComplexValues(actualDoc);

        // Load expected JSON from resources
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = getClass().getResourceAsStream("/expected-pipeline-adf.json");
        assertNotNull(inputStream, "Expected ADF JSON resource should exist");

        String expectedJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JsonNode expectedNode = mapper.readTree(expectedJson);

        // Convert unwrapped actual result to JSON for comparison
        String actualJson = mapper.writeValueAsString(unwrappedActual);
        JsonNode actualNode = mapper.readTree(actualJson);

        // Compare the two JSON structures
        assertEquals(expectedNode, actualNode, "Generated ADF should match expected structure");

        // Additional validation - verify key properties
        assertEquals(1, actualDoc.get("version"), "ADF version should be 1");
        assertEquals("doc", actualDoc.get("type"), "Root type should be 'doc'");

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) actualDoc.get("content");
        assertEquals(19, content.size(), "Should have exactly 19 content nodes");
    }

    /**
     * Recursively unwraps ComplexIssueInputFieldValue objects to get plain Map/List structure.
     * This is needed because ComplexIssueInputFieldValue wraps values in a valuesMap property.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapComplexValues(Map<String, Object> map) {
        Map<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ComplexIssueInputFieldValue) {
                result.put(entry.getKey(), unwrapComplexValues(((ComplexIssueInputFieldValue) value).getValuesMap()));
            } else if (value instanceof List) {
                result.put(entry.getKey(), unwrapList((List<?>) value));
            } else if (value instanceof Map) {
                result.put(entry.getKey(), unwrapComplexValues((Map<String, Object>) value));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * Recursively unwraps ComplexIssueInputFieldValue objects in a list.
     */
    @SuppressWarnings("unchecked")
    private List<Object> unwrapList(List<?> list) {
        List<Object> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof ComplexIssueInputFieldValue) {
                result.add(unwrapComplexValues(((ComplexIssueInputFieldValue) item).getValuesMap()));
            } else if (item instanceof List) {
                result.add(unwrapList((List<?>) item));
            } else if (item instanceof Map) {
                result.add(unwrapComplexValues((Map<String, Object>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private void assertHeading(ComplexIssueInputFieldValue node, int level, String text) {
        Map<String, Object> heading = node.getValuesMap();
        assertEquals("heading", heading.get("type"), "Node should be a heading");

        ComplexIssueInputFieldValue attrsValue = (ComplexIssueInputFieldValue) heading.get("attrs");
        assertEquals(level, attrsValue.getValuesMap().get("level"), "Heading level should be " + level);

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) heading.get("content");
        assertTrue(content.size() > 0, "Heading should have content");
        String actualText = (String) content.get(0).getValuesMap().get("text");
        assertEquals(text, actualText, "Heading text should match");
    }

    private void assertParagraphContains(ComplexIssueInputFieldValue node, String expectedText) {
        Map<String, Object> paragraph = node.getValuesMap();
        assertEquals("paragraph", paragraph.get("type"), "Node should be a paragraph");

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) paragraph.get("content");
        assertTrue(content.size() > 0, "Paragraph should have content");

        String actualText = (String) content.get(0).getValuesMap().get("text");
        assertTrue(
                actualText.contains(expectedText),
                "Paragraph should contain '" + expectedText + "', got: " + actualText);
    }

    private void assertCodeBlock(ComplexIssueInputFieldValue node, String expectedCode) {
        Map<String, Object> codeBlock = node.getValuesMap();
        assertEquals("codeBlock", codeBlock.get("type"), "Node should be a code block");

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) codeBlock.get("content");
        String code = (String) content.get(0).getValuesMap().get("text");
        assertEquals(expectedCode, code, "Code block content should match");
    }

    private void assertOrderedList(ComplexIssueInputFieldValue node, int expectedItems) {
        Map<String, Object> list = node.getValuesMap();
        assertEquals("orderedList", list.get("type"), "Node should be an ordered list");

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> items = (List<ComplexIssueInputFieldValue>) list.get("content");
        assertEquals(expectedItems, items.size(), "Ordered list should have " + expectedItems + " items");
    }

    private void assertBulletList(ComplexIssueInputFieldValue node, int expectedItems) {
        Map<String, Object> list = node.getValuesMap();
        assertEquals("bulletList", list.get("type"), "Node should be a bullet list");

        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> items = (List<ComplexIssueInputFieldValue>) list.get("content");
        assertEquals(expectedItems, items.size(), "Bullet list should have " + expectedItems + " items");
    }

    private void assertListItemContainsLink(
            ComplexIssueInputFieldValue listNode, int itemIndex, String linkText, String href) {
        Map<String, Object> list = listNode.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> items = (List<ComplexIssueInputFieldValue>) list.get("content");
        Map<String, Object> item = items.get(itemIndex).getValuesMap();

        assertTrue(checkForMark(item, "link"), "List item should contain a link");
        // Additional check for href would require deeper traversal
    }

    private void assertListItemContainsMark(ComplexIssueInputFieldValue listNode, int itemIndex, String markType) {
        Map<String, Object> list = listNode.getValuesMap();
        @SuppressWarnings("unchecked")
        List<ComplexIssueInputFieldValue> items = (List<ComplexIssueInputFieldValue>) list.get("content");
        Map<String, Object> item = items.get(itemIndex).getValuesMap();

        assertTrue(checkForMark(item, markType), "List item should contain mark type: " + markType);
    }

    /**
     * Helper method to find a node by type.
     */
    private Map<String, Object> findNodeByType(List<ComplexIssueInputFieldValue> content, String type) {
        for (ComplexIssueInputFieldValue node : content) {
            Map<String, Object> nodeMap = node.getValuesMap();
            if (type.equals(nodeMap.get("type"))) {
                return nodeMap;
            }
        }
        return null;
    }

    /**
     * Helper method to find a heading node by level.
     */
    private Map<String, Object> findNodeByTypeAndText(
            List<ComplexIssueInputFieldValue> content, String type, int level) {
        for (ComplexIssueInputFieldValue node : content) {
            Map<String, Object> nodeMap = node.getValuesMap();
            if (type.equals(nodeMap.get("type")) && nodeMap.containsKey("attrs")) {
                ComplexIssueInputFieldValue attrsValue = (ComplexIssueInputFieldValue) nodeMap.get("attrs");
                Map<String, Object> attrs = attrsValue.getValuesMap();
                if (level == (int) attrs.get("level")) {
                    return nodeMap;
                }
            }
        }
        return null;
    }

    /**
     * Helper method to check if a node contains a specific mark type.
     */
    private boolean checkForMark(Map<String, Object> nodeMap, String markType) {
        if (nodeMap.containsKey("content")) {
            @SuppressWarnings("unchecked")
            List<ComplexIssueInputFieldValue> content = (List<ComplexIssueInputFieldValue>) nodeMap.get("content");
            for (ComplexIssueInputFieldValue item : content) {
                Map<String, Object> itemMap = item.getValuesMap();
                if (itemMap.containsKey("marks")) {
                    @SuppressWarnings("unchecked")
                    List<ComplexIssueInputFieldValue> marks = (List<ComplexIssueInputFieldValue>) itemMap.get("marks");
                    for (ComplexIssueInputFieldValue mark : marks) {
                        if (markType.equals(mark.getValuesMap().get("type"))) {
                            return true;
                        }
                    }
                }
                // Recursively check nested content (for list items)
                if (itemMap.containsKey("content")) {
                    if (checkForMark(itemMap, markType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
