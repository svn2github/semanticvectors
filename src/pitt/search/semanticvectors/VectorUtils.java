/**
   Copyright (c) 2007, University of Pittsburgh

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

package pitt.search.semanticvectors;

import java.lang.Math;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Enumeration;
import java.util.Random;

/**
 * This class provides standard vector methods, e.g., cosine measure,
 * normalization, tensor utils.
 */
public class VectorUtils{

	/**
	 * Check whether a vector is all zeros.
	 */
	static final float kTolerance = 0.0001f; 
	public static boolean isZeroVector(float[] vec) {
		for (int i = 0; i < vec.length; ++i) {
			if (Math.abs(vec[i]) > kTolerance) {
				return false;
			}
		}
		return true;
	}

	public static boolean isZeroTensor(float[][] ten) {
		for (int i = 0; i < ten.length; ++i) {
			if (!isZeroVector(ten[i])) {
				return false;
			}
		}
		return true;
	}

	public static float[][] createZeroTensor(int dim) {
		float[][] newTensor = new float[dim][dim];
		for (int i = 0; i < dim; ++i) {
			for (int j = 0; j < dim; ++j) {
				newTensor[i][j] = 0;
			}
		}
		return newTensor;
	}

	/**
	 * Returns the scalar product (dot product) of two vectors
	 * for normalized vectors this is the same as cosine similarity.
	 * @param vec1 First vector.
	 * @param vec2 Second vector.
	 */
	public static float scalarProduct(float[] vec1, float[] vec2){
		float result = 0;
		for (int i = 0; i < vec1.length; ++i) {
	    result += vec1[i] * vec2[i];
		}
		return result;
	}

	/**
	 * Returns the normalized version of a vector, i.e. same direction,
	 * unit length.
	 * @param vec Vector whose normalized version is requested.
	 */
	public static float[] getNormalizedVector(float[] vec){
		float norm = 0;
		int i;
		float[] tmpVec = new float[vec.length];
		for( i=0; i<vec.length; i++ ){
	    tmpVec[i] = vec[i];
		}
		for( i=0; i<tmpVec.length; i++ ){
	    norm += tmpVec[i]*tmpVec[i];
		}
		norm = (float)Math.sqrt(norm);
		for( i=0; i<tmpVec.length; i++ ){
	    tmpVec[i] = tmpVec[i]/norm;
		}
		return tmpVec;
	}

	/**
	 * Returns the normalized version of a 2 tensor, i.e. an array of
	 * arrays of floats.
	 */
	public static float[][] getNormalizedTensor(float[][] tensor){
		int dim = tensor[0].length;
		float[][] normedTensor = new float[dim][dim];
		float norm = (float)Math.sqrt(getInnerProduct(tensor, tensor));
		for (int i = 0; i < dim; ++i) {
	    for (int j = 0; j < dim; ++j) {
				normedTensor[i][j] = tensor[i][j]/norm;
	    }
		}
		return normedTensor;
	}

	/**
	 * Returns a 2-tensor which is the outer product of 2 vectors.
	 */
	public static float[][] getOuterProduct(float[] vec1, float[] vec2) {
		int dim = vec1.length;
		float[][] outProd = new float[dim][dim];
		for (int i=0; i<dim; ++i) {
	    for (int j=0; j<dim; ++j) {
				outProd[i][j] = vec1[i] * vec2[j];
	    }
		}
		return outProd;
	}

	/** 
	 * Returns te sum of two tensors.  
	 */
	public static float[][]	getTensorSum(float[][] ten1, float[][] ten2) {
		int dim = ten1[0].length;
		float[][] result = new float[dim][dim];
		for (int i = 0; i < dim; ++i) {
	    for (int j = 0; j < dim; ++j) {
				result[i][j] += ten1[i][j] + ten2[i][j];
	    }
		}
		return result;
	}

	/**
	 * Returns the inner product of two tensors.
	 */
	public static float getInnerProduct(float[][] ten1, float[][]ten2){
		float result = 0;
		int dim = ten1[0].length;
		for (int i = 0; i < dim; ++i) {
	    for (int j = 0; j < dim; ++j) {
				result += ten1[i][j] * ten2[j][i];
	    }
		}
		return result;
	}

	/**
	 * Returns the convolution of two vectors; see Plate,
	 * Holographic Reduced Representation, p. 76.
	 */
	public static float[] getConvolutionFromTensor(float[][] tensor){
		int dim = tensor.length;
		float[] conv = new float[2*dim - 1];
		for (int i = 0; i < dim; ++i) {
	    conv[i] = 0;
	    conv[conv.length - 1 - i] = 0;
	    for (int j = 0; j <= i; ++j) {
				// Count each pair of diagonals.
				// TODO(widdows): There may be transpose conventions to check.
				conv[i] += tensor[i-j][j]; 
				if (i != dim - 1) { // Avoid counting lead diagonal twice.
					conv[conv.length - 1 - i] = tensor[dim-1-i+j][dim-1-j];
				}
	    }
		}
		return VectorUtils.getNormalizedVector(conv);
	}

	/**
	 * Returns the convolution of two vectors; see Plate,
	 * Holographic Reduced Representation, p. 76.
	 */
	public static float[] getConvolutionFromVectors(float[] vec1, float[] vec2){
		int dim = vec1.length;
		float[] conv = new float[2*dim - 1];
		for (int i = 0; i < dim; ++i) {
	    conv[i] = 0;
	    conv[conv.length - 1 - i] = 0;
	    for (int j = 0; j <= i; ++j) {
				// Count each pair of diagonals.
				conv[i] += vec1[i-j] * vec2[j];
				if (i != dim - 1) { // Avoid counting lead diagonal twice.
					conv[conv.length - 1 - i] = vec1[dim-1-i+j] * vec2[dim-1-j];
				}
	    }
		}
		return VectorUtils.getNormalizedVector(conv);
	}


	/**
	 * The orthogonalize function takes an array of vectors and
	 * orthogonalizes them using the Gram-Schmidt process. The vectors
	 * are orthogonalized in place, so there is no return value.  Note
	 * that the output of this function is order dependent, in
	 * particular, the jth vector in the array will be made orthogonal
	 * to all the previous vectors. Since this means that the last
	 * vector is orthogonal to all the others, this can be used as a
	 * negation function to give an vector for
	 * vectors[last] NOT (vectors[0] OR ... OR vectors[last - 1].
	 *
	 * @param vectors Array of vectors (which are themselves arrays of
	 * floats) to be orthogonalized in place.
	 */
	public static boolean orthogonalizeVectors(ArrayList<float[]> vectors) {
		vectors.set(0, getNormalizedVector(vectors.get(0)));
		/* Go up through vectors in turn, parameterized by k */
		for (int k = 0; k < vectors.size(); ++k) {
	    float[] kthVector = vectors.get(k);
	    if (kthVector.length != ObjectVector.vecLength) {
				System.err.println("In orthogonalizeVector: not all vectors have required dimension.");
				return false;
	    }
	    /* Go up to vector k, parameterized by j. */
	    for (int j = 0; j < k ; ++j) {
				float[] jthVector = vectors.get(j);
				float dotProduct = scalarProduct(kthVector, jthVector);
				/* Subtract relevant amount from kth vector. */
				for (int i = 0 ; i < ObjectVector.vecLength; ++i) {
					kthVector[i] -= dotProduct * jthVector[i];
				}
	    }
			/* normalize the vector we're working on */
			vectors.set(k, getNormalizedVector(kthVector));
		}
		return true;
	}

  /**
   * Generates a basic sparse vector (dimension = ObjectVector.vecLength)
   * with mainly zeros and some 1 and -1 entries (seedLength/2 of each)
   * each vector is an array of length seedLength containing 1+ the index of a non-zero
   * value, signed according to whether this is a + or -1.
   * <br>
   * e.g. +20 would indicate a +1 in position 19, +1 would indicate a +1 in position 0.
   *      -20 would indicate a -1 in position 19, -1 would indicate a -1 in position 0.
   * <br>
   * The extra offset of +1 is because position 0 would be unsigned,
   * and would therefore be wasted. Consequently we've chosen to make
   * the code slightly more complicated to make the implementation
   * slightly more space efficient.
   *
   * @return Sparse representation of basic ternary vector. Array of
   * short signed integers, indices to the array locations where a
   * +/-1 entry is located.
   */
	public static short[] generateRandomVector(int seedLength, Random random) {
    boolean[] randVector = new boolean[ObjectVector.vecLength];
    short[] randIndex = new short[seedLength];

    int testPlace, entryCount = 0;

    /* put in +1 entries */
    while(entryCount < seedLength / 2 ){
      testPlace = random.nextInt(ObjectVector.vecLength);
      if( !randVector[testPlace]){
        randVector[testPlace] = true;
        randIndex[entryCount] = new Integer(testPlace + 1).shortValue();
        entryCount++;
      }
    }

    /* put in -1 entries */
    while(entryCount < seedLength ){
      testPlace = random.nextInt (ObjectVector.vecLength);
      if( !randVector[testPlace]){
        randVector[testPlace] = true;
        randIndex[entryCount] = new Integer((1 + testPlace) * -1).shortValue();
        entryCount++;
      }
    }
		
    return randIndex;
  }
}

