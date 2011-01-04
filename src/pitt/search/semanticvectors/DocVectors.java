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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;

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
  private static final Logger logger = Logger.getLogger(DocVectors.class.getCanonicalName());

  private VectorStoreRAM docVectors;
  private TermVectorsFromLucene termVectorData;
  private IndexReader indexReader;
  private LuceneUtils lUtils;

  /**
   * Constructor that gets everything it needs from a
   * TermVectorsFromLucene object.
   */
  public DocVectors (TermVectorsFromLucene termVectorData) throws IOException {
    this.termVectorData = termVectorData;
    this.indexReader = termVectorData.getIndexReader();
    this.docVectors = new VectorStoreRAM();
    
    if (this.lUtils == null) {
      String indexReaderDir = termVectorData.getIndexReader().directory().toString();
      indexReaderDir = indexReaderDir.replaceAll("^[^@]+@","");
      indexReaderDir = indexReaderDir.replaceAll(" lockFactory=.+$","");
      this.lUtils = new LuceneUtils(indexReaderDir);
    }

    initializeDocVectors();
    trainDocVectors();
  }

  /**
   * Creates doc vectors, iterating over terms.
   */
  private void trainDocVectors() {
    logger.info("Building document vectors ...");
    Enumeration<ObjectVector> termEnum = termVectorData.getAllVectors();
    try {
      int tc = 0;
      while (termEnum.hasMoreElements()) {
        // Output progress counter.
        if ((tc % 50000 == 0) || (tc < 50000 && tc % 10000 == 0)) {
          logger.info("Processed " + tc + " terms ... ");
        }
        tc++;

        ObjectVector termVectorObject = termEnum.nextElement();
        float[] termVector = termVectorObject.getVector();
        String word = (String) termVectorObject.getObject();


        // Go through checking terms for each fieldName.
        for (String fieldName: termVectorData.getFieldsToIndex()) {
          Term term = new Term(fieldName, word);
          float globalweight = 1;
          if (Flags.termweight.equals("logentropy")) { 
            //global entropy weighting
            globalweight = globalweight * lUtils.getEntropy(term);
          }

          // Get any docs for this term.
          TermDocs td = this.indexReader.termDocs(term);
          while (td.next()) {
            String docID = Integer.toString(td.doc());
            // Add vector from this term, taking freq into account.
            float[] docVector = this.docVectors.getVector(docID);
            float localweight = td.freq();

            if (Flags.termweight.equals("logentropy"))
            {
              //local weighting: 1+ log (local frequency)
              localweight = new Double(1 + Math.log(localweight)).floatValue();    	
            }

            for (int j = 0; j < termVectorData.getDimension(); ++j) {
              docVector[j] += localweight * globalweight * termVector[j];

            }
          }
        }
      }
    }
    catch (IOException e) { // catches from indexReader.
      e.printStackTrace();
    }

    logger.info("\nNormalizing doc vectors ...");
    int dc = 0;
    for (int i = 0; i < indexReader.numDocs(); ++i) {
      float[] docVector = this.docVectors.getVector(Integer.toString(i));
      docVector = VectorUtils.getNormalizedVector(docVector);
      this.docVectors.putVector(Integer.toString(i), docVector);
    }
  }
  
  /**
   * Allocate doc vectors to zero vectors.
   */
  private void initializeDocVectors() {
    logger.info("Initializing document vector store ...");
    for (int i = 0; i < indexReader.numDocs(); ++i) {
      float[] docVector = new float[termVectorData.getDimension()];
      for (int j = 0; j < termVectorData.getDimension(); ++j) {
        docVector[j] = 0;
      }
      this.docVectors.putVector(Integer.toString(i), docVector);
    }
  }

  /**
   * Create a version of the vector store indexes by path / filename rather than Lucene ID.
   */
  public VectorStore makeWriteableVectorStore() {
    VectorStoreRAM outputVectors = new VectorStoreRAM();

    for (int i = 0; i < this.indexReader.numDocs(); ++i) {
      String docName = "";
      try {
        // Default field value for docid is "path". But can be
        // reconfigured.  For bilingual docs, we index "filename" not
        // "path", since there are two system paths, one for each
        // language.
        if (this.indexReader.document(i).getField(Flags.docidfield) != null) {
          if (docName.length() == 0) {
            logger.info("Empty document name!!! This will cause problems ...");
            logger.info("Please set -docidfield to a nonempty field in your Lucene index.");
          }
        }
        float[] docVector = this.docVectors.getVector(Integer.toString(i));
        outputVectors.putVector(docName, docVector);
      } catch (CorruptIndexException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return outputVectors;
  }

  public float[] getVector(Object id) {
    return this.docVectors.getVector(id);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return this.docVectors.getAllVectors();
  }

  public int getNumVectors() {
    return this.docVectors.getNumVectors();
  }
}
