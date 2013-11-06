/*
 * The MIT License
 * 
 * Copyright (c) 2012 Steven G. Brown
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.timestamper.annotator;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.model.Run;
import hudson.plugins.timestamper.TimestampFormatter;
import hudson.plugins.timestamper.TimestamperTestAssistant;
import hudson.plugins.timestamper.io.TimestampsWriter;
import hudson.plugins.timestamper.io.TimestampsWriterImpl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.SerializationUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.io.Files;

/**
 * Unit test for the {@link TimestampAnnotator} class.
 * 
 * @author Steven G. Brown
 */
@RunWith(Parameterized.class)
public class TimestampAnnotatorTest {

  private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d|\\.)+");

  /**
   * @return parameterised test data
   */
  @SuppressWarnings("boxing")
  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { 0, Arrays.asList("0ab<br>", "1cd<br>", "2ef<br>") },
        { 1, Arrays.asList("b<br>", "1cd<br>", "2ef<br>") },
        { 2, Arrays.asList("<br>", "1cd<br>", "2ef<br>") },
        { 3, Arrays.asList("1cd<br>", "2ef<br>") },
        { 4, Arrays.asList("d<br>", "2ef<br>") },
        { 5, Arrays.asList("<br>", "2ef<br>") },
        { 6, Arrays.asList("2ef<br>") }, { 7, Arrays.asList("f<br>") },
        { 8, Arrays.asList("<br>") } });
  }

  /**
   */
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private Run<?, ?> build;

  private List<String> consoleLogLines;

  private TimestampFormatter formatter;

  private int offset;

  private List<String> expectedResult;

  private List<String> expectedResultNoTimestamps;

  /**
   * @param offset
   * @param result
   */
  public TimestampAnnotatorTest(int offset, List<String> result) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    this.formatter = new TimestampFormatter("S", "", request);

    this.offset = offset;

    this.expectedResultNoTimestamps = new ArrayList<String>(result.size());
    for (String line : result) {
      expectedResultNoTimestamps.add(NUMBER_PATTERN.matcher(line)
          .replaceAll(""));
    }

    this.expectedResult = new ArrayList<String>(result.size());
    for (String line : result) {
      this.expectedResult.add(NUMBER_PATTERN.matcher(line).replaceAll(
          TimestamperTestAssistant.span("$0")));
    }
  }

  /**
   * @throws Exception
   */
  @Before
  public void setUp() throws Exception {
    final byte[] consoleLogContents = new byte[] { 'a', 'b', 0x0A, 'c', 'd',
        0x0A, 'e', 'f', 0x0A };
    File consoleLog = folder.newFile();
    Files.write(consoleLogContents, consoleLog);
    consoleLogLines = Arrays.asList("ab<br>", "b<br>", "<br>", "cd<br>",
        "d<br>", "<br>", "ef<br>", "f<br>", "<br>");

    build = mock(Run.class);
    when(build.getRootDir()).thenReturn(folder.getRoot());
    when(build.getLogInputStream()).thenAnswer(new Answer<InputStream>() {
      public InputStream answer(InvocationOnMock invocation) throws Throwable {
        return new ByteArrayInputStream(consoleLogContents);
      }
    });
    when(build.getLogFile()).thenReturn(consoleLog);
  }

  /**
   * @throws Exception
   */
  @Test
  public void testAnnotate() throws Exception {
    writeTimestamps();
    assertThat(annotate(offset, false), is(expectedResult));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testAnnotateNegativeOffset() throws Exception {
    writeTimestamps();
    assertThat(annotateNegativeOffset(offset, false), is(expectedResult));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testAnnotateWithSerialization() throws Exception {
    writeTimestamps();
    assertThat(annotate(offset, true), is(expectedResult));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testAnnotateNegativeOffsetWithSerialization() throws Exception {
    writeTimestamps();
    assertThat(annotateNegativeOffset(offset, true), is(expectedResult));
  }

  /**
   */
  @Test
  public void testNoTimestamps() {
    List<String> annotated = annotate(offset, false);
    assertThat(annotated,
        is(expectedResultNoTimestamps.subList(0, annotated.size())));
  }

  /**
   */
  @Test
  public void testNoTimestampsNegativeOffset() {
    List<String> annotated = annotateNegativeOffset(offset, false);
    assertThat(annotated,
        is(expectedResultNoTimestamps.subList(0, annotated.size())));
  }

  /**
   */
  @Test
  public void testNoTimestampsWithSerialization() {
    List<String> annotated = annotate(offset, true);
    assertThat(annotated,
        is(expectedResultNoTimestamps.subList(0, annotated.size())));
  }

  /**
   */
  @Test
  public void testNoTimestampsNegativeOffsetWithSerialization() {
    List<String> annotated = annotateNegativeOffset(offset, true);
    assertThat(annotated,
        is(expectedResultNoTimestamps.subList(0, annotated.size())));
  }

  private void writeTimestamps() throws Exception {
    TimestampsWriter writer = new TimestampsWriterImpl(build);
    try {
      for (int i = 0; i < 3; i++) {
        writer.write(TimeUnit.MILLISECONDS.toNanos(i), i, 1);
      }
    } finally {
      writer.close();
    }
  }

  @SuppressWarnings("rawtypes")
  private List<String> annotate(int offset, boolean serializeAnnotator) {
    ConsoleAnnotator annotator = new TimestampAnnotator(formatter, offset);
    return annotate(offset, annotator, serializeAnnotator);
  }

  @SuppressWarnings("rawtypes")
  private List<String> annotateNegativeOffset(int offset,
      boolean serializeAnnotator) {
    long negativeOffset = offset - build.getLogFile().length();
    ConsoleAnnotator annotator = new TimestampAnnotator(formatter,
        negativeOffset);
    return annotate(offset, annotator, serializeAnnotator);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private List<String> annotate(int offset, ConsoleAnnotator annotator,
      boolean serializeAnnotator) {
    List<String> result = new ArrayList<String>();
    while (annotator != null && offset < consoleLogLines.size()) {
      MarkupText markupText = new MarkupText(consoleLogLines.get(offset));
      if (serializeAnnotator) {
        annotator = (ConsoleAnnotator) SerializationUtils.clone(annotator);
      }
      annotator = annotator.annotate(build, markupText);
      result.add(markupText.toString(false).replace("&lt;", "<"));
      offset += 3 - (offset % 3);
    }
    return result;
  }
}
