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
import java.util.LinkedList;
import java.util.Enumeration;

/**
 * This class provides a basic vector methods, e.g., cosine measure, normalization,
 * and nearest neighbor search.
 */
public class VectorUtils{

    /**
     * Returns the scalar product (dot product) of two vectors
     * for normalized vectors this is the same as cosine similarity.
     * @param vec1 First vector.
     * @param vec2 Second vector.
     */
    public static float scalarProduct(float[] vec1, float[] vec2){
	float result = 0;
	for( int i=0; i<vec1.length; i++ ){
	    result += vec1[i]*vec2[i];
	}
	return result;
    }

    /** 
     * Returns the normalized version of a vector, i.e. same direction, unit length.
     * @param vec Vector whose normalized version is requested.
     */
    public static float[] normalize(float[] vec){
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
     * @param queryVector the query vector: method will search for vectors closest to this
     * @param vecEnum the enumeration of ObjectVectors that will be searched
     * @param numResults the number of results / length of the result list
     */
    public static LinkedList getNearestNeighbors(float[] queryVector,
						 Enumeration<ObjectVector> vecEnum, 
						 int numResults){
	LinkedList<SearchResult> results = new LinkedList();

	float score, threshold = -1;

	while (vecEnum.hasMoreElements()) {
	    /* initialize result list */
	    if (results.size() == 0) {
		ObjectVector firstElement = vecEnum.nextElement();
		score = scalarProduct(queryVector, firstElement.getVector());
		results.add(new SearchResult(score, firstElement));
		continue;
	    }

	    /* test this element */
	    ObjectVector testElement = vecEnum.nextElement();
	    score = scalarProduct (queryVector, testElement.getVector());
	    if (score > threshold) {
		boolean added = false;
		for (int i = 0; i < results.size(); i++) {
		    /* add to list if this is right place */
		    if (score > results.get(i).getScore() && added==false) {
			results.add(i, new SearchResult(score, testElement));
			added = true;
		    }
		}
		/* prune list if reached numResults */
		if (results.size() > numResults) {
		    results.removeLast();
		    threshold = results.getLast().getScore();
		}
	    }
	}		
	return results;
    }

}