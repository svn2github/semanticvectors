/**
   Copyright 2009, The SemanticVectors AUTHORS.
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

 * Neither the name of Google Inc. nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors.integrationtests;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.junit.*;

import pitt.search.semanticvectors.LSA;
import pitt.search.semanticvectors.Search;
import pitt.search.semanticvectors.SearchResult;
import static org.junit.Assert.*;

/**
 * Test for LSA end-to-end workings.
 *
 * Should be run using "ant run-integration-tests".
 */
public class LSATest {
  @Before
  public void setUp() {
    assertTrue(RunTests.prepareTestData());
  }

  @Test
  public void testBuildAndSearchLSAIndex() throws IOException {
    // Cut dimension to 20; only 22 docs in test data and using more dimensions than this
    // causes ArrayIndexOutOfBoundsException for SVD library.
    String buildCmd = "-dimension 20 -vectortype real -luceneindexpath positional_index";
    String[] filesToBuild = new String[] {"termvectors.bin", "docvectors.bin"};
    String[] buildArgs = buildCmd.split("\\s+");
    for (String fn : filesToBuild) {
      if (new File(fn).isFile()) {
        new File(fn).delete();
      }
      assertFalse((new File(fn)).isFile());
    }
    LSA.main(buildArgs);
    for (String fn: filesToBuild) assertTrue((new File(fn)).isFile());

    String searchCmd =
        "-queryvectorfile termvectors.bin -searchvectorfile termvectors.bin -searchtype sum simon";
    String[] searchArgs = searchCmd.split("\\s+");
    List<SearchResult> results = Search.RunSearch(searchArgs, 10);
    int rank = 1;
    if (results.isEmpty()) {
      throw new RuntimeException("Results were empty!");
    } else {
      for (SearchResult result : results) {
        String term = (String) result.getObjectVector().getObject();
        if (term.equals("peter")) break;
        ++rank;
      }
    }
    assertTrue(rank < 5);

    searchCmd = "-queryvectorfile termvectors.bin -searchvectorfile docvectors.bin pilate";
    searchArgs = searchCmd.split("\\s+");
    results = Search.RunSearch(searchArgs, 10);
    rank = 1;
    if (results.isEmpty()) {
      throw new RuntimeException("Results were empty!");
    } else {
      for (SearchResult result : results) {
        String term = (String) result.getObjectVector().getObject();
        if (term.contains("Chapter_19")) break;
        ++rank;
      }
    }
    assertTrue(rank < 5);

    for (String fn: filesToBuild) assertTrue((new File(fn)).delete());
  }
}
