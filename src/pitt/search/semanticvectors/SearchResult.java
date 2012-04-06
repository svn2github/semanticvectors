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

/**
 * Class to represent search results.
 * 
 * @author Dominic Widdows
 */
public class SearchResult implements Comparable {
  /** The ObjectVector in the search results. */
  private ObjectVector objectVector;

  /** The score given to this object in the search. */
  private double score;
  
  public double getScore() {
    return score;
  }

  public ObjectVector getObjectVector() {
    return objectVector;
  }

  public SearchResult(double score, ObjectVector object) {
    this.score = score;
    this.objectVector = object;
  }

@Override
public int compareTo(Object arg0) {
	if (! arg0.getClass().equals(this.getClass()))
	throw new IllegalArgumentException();
	
	SearchResult otherSearchResult = (SearchResult) arg0;
	
	if (this.getScore() > otherSearchResult.getScore()) return -1;
	else if (this.getScore() < otherSearchResult.getScore()) return 1;
	else return 0;
}
}
