/**
	 Copyright (c) 2008, Arizona State University.

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


import java.io.File;
import java.io.IOException;
import java.lang.Integer;
import org.apache.lucene.index.*;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.FSDirectory;

import java.util.Arrays;
import java.util.Enumeration;

/**
 * generates term vectors incrementally (i.e. one document at a time)
 * requires a
 * @param termVectorData Has all the information needed to create term vectors.
 * @param vectorFile Filename for the document vectors
 * @author Trevor Cohen, Dominic Widdows
 */
public class IncrementalTermVectors {

	private VectorStoreRAM termVectorData;
	private IndexReader indexReader;
	private String[] fieldsToIndex = null;
	private static LuceneUtils lUtils = null;
	  
	  
	/**
	 * Constructor that gets everything it needs from a
	 * TermVectorsFromLucene object and writes to a named file.
	 * @param termVectorData Has all the information needed to create doc vectors.
	 * @param indexDir Directory of the Lucene Index used to generate termVectorData
	 * @param fieldsToIndex String[] containing fields indexed when generating termVectorData
	 * @param vectorFile Filename for the document vectors
	 */
	
	public IncrementalTermVectors(String indexDir,
            String[] fieldsToIndex, String vectorFileName)

	throws IOException {
		
	
		  this.indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
		  this.fieldsToIndex = fieldsToIndex;
		    if (this.lUtils == null)
		      this.lUtils = new LuceneUtils(indexDir);

		    int numdocs = indexReader.numDocs();

		    // Open file and write headers.
		    File vectorFile = new File(vectorFileName);
		    String parentPath = vectorFile.getParent();
		    if (parentPath == null) parentPath = "";
		    FSDirectory fsDirectory = FSDirectory.open(new File(parentPath));
		    IndexInput inputStream = fsDirectory.openInput(vectorFileName.replaceAll(".*/", ""));
		    
		    float[] tmpVector = new float[Flags.dimension];
		    int counter = 0;
		    System.err.println("Read vectors incrementally from file " + vectorFile);

		    boolean hasHeader = false;
		    
		    //Read number of dimensions from document vectors
		    String test = inputStream.readString();
		      // Include "-" character to avoid unlikely case that first term is "dimensions"!
		      if ((test.equalsIgnoreCase("-dimensions"))) {
		       Flags.dimension = inputStream.readInt();
		       }
		      else {
		        System.err.println("No file header for file " + vectorFile +
		                           "\nAttempting to process with default vector length: " +
		                           Flags.dimension +
		                           "\nIf this fails, consider rebuilding indexes - existing " +
		                           "ones were probably created with old version of software.");
		         }
		    
		    
		 
		System.err.println("Opening index at "+indexDir);
	
		
		termVectorData = new VectorStoreRAM();
		TermEnum terms = this.indexReader.terms();
		    int tc = 0;

		    while(terms.next()){
	    		Term term = terms.term();

	    		// Skip terms that don't pass the filter.
				if (!lUtils.termFilter(terms.term(), fieldsToIndex))
					continue;
				tc++;
				float[] termVector = new float[Flags.dimension];

				// Place each term vector in the vector store.
				termVectorData.putVector(term.text(), termVector);
		    }
		    System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");
		
	
		// Iterate through documents.
		for (int dc=0; dc < numdocs; dc++) {
			/* output progress counter */
			if (( dc % 10000 == 0 ) || ( dc < 10000 && dc % 1000 == 0 )) {
				System.err.print(dc + " ... ");
			}
			
			String docID = Integer.toString(dc);
			int dcount = dc;
			String docName = "";
			float[] docVector = new float[Flags.dimension];
			
			try {
			docName = inputStream.readString();
			for (int i = 0; i < Flags.dimension; ++i) {
				docVector[i] = Float.intBitsToFloat(inputStream.readInt());
			}
			
			}
			catch 
			(Exception e)
			{ System.out.println("Doc vectors less than total number of documents");
				dc = numdocs +1;
				continue;
			}
		
			for (String fieldName: fieldsToIndex) {
				TermFreqVector vex =
					(TermFreqVector) indexReader.getTermFreqVector(dcount, fieldName);

				if (vex !=null) {
					// Get terms in document and term frequencies.
					String[] docterms = vex.getTerms();
					int[] freqs = vex.getTermFrequencies();

					//For each term in doc (and its frequency)
					for (int b = 0; b < freqs.length; ++b) {
						String term = docterms[b];
						int freq = freqs[b];
						float[] termVector = new float[0];
			
						try{
							termVector = termVectorData.getVector(term);
						} catch (NullPointerException npe) {
							// Don't normally print anything - too much data!
							// TODO(dwiddows): Replace with a configurable logging system.
							// System.err.println("term "+term+ " not represented");
						}
						// Exclude terms that are not represented in termVectorData
						if (termVector != null && termVector.length > 0) {
							for (int j = 0; j < Flags.dimension; ++j) {
								termVector[j] += freq * docVector[j];
							}
						}
					}
				}

			}
		} // Finish iterating through documents.
		
		
		//Normalize vectors
		Enumeration allVectors = termVectorData.getAllVectors();
		int k=0;
		while (allVectors.hasMoreElements())
		{ObjectVector obVec = (ObjectVector) allVectors.nextElement();
		  float[] termVector = obVec.getVector();
		 termVector = VectorUtils.getNormalizedVector(termVector);
		}
		
		new VectorStoreWriter().WriteVectors("incremental_termvectors.bin", termVectorData);
		
		inputStream.close();
		indexReader.close();
		
	}
	
	 /**
	   * Prints the following usage message:
	   * <code>
	   * <br> IncrementalTermVectors class in package pitt.search.semanticvectors
	   * <br> Usage: java pitt.search.semanticvectors.BuildIndex PATH_TO_LUCENE_INDEX
	   * <br> IncrementalTermVectors creates termvectors  files in local directory from docvectors file
	   * <br> 
	   * <br> Usage: java pitt.search.semanticvectors.IncrementalTermVectors [document vector file] [lucene index]
	   * <br> 
	   * <br> Other parameters that can be changed include vector length,
	   * <br>     (number of dimensions), seed length (number of non-zero
	   * <br>     entries in basic vectors), minimum term frequency,
	   * <br>     and number of iterative training cycles.
	   * <br> To change these use the following command line arguments:
	   * <br> -dimension [number of dimensions]
	   * <br> -seedlength [seed length]
	   * <br> -minfrequency [minimum term frequency]
	   * <br> -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]
  	   * </code>
	   */
	  public static void usage() {
	    String usageMessage = "\nBuildIndex class in package pitt.search.semanticvectors"
	        + "\nUsage: java pitt.search.semanticvectors.BuildIndex PATH_TO_LUCENE_INDEX"
	        + "\nBuildIndex creates termvectors and docvectors files in local directory."
	        + "\nOther parameters that can be changed include vector length,"
	        + "\n    (number of dimensions), seed length (number of non-zero"
	        + "\n    entries in basic vectors), minimum term frequency,"
	        + "\n    and number of iterative training cycles."
	        + "\nTo change these use the command line arguments "
	        + "\n  -dimension [number of dimensions]"
	        + "\n  -seedlength [seed length]"
	        + "\n  -minfrequency [minimum term frequency]"
	        + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]"
	        + "\n  -trainingcycles [training cycles]"
	        + "\n  -docindexing [incremental|inmemory|none] Switch between building doc vectors incrementally"
	        + "\n        (requires positional index), all in memory (default case), or not at all";
	    System.out.println(usageMessage);
	  }
	

	
	  public static void main(String[] args) throws IOException
	  { 
		  try {
		      args = Flags.parseCommandLineFlags(args);
		    } catch (IllegalArgumentException e) {
		      usage();
		      throw e;
		    }

		    // Only one argument should remain, the path to the Lucene index.
		    if (args.length != 2) {
		      usage();
		      throw (new IllegalArgumentException("After parsing command line flags, there were " + args.length
		                                          + " arguments, instead of the expected 2."));
		    }

		    System.err.println("Minimum frequency = " + Flags.minfrequency);
		    System.err.println("Maximum frequency = " + Flags.maxfrequency);
		    System.err.println("Number non-alphabet characters = " + Flags.maxnonalphabetchars);
		   
		    System.err.println("Contents fields are: " + Arrays.toString(Flags.contentsfields));

		  
		  String vectorFile = args[0];
		  String luceneIndex = args[1];

		    new IncrementalTermVectors(luceneIndex,
		                              Flags.contentsfields, vectorFile);
	    
		
		  
		  
	  }
	  
}
