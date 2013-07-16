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

import java.util.Enumeration;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;

/**
   Classes implementing this interface are used to represent a collection
   of object vectors, including i. methods for accessing individual
   ObjectVectors and ii. an enumeration of all the vectors.
   
   @author Dominic Widdows
   @see ObjectVector
*/
public interface VectorStore {
  /**
   * Returns the type of all vectors in the vector store.  (Implementations should enforce homogeneity.)
   */
  public VectorType getVectorType();
  
  /**
   * Returns the dimension of all vectors in the vector store.  (Implementations should enforce homogeneity.)
   */
  public int getDimension();
  
  /**
   * Returns the vector stored for this object, or {@ code null} if none is present.
   * (Support is only tested for {@code String} objects.) 
   * 
   * @param object the object whose vector you want to look up
   * @return a vector (of floats)
   */
  public Vector getVector(Object object);

  /**
   * Returns an enumeration of all the object vectors in the store.
   */
  public Enumeration<ObjectVector> getAllVectors();

  /**
   * Returns a count of the number of vectors in the store.
   */
  public int getNumVectors();
}
