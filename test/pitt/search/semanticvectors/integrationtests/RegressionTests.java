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
import java.util.logging.*;
import java.util.*;

import org.junit.*;

import pitt.search.semanticvectors.BuildIndex;
import pitt.search.semanticvectors.BuildPositionalIndex;
import pitt.search.semanticvectors.integrationtests.RunTests;
import static org.junit.Assert.*;

/**
 * A collection of regression tests, to make sure that new options and
 * features don't break existing usage patterns.
 *
 * Should be run using "ant run-tests", which will run unit tests and
 * regression using the RunTests class working in the
 * test/testdata/tmp directory. Depends on there being appropriate
 * Lucene indexes in this directory, which are prepared by the
 * RunTests class.
 */
public class RegressionTests {
  private static Logger logger = Logger.getLogger("RegressionTests");

  @Before
  public void setUp() {
    assert(RunTests.prepareTestData());
  }

  private int buildSearchGetRank(String buildCmd, String searchCmd, String targetResult) {
    String[] buildArgs = buildCmd.split("\\s+");
    String[] searchArgs = searchCmd.split("\\s+");
    assert(!(new File("termvectors.bin")).isFile());
    assert(!(new File("docvectors.bin")).isFile());
    BuildIndex.main(buildArgs);
    assert((new File("termvectors.bin")).isFile());
    assert((new File("docvectors.bin")).isFile());
    Scanner results = TestUtils.getCommandOutput(
        pitt.search.semanticvectors.Search.class, searchArgs);
    int rank = 1;
    while (results.hasNext()) {
      String nextTerm = TestUtils.termFromResult(results.next());
      if (nextTerm.equals(targetResult)) break;
      ++rank;
    }  
    results.close();
    new File("termvectors.bin").delete();
    new File("docvectors.bin").delete();
    return rank;
  }
  
  @Test
  public void testBuildAndSearchBasicRealIndex() {
    assertEquals(2, buildSearchGetRank("-dimension 200 index", "peter", "simon"));
  }

  @Test
  public void testBuildAndSearchBasicComplexIndex() {
    assertEquals(2, buildSearchGetRank(
        "-dimension 200 -vectortype complex index", "peter", "simon"));
  }

  @Test
  public void testBuildAndSearchBasicBinaryIndex() {
    assertEquals(2, buildSearchGetRank(
        "-dimension 8192 -seedlength 128 -vectortype binary index", "peter", "simon"));
  }

  private int positionalBuildSearchGetRank(
      String buildCmd, String searchCmd, String[] filesToBuild, String targetResult) {
    String[] buildArgs = buildCmd.split("\\s+");
    String[] searchArgs = searchCmd.split("\\s+");
    for (String fn : filesToBuild) assertFalse(new File(fn).isFile());

    BuildPositionalIndex.main(buildArgs);
    for (String fn : filesToBuild) assertTrue(new File(fn).isFile());

    Scanner results = TestUtils.getCommandOutput(
        pitt.search.semanticvectors.Search.class, searchArgs);
    int rank = 1;
    while (results.hasNext()) {
      String result = results.next();
      String nextTerm = TestUtils.termFromResult(result);
      if (nextTerm.equals(targetResult)) break;
      ++rank;
    }  
    results.close();
    for (String fn : filesToBuild) assertTrue(new File(fn).delete());
    return rank;
  }

  @Test
  public void testBuildAndSearchRealPositionalIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 200 -vectortype real -seedlength 10 positional_index",
        "-queryvectorfile termtermvectors.bin simon",
        new String[] {"termtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
    assertTrue(peterRank < 5);
  }

  @Test
  public void testBuildAndSearchBinaryPositionalIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 8192 -vectortype binary -seedlength 128 positional_index",
        "-queryvectorfile termtermvectors.bin simon",
        new String[] {"termtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
       assertTrue(peterRank < 5);
  }
  
  @Test
  public void testBuildAndSearchComplexPositionalIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 200 -vectortype complex -seedlength 10 positional_index",
        "-queryvectorfile termtermvectors.bin simon",
        new String[] {"termtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
    assertTrue(peterRank < 5);
  }
  
  @Test
  public void testBuildAndSearchRealPermutationIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 200 -vectortype real -seedlength 10 -positionalmethod permutation positional_index",
        "-searchtype permutation -queryvectorfile randomvectors.bin -searchvectorfile permtermvectors.bin simon ?",
        new String[] {"randomvectors.bin", "permtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
    assertEquals(1, peterRank);
  }
  
  @Test
  public void testBuildAndSearchComplexPermutationIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 200 -vectortype complex -seedlength 10 -positionalmethod permutation positional_index",
        "-searchtype permutation -queryvectorfile randomvectors.bin -searchvectorfile permtermvectors.bin simon ?",
        new String[] {"randomvectors.bin", "permtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
    assertEquals(1, peterRank);
  }

  
  @Test
  public void testBuildAndSearchBinaryPermutationIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 16834 -vectortype binary -seedlength 8192 -positionalmethod permutation positional_index",
        "-searchtype permutation -queryvectorfile randomvectors.bin -searchvectorfile permtermvectors.bin simon ?",
        new String[] {"randomvectors.bin", "permtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
    assertEquals(2, peterRank);
  }
  

  @Test
  public void testBuildAndSearchRealBalancedPermutationIndex() {
    int peterRank = positionalBuildSearchGetRank(
        "-dimension 200 -vectortype real -seedlength 10 -positionalmethod permutation positional_index",
        "-searchtype permutation -queryvectorfile randomvectors.bin -searchvectorfile permtermvectors.bin -searchtype balanced_permutation simon ?",
        new String[] {"randomvectors.bin", "permtermvectors.bin", "incremental_docvectors.bin"},
        "peter");
    assertEquals(1, peterRank);
  }
}
