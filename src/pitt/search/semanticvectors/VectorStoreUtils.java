/**
   Copyright (c) 2011, The SemanticVectors AUTHORS

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
**/package pitt.search.semanticvectors;

public class VectorStoreUtils {

  /**
   * Returns "$storeName.bin" if {@link FlagConfig#indexfileformat} is "lucene".
   * Returns "$storeName.txt" if {@link FlagConfig#indexfileformat} is "text".
   * 
   * Method is idempotent: if file already ends with ".bin" or ".txt" as appropriate, input
   * is returned unchanged.
   */
  public static String getStoreFileName(String storeName, FlagConfig flagConfig) {
    if (flagConfig.indexfileformat() == "lucene") {
      if (storeName.endsWith(".bin")) {
        return storeName;
      }
      else {
        return storeName + ".bin";
      }
    } else if (flagConfig.indexfileformat() == "text") {
      if (storeName.endsWith(".txt")) {
        return storeName;
      }
      else {
        return storeName + ".txt";
      }
    }
    throw new IllegalStateException("Looks like an illegal indexfileformat: " + flagConfig.indexfileformat());
  }
}
