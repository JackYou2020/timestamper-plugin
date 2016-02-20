/*
 * The MIT License
 * 
 * Copyright (c) 2016 Steven G. Brown
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
package hudson.plugins.timestamper.io;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.model.AbstractBuild;
import hudson.plugins.timestamper.TimestampNote;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

/**
 * Unit test for {@link LogFileReader}.
 * 
 * @author Steven G. Brown
 */
public class LogFileReaderTest {

  /**
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private AbstractBuild<?, ?> build;

  private LogFileReader logFileReader;

  private String logFileContents;

  /**
   * @throws Exception
   */
  @Before
  public void setUp() throws Exception {
    build = mock(AbstractBuild.class);
    when(build.getLogInputStream()).thenCallRealMethod();
    when(build.getLogReader()).thenCallRealMethod();

    logFileReader = new LogFileReader(build);

    logFileContents = "line1\nline2" + new TimestampNote(0, 0).encode() + "\n";
  }

  /**
   */
  @After
  public void tearDown() {
    logFileReader.close();
  }

  /**
   * @throws Exception
   */
  @Test
  public void testNextLine_logFileExists() throws Exception {
    File logFile = tempFolder.newFile();
    Files.write(logFileContents, logFile, Charsets.UTF_8);
    when(build.getLogFile()).thenReturn(logFile);

    testNextLine();
  }

  /**
   * @throws Exception
   */
  @Test
  public void testNextLine_zippedLogFile() throws Exception {
    File logFile = tempFolder.newFile("logFile.gz");
    FileOutputStream fileOutputStream = null;
    GZIPOutputStream gzipOutputStream = null;
    try {
      fileOutputStream = new FileOutputStream(logFile);
      gzipOutputStream = new GZIPOutputStream(fileOutputStream);
      gzipOutputStream
          .write(logFileContents.getBytes(Charset.defaultCharset()));
    } finally {
      Closeables.close(gzipOutputStream, true);
      Closeables.close(fileOutputStream, true);
    }
    when(build.getLogFile()).thenReturn(logFile);

    testNextLine();
  }

  private void testNextLine() throws Exception {
    List<Optional<String>> lines = new ArrayList<Optional<String>>();
    for (int i = 0; i < 3; i++) {
      lines.add(logFileReader.nextLine());
    }
    @SuppressWarnings("unchecked")
    List<Optional<String>> expectedLines = Arrays.asList(Optional.of("line1"),
        Optional.of("line2"), Optional.<String> absent());
    assertThat(lines, is(expectedLines));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testNextLine_noLogFile() throws Exception {
    File logFile = new File(tempFolder.getRoot(), "logFile");
    when(build.getLogFile()).thenReturn(logFile);

    assertThat(logFileReader.nextLine(), is(Optional.<String> absent()));
  }
}