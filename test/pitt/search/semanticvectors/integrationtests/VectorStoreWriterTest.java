/**
   Copyright 2009, SemanticVectors AUTHORS.
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

import org.junit.*;

import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreReaderLucene;
import pitt.search.semanticvectors.VectorStoreWriter;

import java.io.IOException;

public class VectorStoreWriterTest {

	@Before
		public void setUp() { assert(RunTests.prepareTestData()); }
	
	@Test
		public void TestWriteToFileUsedEarlier() {
		System.err.println("Running tests for VectorStoreWriter");
		
		VectorStoreReaderLucene fileReader;
		VectorStoreRAM ramCache = new VectorStoreRAM();

		try {
			fileReader = new VectorStoreReaderLucene(RunTests.vectorBinFile);
			ramCache.InitFromFile(RunTests.vectorBinFile);
			fileReader.close();
		} catch (IOException e) {
			System.out.println("Couldn't open and close test vector stores.");
		}

		VectorStoreWriter writer = new VectorStoreWriter();
		writer.WriteVectors(RunTests.vectorBinFile, ramCache);
	}
}