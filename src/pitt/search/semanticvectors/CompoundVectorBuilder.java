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

import java.util.ArrayList;
import org.apache.lucene.index.Term;

/**
 * This class contains methods for manipulating queries, e.g., taking
 * a list of queryterms and producing a (possibly weighted) aggregate
 * query vector. In the fullness of time this will hopefully include
 * parsing and building querres that include basic (quantum) logical operations.
 */
public class CompoundVectorBuilder {

    private VectorStore vecReader;
    private LuceneUtils lUtils;

    public CompoundVectorBuilder (VectorStore vecReader, LuceneUtils lUtils) {
	this.vecReader = vecReader;
	this.lUtils = lUtils;
    }

    /**
     * Method gets a query vector from a query string, i.e., a
     * space-separated list of queryterms. The method is static and
     * creates its own CompoundVectorBuilder.  This enables client
     * code just to call "getQueryVector" without creating an object
     * first, though this may be slightly less efficient for multiple
     * calls.
     * @param vecReader The vector store reader to use.
     * @param lUtils Lucene utilities for getting term weights.
     * @param queryString Query expression, e.g., from command line.
     * @return queryVector, an array of floats representing the user's query.
     */
    public static float[] getQueryVector(VectorStore vecReader,
					 LuceneUtils lUtils,
					 String queryString) {
	String[] queryTerms = queryString.split(" ");

	CompoundVectorBuilder builder = new CompoundVectorBuilder(vecReader, lUtils);
	/* Check through args to see if we need to do negation. */
	for (int i = 0; i < queryTerms.length; ++i) {
	    if (queryTerms[i].equals("NOT")) {
		/* If, so build negated query and return. */
		return builder.getNegatedQueryVector(queryTerms, i);
	    }
	}
	return builder.getAdditiveQueryVector(queryTerms);
    }

    /**
     * Returns a (possibly weighted) normalized query vector created
     * by adding together vectors retrieved from vector store.
     * @param queryTerms String array of query terms to look up.
     */
    private float[] getAdditiveQueryVector(String[] queryTerms) {
	float[] queryVec = new float[ObjectVector.vecLength];
	float[] tmpVec = new float[ObjectVector.vecLength];
	float weight = 1;

	for (int i = 0; i < ObjectVector.vecLength; ++i) {
	    queryVec[i] = 0;
	}

	for (int j = 0; j < queryTerms.length; ++j) {
	    tmpVec = vecReader.getVector(queryTerms[j]);

	    // try to get term weight; assume field is "contents"
	    if (lUtils != null) {
		weight = lUtils.getGlobalTermWeight(new Term("contents", queryTerms[j]));
	    }
	    else{ weight = 1; }

	    if (tmpVec != null) {
		System.err.println("Got vector for " + queryTerms[j] +
				   ", using term weight " + weight);
		for (int i = 0; i < ObjectVector.vecLength; ++i) {
		    queryVec[i] += tmpVec[i] * weight;
		}
	    }
	    else{ System.err.println("No vector for " + queryTerms[j]); }
	}

	queryVec = VectorUtils.getNormalizedVector(queryVec);
	return queryVec;
    }

    /**
     * Creates a vector including orthogonalizing negated terms.
     * @param queryTerms List of positive and negative terms.
     * @param split Position in this list of the NOT mark: terms
     * before this are positive, those after this are negative.
     */
    private float[] getNegatedQueryVector(String[] queryTerms, int split) {
	int numNegativeTerms = queryTerms.length - split - 1;
	int numPositiveTerms = split;
	System.err.println("Numer of negative terms: " + numNegativeTerms);
	System.err.println("Numer of positive terms: " + numPositiveTerms);
	ArrayList<float[]> vectorList = new ArrayList();
	for (int i = 1; i <= numNegativeTerms; ++i) {
	    float[] tmpVector = vecReader.getVector(queryTerms[split + i]); 
	    if (tmpVector != null) {
		vectorList.add(tmpVector); 
	    }
	}
	String[] positiveTerms = new String[numPositiveTerms];
	for (int i = 0; i < numPositiveTerms; ++i) {
	    positiveTerms[i] = queryTerms[i];
	}
	vectorList.add(getAdditiveQueryVector(positiveTerms));
	VectorUtils.orthogonalizeVectors(vectorList);
	return vectorList.get(vectorList.size() - 1);
    }
}
