/**
   Copyright (c) 2011, the SemanticVectors AUTHORS.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

   * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors.vectors;

import org.junit.Test;
import static org.junit.Assert.*;

import junit.framework.TestCase;

public class PermutationUtilsTest extends TestCase {
  
  @Test
  public void testGetShiftPermutation() {
    assertArrayEquals(new int[] {1, 0}, PermutationUtils.getShiftPermutation(VectorType.REAL, 2, 1));
    assertArrayEquals(new int[] {1, 2, 0}, PermutationUtils.getShiftPermutation(VectorType.REAL, 3, 1));
    assertArrayEquals(new int[] {2, 3, 4, 5, 0, 1}, PermutationUtils.getShiftPermutation(VectorType.REAL, 6, 2));
    assertArrayEquals(new int[] {2, 3, 4, 5, 0, 1}, PermutationUtils.getShiftPermutation(VectorType.REAL, 6, -4));
    assertArrayEquals(new int[] {2, 3, 4, 5, 0, 1}, PermutationUtils.getShiftPermutation(VectorType.REAL, 6, 14));
  }
  
  @Test
  public void testGetShiftPermutationForBinary() {
    assertArrayEquals(new int[] {1, 0}, PermutationUtils.getShiftPermutation(VectorType.BINARY, 128, 1));
    assertArrayEquals(new int[] {1, 2, 0}, PermutationUtils.getShiftPermutation(VectorType.BINARY, 192, 1));
    assertArrayEquals(new int[] {2, 3, 4, 5, 0, 1}, PermutationUtils.getShiftPermutation(VectorType.BINARY, 384, 2));
  }
}
