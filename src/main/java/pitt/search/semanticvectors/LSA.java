package pitt.search.semanticvectors;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import ch.akuhn.edu.mit.tedlab.*;

/**
 * Interface to Adrian Kuhn and David Erni's implementation of SVDLIBJ, a native Java version
 * of Doug Rhodes' SVDLIBC, which was in turn based on SVDPACK, by Michael Berry, Theresa Do,
 * Gavin O'Brien, Vijay Krishna and Sowmini Varadhan.
 *
 * This class will produce two files, svd_termvectors.bin and svd_docvectors.bin from a Lucene index
 * Command line arguments are consistent with the rest of the Semantic Vectors Package
 */
public class LSA {
  private static final Logger logger = Logger.getLogger(LSA.class.getCanonicalName());

  public static String usageMessage = "\nLSA class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.LSA -luceneindexpath PATH_TO_LUCENE_INDEX"
        + "\nBuildIndex creates svd_termvectors and svd_docvectors files in local directory."
        + "\nOther parameters that can be changed include vector length,"
        + "\n    (number of dimension), seed length (number of non-zero"
        + "\n    entries in basic vectors), and minimum term frequency."
        + "\nTo change these use the command line arguments "
        + "\n  -dimension [number of dimension]"
        + "\n  -minfrequency [minimum term frequency]"
        + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]"
        + "\n  -filternumbers [true or false]";

  private FlagConfig flagConfig;
  /** Stores the list of terms in the same order as rows in the matrix. */
  private String[] termList;
  /** The single contents field in the Lucene index: LSA only supports one. */
  private String contentsField;
  private LuceneUtils luceneUtils;
  
  /**
   * Basic constructor that tries to check up front that resources are available and
   * configurations are consistent.
   * 
   * @param luceneIndexDir Relative path to directory containing Lucene index.
   * @throws IOException 
   */
  private LSA(String luceneIndexDir, FlagConfig flagConfig) throws IOException {
    this.flagConfig = flagConfig;    
    this.luceneUtils = new LuceneUtils(flagConfig);
    this.contentsField = flagConfig.contentsfields()[0];
    
    // Find the number of docs, and if greater than dimension, set the dimension
    // to be this number.
    if (flagConfig.dimension() > this.luceneUtils.getNumDocs()) {
      logger.warning("Dimension for SVD cannot be greater than number of documents ... "
          + "Setting dimension to " + this.luceneUtils.getNumDocs());
      flagConfig.setDimension(this.luceneUtils.getNumDocs());
    }

    if (flagConfig.termweight().equals("logentropy")) {
      VerbatimLogger.info("Term weighting: log-entropy.\n");
    }

    // Log some of the basic properties. This could be altered to be more informative if
    // our users ever ask for different properties.
    VerbatimLogger.info("Set up LSA indexer.\n" +
    		"Dimension: " + flagConfig.dimension() + " Minimum frequency = " + flagConfig.minfrequency()
        + " Maximum frequency = " + flagConfig.maxfrequency()
        + " Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() +  "\n");
  }

  /**
   * Converts the Lucene index into a sparse matrix.
   * Also populates termList as a side-effect.
   * 
   * @returns sparse term-document matrix in the format expected by SVD library
   */
  private SMat smatFromIndex() throws IOException {
    SMat S;
    Terms terms = this.luceneUtils.getTermsForField(contentsField);
    VerbatimLogger.info(String.format(
        "There are %d terms (and %d docs).\n", terms.size(), this.luceneUtils.getNumDocs()));
    termList = new String[(int) terms.size()];
    int[][] baseIndex = new int[(int) terms.size()][];
    int nonZeroVals = 0, termCounter = 0;

    TermsEnum termsEnum = terms.iterator(null);
    BytesRef bytes;
    
    // This first loop is all setup and preparing counters.
    while((bytes = termsEnum.next()) != null) {
      Term term = new Term(contentsField, bytes);
      if (luceneUtils.termFilter(term)) {
        termList[termCounter] = term.text();

        // Create matrix of nonzero indices.
        DocsEnum docsEnum = this.luceneUtils.getDocsForTerm(term);
        int numDocsWithTerm = 0;
        while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          ++numDocsWithTerm;
          ++nonZeroVals;
        }
        baseIndex[termCounter] = new int[numDocsWithTerm];

        // Fill in matrix of nonzero indices, enumerating docsEnum again.
        docsEnum = this.luceneUtils.getDocsForTerm(term);
        int count = 0;
        while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          baseIndex[termCounter][count] = docsEnum.docID();
          ++count;
        }
        ++termCounter;	// Next term.
      }
    }
    
    // Initialize "SVDLIBJ" sparse data structure.
    S = new SMat(this.luceneUtils.getNumDocs(), termCounter, nonZeroVals);

    // Populate "SVDLIBJ" sparse data structure.
    termsEnum = terms.iterator(termsEnum);
    termCounter = 0;
    int firstNonZero = 0; // Index of first non-zero entry (document) of each column (term).
    while((bytes = termsEnum.next()) != null) {
      Term term = new Term(contentsField, bytes);
      if (this.luceneUtils.termFilter(term)) {
        DocsEnum docsEnum = this.luceneUtils.getDocsForTerm(term);
        S.pointr[termCounter] = firstNonZero;

        while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          /** public int[] pointr; For each col (plus 1), index of
            *  first non-zero entry.  we'll represent the matrix as a
            *  document x term matrix such that terms are columns
            *  (otherwise it would be difficult to extract this
            *  information from the lucene index)
            */
          S.rowind[firstNonZero] = docsEnum.docID();  // set row index to document number
          float value = docsEnum.freq() * luceneUtils.getGlobalTermWeight(term);
          S.value[firstNonZero] = value;  // set value to frequency (with/without weighting)
          firstNonZero++;
        }
        termCounter++;
      }
    }
    S.pointr[S.cols] = S.vals;

    return S;
  }

  private void writeOutput(DMat vT, DMat uT) throws IOException {
    // Open file and write headers.
    FSDirectory fsDirectory = FSDirectory.open(new File("."));
    IndexOutput outputStream = fsDirectory.createOutput(
        VectorStoreUtils.getStoreFileName(flagConfig.termvectorsfile(), flagConfig),
        IOContext.DEFAULT);
  
    // Write header giving number of dimensions for all vectors and make sure type is real.
    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
    int cnt;
    // Write out term vectors
    for (cnt = 0; cnt < vT.cols; cnt++) {
      outputStream.writeString(this.termList[cnt]);
      Vector termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
  
      float[] tmp = new float[flagConfig.dimension()];
      for (int i = 0; i < flagConfig.dimension(); i++)
        tmp[i] = (float) vT.value[i][cnt];
      termVector = new RealVector(tmp);
      termVector.normalize();
  
      termVector.writeToLuceneStream(outputStream);
    }
    outputStream.flush();
    outputStream.close();
    VerbatimLogger.info(
        "Wrote " + cnt + " term vectors incrementally to file " + flagConfig.termvectorsfile() + ".\n");
  
    // Write document vectors.
    // Open file and write headers.
    outputStream = fsDirectory.createOutput(
        VectorStoreUtils.getStoreFileName(flagConfig.docvectorsfile(), flagConfig), IOContext.DEFAULT);
  
    // Write header giving number of dimensions for all vectors and make sure type is real.
    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
  
    // Write out document vectors
    for (cnt = 0; cnt < uT.cols; cnt++) {
      String thePath = this.luceneUtils.getDoc(cnt).get(flagConfig.docidfield());
      outputStream.writeString(thePath);
      float[] tmp = new float[flagConfig.dimension()];
  
      for (int i = 0; i < flagConfig.dimension(); i++)
        tmp[i] = (float) uT.value[i][cnt];
      RealVector docVector = new RealVector(tmp);
      docVector.normalize();
      
      docVector.writeToLuceneStream(outputStream);
    }
    outputStream.flush();
    outputStream.close();
    VerbatimLogger.info("Wrote " + cnt + " document vectors incrementally to file "
                        + flagConfig.docvectorsfile() + ". Done.\n");
  }

  public static void main(String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.out.println(usageMessage);
      throw e;
    }
    if (flagConfig.vectortype() != VectorType.REAL) {
      logger.warning("LSA is only supported for real vectors ... setting vectortype to 'real'."); 
    }
    
    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath must be set."));
    }
    
    if (flagConfig.contentsfields().length != 1) {
      throw new IllegalArgumentException(
          "LSA only supports one -contentsfield, more than this may cause a corrupt matrix.");
    }
    
    LSA lsaIndexer = new LSA(flagConfig.luceneindexpath(), flagConfig);
    SMat A = lsaIndexer.smatFromIndex();
    Svdlib svd = new Svdlib();

    VerbatimLogger.info("Starting SVD using algorithm LAS2 ...\n");

    SVDRec svdR = svd.svdLAS2A(A, flagConfig.dimension());
    DMat vT = svdR.Vt;
    DMat uT = svdR.Ut;
    lsaIndexer.writeOutput(vT, uT);
  }
}
