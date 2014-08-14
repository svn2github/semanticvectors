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

import pitt.search.semanticvectors.utils.StringUtils;

/**
 * Class to represent search results.
 *
 * @author Dominic Widdows
 */
public class SearchResult implements Comparable<SearchResult> {
  /** The ObjectVector in the search results. */
  private ObjectVector objectVector;

  /** The score given to this object in the search. */
  private double score;

  public double getScore() {
    return score;
  }

  public void set(double score, ObjectVector object) {
    this.score = score;
    this.objectVector = object;
  }

  public ObjectVector getObjectVector() {
    return objectVector;
  }

  public SearchResult(double score, ObjectVector object) {
    this.set(score, object);
  }

  @Override
  public int compareTo(SearchResult otherSearchResult) {
    if (this.getScore() > otherSearchResult.getScore()) return -1;
    else if (this.getScore() < otherSearchResult.getScore()) return 1;
    else return 0;
  }

  public String toTexTableString(int pad) {
    return String.format("%s%s&  %.3f",
        this.getObjectVector().getObject(),
        StringUtils.nSpaces(pad - this.getObjectVector().getObject().toString().length()),
        this.getScore());

  }

  public String toTrecString(int trecevalNumber, int cnt) {
    return String.format("%s\t%s\t%s\t%s\t%f\t%s",
        trecevalNumber, "Q0",
        this.getObjectVector().getObject().toString(),
        cnt,
        this.getScore(),
        "DEFAULT");
  }

  public String toSimpleString() {
    return   //results in cosine:object format
        String.format("%f:%s",
            this.getScore(),
            this.getObjectVector().getObject().toString());
  }
}
