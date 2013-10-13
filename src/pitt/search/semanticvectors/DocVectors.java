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

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * Implementation of vector store that collects doc vectors by
 * iterating through all the terms in a term vector store and
 * incrementing document vectors for each of the documents containing
 * that term.
 */
public class DocVectors implements VectorStore {
  
  /**
   * Enumeration of different memory management strategies for indexing documents. 
   */
  public enum DocIndexingStrategy {
    /**
     * Index documents in memory, write to disk at the end. Fast but relies on large
     * memory footprint. */
    INMEMORY,
    
    /**
     * Index one document at a time, writing to disk during the process.
     * Requires less memory. Cannot be used for indexing strategies that rely
     * on random access to doc vector store during indexing.
     */
    INCREMENTAL,
    
    /**
     * Do not create document vectors at all. Useful if there are many documents
     * and you are only interested in exploring term vectors.
     */
    NONE,
  }
  
  private static final Logger logger = Logger.getLogger(DocVectors.class.getCanonicalName());
  private FlagConfig flagConfig;
  private VectorStoreRAM docVectors;
  private VectorStore termVectors;
  private LuceneUtils luceneUtils;

  //@Override
  public VectorType getVectorType() { return flagConfig.vectortype(); }

  //@Override
  public int getDimension() { return flagConfig.dimension(); }

  /**
   * Constructor that gets everything it needs from a
   * TermVectorsFromLucene object and its corresponding FlagConfig.
   */
  public DocVectors (VectorStore termVectors, FlagConfig flagConfig, LuceneUtils luceneUtils) throws IOException {
    this.flagConfig = flagConfig;
    this.luceneUtils = luceneUtils;
    this.termVectors = termVectors;
    this.docVectors = new VectorStoreRAM(flagConfig);

    initializeDocVectors();
    trainDocVectors();
  }

  /**
   * Creates doc vectors, iterating over terms.
   */
  private void trainDocVectors() {
    VerbatimLogger.info("Building document vectors ... ");
    Enumeration<ObjectVector> termEnum = termVectors.getAllVectors();
    try {
      int tc = 0;
      while (termEnum.hasMoreElements()) {
        // Output progress counter.
        if ((tc % 10000 == 0) || (tc < 10000 && tc % 1000 == 0)) {
          VerbatimLogger.info("Processed " + tc + " terms ... ");
        }
        tc++;

        ObjectVector termVectorObject = termEnum.nextElement();
        Vector termVector = termVectorObject.getVector();
        String word = (String) termVectorObject.getObject();

        // Go through checking terms for each fieldName.
        for (String fieldName : flagConfig.contentsfields()) {
          Term term = new Term(fieldName, word);
          float globalweight = luceneUtils.getGlobalTermWeight(term);
          float fieldweight = 1;
           
          // Get any docs for this term.
          DocsEnum docsEnum = this.luceneUtils.getDocsForTerm(term);

          while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
            int docID = docsEnum.docID();
            // Add vector from this term, taking freq into account.
            Vector docVector = this.docVectors.getVector(Integer.toString(docID));
            float localweight = docsEnum.freq();

            if (flagConfig.fieldweight()) {
              //field weight: 1/sqrt(number of terms in field)
              TermsEnum terms = luceneUtils.getTermVector(docID, fieldName).iterator(null);
              int numTerms = 0;
              while (terms.next() != null) {
                numTerms++;
              }
              fieldweight = (float) (1/Math.sqrt(numTerms));
            }

            docVector.superpose(
                termVector, localweight * globalweight * fieldweight, null);
          }
        }
      }
    }
    catch (IOException e) { // catches from indexReader.
      e.printStackTrace();
    }

    VerbatimLogger.info("\nNormalizing doc vectors ...\n");
    for (int i = 0; i < luceneUtils.getNumDocs(); ++i) {
      docVectors.getVector(Integer.toString(i)).normalize();
    }
  }

  /**
   * Allocate doc vectors to zero vectors.
   */
  private void initializeDocVectors() {
    VerbatimLogger.info("Initializing document vector store ... \n");
    for (int i = 0; i < luceneUtils.getNumDocs(); ++i) {
      Vector docVector = VectorFactory.createZeroVector(
          flagConfig.vectortype(), flagConfig.dimension());
      this.docVectors.putVector(Integer.toString(i), docVector);
    }
  }

  /**
   * Create a version of the vector store indexes by path / filename rather than Lucene ID.
   */
  public VectorStore makeWriteableVectorStore() {
    VectorStoreRAM outputVectors = new VectorStoreRAM(flagConfig);

    for (int i = 0; i < this.luceneUtils.getNumDocs(); ++i) {
      String docName = "";
      try {
        // Default field value for docid is "path". But can be
        // reconfigured.  For bilingual docs, we index "filename" not
        // "path", since there are two system paths, one for each
        // language.
        if (this.luceneUtils.getDoc(i).getField(flagConfig.docidfield()) != null) {
          docName = this.luceneUtils.getDoc(i).getField(flagConfig.docidfield()).stringValue();
          if (docName.length() == 0) {
            logger.warning("Empty document name!!! This will cause problems ...");
            logger.warning("Please set -docidfield to a nonempty field in your Lucene index.");
          }
        }
        Vector docVector = this.docVectors.getVector(Integer.toString(i));
        outputVectors.putVector(docName, docVector);
      } catch (CorruptIndexException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return outputVectors;
  }

  public Vector getVector(Object id) {
    return this.docVectors.getVector(id);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return this.docVectors.getAllVectors();
  }

  public int getNumVectors() {
    return this.docVectors.getNumVectors();
  }
  
  @Override
  public boolean containsVector(Object object) {
	  return this.getVector(object) != null;
  }
}
