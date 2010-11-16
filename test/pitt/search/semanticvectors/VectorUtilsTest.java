/**
   Copyright 2008, Google Inc.
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

package pitt.search.semanticvectors;

import java.util.ArrayList;

import junit.framework.TestCase;

import org.junit.Test;

public class VectorUtilsTest extends TestCase {

	@Test
    public void testScalarProduct() {
		System.err.println("\nRunning tests for VectorUtilsTest");
		float[] vec1 = new float[] {1, 1, 0};
		float[] vec2 = new float[] {1, 0, 1};
		assertEquals(1.0, VectorUtils.scalarProduct(vec1, vec2), 0.0001);
	}

	@Test
    public void testOrthogonalizeVectors() {
		Flags.dimension = 3;
		float[] vec1 = new float[] {1, 2, 1};
		float[] vec2 = new float[] {2, 3, 1};
		float[] vec3 = new float[] {2, 1, 1};
		ArrayList<float[]> list = new ArrayList<float[]>();
		list.add(vec1);
		list.add(vec2);
		list.add(vec3);
		VectorUtils.orthogonalizeVectors(list);

		for (int i = 0; i < list.size(); ++i) {
			VectorUtils.printVector(list.get(i));
			assertEquals(1.0, VectorUtils.scalarProduct(list.get(i), list.get(i)), 0.0001);
			for (int j = i + 1; j < list.size(); ++j) {
				assertEquals(0.0, VectorUtils.scalarProduct(list.get(i), list.get(j)), 0.0001);
			}
			assertEquals(1.0, VectorUtils.getSumScalarProduct(list.get(i), list), 0.0001);
		}
	}

	@Test
		public void testGetNLargestPositions() {
		float[] floatVector = {2.3f, 0.1f, -1.0f};
		short[] largest1 = VectorUtils.getNLargestPositions(floatVector, 1);
		assertEquals(1, largest1.length);
		assertEquals((short) 0, largest1[0]);
	}

	@Test
		public void testGetNLargestPositions2() {
		float[] floatVector2 = {1.0f, 2.3f, 0.1f, -1.0f};
		short[] largest2 = VectorUtils.getNLargestPositions(floatVector2, 2);
		assertEquals(2, largest2.length);
		assertEquals((short) 1, largest2[0]);
		assertEquals((short) 0, largest2[1]);
	}

	@Test
		public void testFloatVectorToSparseVector() {
		float[] floatVector = {1.0f, 2.3f, 0.1f, -1.0f};
		short[] sparseVector = VectorUtils.floatVectorToSparseVector(floatVector, 2);
		assertEquals(2, sparseVector.length);
		assertEquals((short) 2, sparseVector[0]);
		assertEquals((short) -4, sparseVector[1]);

		float[] floatVector2 = {1.0f, 2.3f, 0.1f, -1.0f, 0f, 0f, 0f, 0f};
		sparseVector = VectorUtils.floatVectorToSparseVector(floatVector2, 4);
		assertEquals(4, sparseVector.length);
		assertEquals((short) 2, sparseVector[0]);
		assertEquals((short) -4, sparseVector[2]);
	}

	@Test
		public void testSparseVectorToFloatVector() {
		short[] sparseVector = {2, -4};
		float[] floatVector = VectorUtils.sparseVectorToFloatVector(sparseVector, 4);
		assertEquals(4, floatVector.length);
		assertEquals(1, floatVector[1], 0.0001);
		assertEquals(-1, floatVector[3], 0.0001);
	}
}