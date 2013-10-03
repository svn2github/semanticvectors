/**
   Copyright (c) 2008, University of Pittsburgh

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.orthography.NumberRepresentation;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Implementation of vector store that creates term by term
 * co-occurrence vectors by iterating through all the documents in a
 * Lucene index.  This class implements a sliding context window
 * approach, as used by Burgess and Lund (HAL) and Schutze amongst
 * others Uses a sparse representation for the basic document vectors,
 * which saves considerable space for collections with many individual
 * documents.
 *
 * @author Trevor Cohen, Dominic Widdows.
 */
public class TermTermVectorsFromLucene { //implements VectorStore {

  /** Different methods for creating positional indexes. */
  public enum PositionalMethod {
    /** Basic "bag-of-words" method using context windows. */
    BASIC,
    /** Binds vectors on left or right based on position. */
    DIRECTIONAL,
    /** Permutes vectors according to how many places they are from focus term. */ 
    PERMUTATION,
    /** Superposition of basic and permuted vectors. */
    PERMUTATIONPLUSBASIC,
    /** Encodes position within sliding window using NumberRepresentation */
    PROXIMITY
  }

  private FlagConfig flagConfig;
  private boolean retraining = false;
  private VectorStoreRAM termVectors;
  private VectorStoreRAM numberVectors;
  private VectorStore indexVectors;
  private LuceneUtils luceneUtils;

  /**
   * Used to store permutations we'll use in training.  If positional method is one of the
   * permutations, this contains the shift for all the focus positions.
   */
  private int[][] permutationCache;

  static final short NONEXISTENT = -1;
  
  /** Returns the semantic (learned) vectors. */
  public VectorStore getSemanticTermVectors() { return this.termVectors; }

  // Basic VectorStore interface methods implemented through termVectors.
  public Vector getVector(Object term) {
    return termVectors.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.getAllVectors();
  }

  public int getNumVectors() {
    return termVectors.getNumVectors();
  }

  /**
   * Constructs an instance using the given configs and elemental vectors.
   * @throws IOException
   */
  public TermTermVectorsFromLucene(
      FlagConfig flagConfig, VectorStore elementalVectors) throws IOException {
    this.flagConfig = flagConfig;
    this.indexVectors = elementalVectors;

    if (flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
        || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC) {
      initializePermutations();}
    else if (flagConfig.positionalmethod() == PositionalMethod.DIRECTIONAL) {
      initializeDirectionalPermutations();	  }
    else if (flagConfig.positionalmethod() == PositionalMethod.PROXIMITY) {
      initializeNumberRepresentations(); }
    trainTermTermVectors();
  }

  /**
   * Initialize all permutations that might be used.
   */
  private void initializePermutations() {    
    permutationCache =
        new int[2 * flagConfig.windowradius() + 1][PermutationUtils.getPermutationLength(flagConfig.vectortype(), flagConfig.dimension())];
    for (int i = 0; i < 2 * flagConfig.windowradius() + 1; ++i) {
      permutationCache[i] = PermutationUtils.getShiftPermutation(
          flagConfig.vectortype(), flagConfig.dimension(), i - flagConfig.windowradius());
    }
  }

  /**
   * Initialize all number vectors that might be used (i.e. one for each position in the sliding window)
   */
  private void initializeNumberRepresentations() {
    NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    numberVectors = numberRepresentation.getNumberVectors(1, 2*flagConfig.windowradius() + 2);
    this.initializeDirectionalPermutations();

    Enumeration<ObjectVector> VEN = numberVectors.getAllVectors();
    while (VEN.hasMoreElements())
      System.err.println(VEN.nextElement().getObject());
  }

  /**
   * Initialize all permutations that might be used (i.e +1 and -1).
   */
  private void initializeDirectionalPermutations() {    
    permutationCache =
        new int[2][PermutationUtils.getPermutationLength(flagConfig.vectortype(), flagConfig.dimension())];

    permutationCache[0] = PermutationUtils.getShiftPermutation(
        flagConfig.vectortype(), flagConfig.dimension(), -1);

    permutationCache[1] = PermutationUtils.getShiftPermutation(
        flagConfig.vectortype(), flagConfig.dimension(), 1);
  }

  private void trainTermTermVectors() throws IOException, RuntimeException { 
    LuceneUtils.compressIndex(flagConfig.luceneindexpath());
    luceneUtils = new LuceneUtils(flagConfig);

    // Check that the Lucene index contains Term Positions.
    FieldInfos fieldsWithPositions = luceneUtils.getFieldInfos();
    if (!fieldsWithPositions.hasVectors()) {
      throw new IOException(
          "Term-term indexing requires a Lucene index containing TermPositionVectors."
              + "\nTry rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
    }

    // If basicTermVectors was passed in, set state accordingly.
    if (indexVectors != null) {
      retraining = true;
      VerbatimLogger.info("Reusing basic term vectors; number of terms: "
          + indexVectors.getNumVectors() + "\n");
    } else {
      this.indexVectors = new VectorStoreRAM(flagConfig);
    }
    Random random = new Random();
    this.termVectors = new VectorStoreRAM(flagConfig);

    // Iterate through an enumeration of terms and allocate initial term vectors.
    // If not retraining, create random elemental vectors as well.
    int tc = 0;
    for (String fieldName : flagConfig.contentsfields()) {
      TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator(null);
      BytesRef bytes;
      while((bytes = terms.next()) != null) {
        Term term = new Term(fieldName, bytes);
        // Skip terms that don't pass the filter.
        if (!luceneUtils.termFilter(term)) continue;

        tc++;
        Vector termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
        // Place each term vector in the vector store.
        this.termVectors.putVector(term.text(), termVector);
        // Do the same for random index vectors unless retraining with trained term vectors
        if (!retraining) {

          if (flagConfig.deterministicvectors())
            random.setSeed(Bobcat.asLong(term.text()));

          Vector indexVector =  VectorFactory.generateRandomVector(
              flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random);
          ((VectorStoreRAM) this.indexVectors).putVector(term.text(), indexVector);
        }
      }
    }
    VerbatimLogger.info("Created basic term vectors for " + tc + " terms (and "
        + luceneUtils.getNumDocs() + " docs).\n");

    // Iterate through documents.
    int numdocs = luceneUtils.getNumDocs();

    for (int dc = 0; dc < numdocs; ++dc) {
      // Output progress counter.
      if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
        VerbatimLogger.info("Processed " + dc + " documents ... ");
      }

      for (String field: flagConfig.contentsfields()) {
        Terms terms = luceneUtils.getTermVector(dc, field);
        if (terms == null) {VerbatimLogger.severe("No term vector for document "+dc); continue; }
        processTermPositionVector(terms, field);
      }
    }

    VerbatimLogger.info("Created " + termVectors.getNumVectors() + " term vectors ...\n");
    VerbatimLogger.info("Normalizing term vectors.\n");
    Enumeration<ObjectVector> e = termVectors.getAllVectors();
    while (e.hasMoreElements())	{
      e.nextElement().getVector().normalize();
    }

    // If building a permutation index, these need to be written out to be reused.
    //
    // TODO(widdows): It is odd to do this here while not writing out the semantic
    // term vectors here.  We should redesign this.
    if (((flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
        || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC)) 
        && !retraining) {
      VerbatimLogger.info("Normalizing and writing random vectors to " + flagConfig.elementalvectorfile() + "\n");
      Enumeration<ObjectVector> f = indexVectors.getAllVectors();
      while (f.hasMoreElements())	{
        f.nextElement().getVector().normalize();
      }
      VectorStoreWriter.writeVectors(flagConfig.elementalvectorfile(), flagConfig, this.indexVectors);
    }
  }

  /**
   * For each term, add term index vector
   * for any term occurring within a window of size windowSize such
   * that for example if windowSize = 5 with the window over the
   * phrase "your life is your life" the index vectors for terms
   * "your" and "life" would each be added to the term vector for
   * "is" twice.
   *
   * TermPositionVectors contain arrays of (1) terms as text (2)
   * term frequencies and (3) term positions within a
   * document. The index of a particular term within this array
   * will be referred to as the 'local index' in comments.
   * @throws IOException 
   */
  private void processTermPositionVector(Terms terms, String field)
      throws ArrayIndexOutOfBoundsException, IOException {
    if (terms == null) return;

    ArrayList<String> localTerms = new ArrayList<String>();
    ArrayList<Integer> freqs = new ArrayList<Integer>();
    Hashtable<Integer, Integer> localTermPositions = new Hashtable<Integer, Integer>();

    TermsEnum termsEnum = terms.iterator(null);
    BytesRef text;
    int termcount = 0;

    while((text = termsEnum.next()) != null) {
      String theTerm = text.utf8ToString();
      if (indexVectors.getVector(theTerm) == null) continue;
      DocsAndPositionsEnum docsAndPositions = termsEnum.docsAndPositions(null, null);
      if (docsAndPositions == null) return;
      docsAndPositions.nextDoc();
      freqs.add(docsAndPositions.freq());
      localTerms.add(theTerm); 

      for (int x = 0; x < docsAndPositions.freq(); x++) {
        localTermPositions.put(new Integer(docsAndPositions.nextPosition()), termcount);
      }

      termcount++;
    }

    // Iterate through positions adding index vectors of terms
    // occurring within window to term vector for focus term
    for (int focusposn = 0; focusposn < localTermPositions.size(); ++focusposn) {
      if (localTermPositions.get(focusposn) == null) continue;
      String focusterm = localTerms.get(localTermPositions.get(focusposn));
      int windowstart = Math.max(0, focusposn - flagConfig.windowradius());
      int windowend = Math.min(focusposn + flagConfig.windowradius(), localTermPositions.size() - 1);

      for (int cursor = windowstart; cursor <= windowend; cursor++) {
        if (cursor == focusposn) continue;
        if (localTermPositions.get(cursor) == null) continue;
        String coterm = localTerms.get(localTermPositions.get(cursor));

        if (this.indexVectors.getVector(coterm) == null) {
          continue;
        }

        float globalweight = luceneUtils.getGlobalTermWeight(new Term(field, coterm));

        // bind to appropriate position vector
        if (flagConfig.positionalmethod() == PositionalMethod.PROXIMITY) {
          indexVectors.getVector(coterm).bind(numberVectors.getVector((1+cursor-windowstart)));
        }

        // calculate permutation required for either Sahlgren (2008) implementation
        // encoding word order, or encoding direction as in Burgess and Lund's HAL
        if (flagConfig.positionalmethod() == PositionalMethod.BASIC
            || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC) {
          termVectors.getVector(focusterm).superpose(indexVectors.getVector(coterm), globalweight, null);
        }
        if (flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
            || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC) {
          int[] permutation = permutationCache[cursor - focusposn + flagConfig.windowradius()];
          termVectors.getVector(focusterm).superpose(indexVectors.getVector(coterm), globalweight, permutation);
        } else if (flagConfig.positionalmethod() == PositionalMethod.DIRECTIONAL || flagConfig.positionalmethod() == PositionalMethod.PROXIMITY) {
          int[] permutation = permutationCache[(int) Math.max(0,Math.signum(cursor - focusposn))];
          termVectors.getVector(focusterm).superpose(indexVectors.getVector(coterm), globalweight, permutation);

          //release to appropriate position vector
          if (flagConfig.positionalmethod() == PositionalMethod.PROXIMITY)
            indexVectors.getVector(coterm).release(numberVectors.getVector((1+cursor-windowstart)));
        }
      } //end of current sliding window   
    } //end of all sliding windows
  }
}
