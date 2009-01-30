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

import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.util.LinkedList;

/**
 * Command line term vector search utility.
 */
public class Search {

	/**
	 * List of different types of searches that can be performed. Most
	 * involve processing combinations of vectors in different ways, in
	 * building a query expression, scoring candidates against these
	 * query expressions, or both. Most options here correspond directly
	 * to a particular subclass of <code>VectorSearcher</code>
   * @see VectorSearcher
	 */
	public enum SearchType { 
		/**
		 * Default option - build a query by adding together (weighted)
		 * vectors for each of the query terms, and search using cosine
		 * similarity.
		 * @see VectorSearcher.VectorSearcherCosine
		 */
		SUM,
			/**
			 * Build a query as with <code>SUM</code> option, but quantize to
			 * sparse vectors before taking scalar product at search time.
			 * This can be used to give a guide to how much smilarities are
			 * changed by only using the most significant coordinates of a
			 * vector.
			 * @see VectorSearcher.VectorSearcherCosineSparse
			 */
			SPARSESUM,
			/**
			 * "Quantum disjunction" - get vectors for each query term, create a
			 * representation for the subspace spanned by these vectors, and
			 * score by measuring cosine similarity with this subspace.
			 * @see VectorSearcher.VectorSearcherSubspaceSim
			 */
			SUBSPACE, 
			/**
			 * "Closest disjunction" - get vectors for each query term, score
			 * by measuring distance to each term and taking the minimum.
			 * @see VectorSearcher.VectorSearcherMaxSim
			 */
			MAXSIM,
			/**
			 * A product similarity that trains by taking ordered pairs of
			 * terms, a target query term, and searches for the term whose tensor
			 * product with the target term gives the largest similarity with training tensor.
			 * @see VectorSearcher.VectorSearcherTensorSim
			 */
			TENSOR,
			/**
			 * Similar to <code>TENSOR</code>, product similarity that trains
			 * by taking ordered pairs of terms, a target query term, and
			 * searches for the term whose convolution product with the target
			 * term gives the largest similarity with training convolution.
			 * @see VectorSearcher.VectorSearcherConvolutionSim
			 */
			CONVOLUTION, 
			/**
			 * Based on Sahlgren (2008). Searches for the term that best matches
			 * the position of a "?" in a sequence of terms. For example
			 * 'martin ? king' should retrieve luther as the top ranked match
			 * requires the index queried to contain unpermuted vectors, either
			 * random vectors (or term vectors if these were used to build permuted
			 * vectors - not yet implemented), and the index searched must contain
			 * permuted vectors 
			 **/		
			PERMUTATION,
			/**
			 * Build an additive query vector (as with <code>SUM</code> and
			 * print out the query vector for debugging.
			 */
			PRINTQUERY}

	// Experimenting with class-level static variables to enable several
	// methods to use this state. Initialize each with default values.
	static String queryFile = "termvectors.bin";
	static String searchFile = "";
	static String lucenePath = null;
	static CloseableVectorStore queryVecReader, searchVecReader;
	static boolean textIndex = false; 
	static LuceneUtils lUtils = null;
	static int numResults;
	static SearchType searchType = SearchType.SUM;
	static boolean lowerCase = true;

  /**
   * Prints the following usage message:
   * <code>
   * <br> Search class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.Search [-q query_vector_file]
   * <br>                                                [-s search_vector_file]
   * <br>                                                [-l path_to_lucene_index]
	 * <br>                                                [-searchtype TYPE]
	 * <br>                                                [-results num_results]
	 * <br>                                                [-lowercase true|false]
   * <br>                                                &lt;QUERYTERMS&gt;
   * <br> If no query or search file is given, default will be
   * <br>     termvectors.bin in local directory.
   * <br> -l argument my be used to get term weights from
   * <br>     term frequency, doc frequency, etc. in lucene index.
	 * <br> -searchtype can be one of SUM, SPARSESUM, SUBSPACE, MAXSIM,
	 * <br> TENSOR, CONVOLUTION, PERMUTATION, PRINTQUERY
   * <br> &lt;QUERYTERMS&gt; should be a list of words, separated by spaces.
   * <br> If the term NOT is used, terms after that will be negated.
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nSearch class in package pitt.search.semanticvectors"
			+ "\nUsage: java pitt.search.semanticvectors.Search [-q query_vector_file]"
			+ "\n                                               [-s search_vector_file]"
			+ "\n                                               [-l path_to_lucene_index]"
			+ "\n                                               [-searchtype TYPE]"
			+ "\n                                               [-results num_results]"
			+ "\n                                               [-lowercase true|false]"
			+ "\n                                               <QUERYTERMS>"
			+ "\n-q argument must precede -s argument if they differ;"
			+ "\n    otherwise -s will default to -q."
			+ "\nIf no query or search file is given, default will be"
			+ "\n    termvectors.bin in local directory."
			+ "\n-l argument is needed if to get term weights from"
			+ "\n    term frequency, doc frequency, etc. in lucene index."
			+ "\n-searchtype can be one of SUM, SPARSESUM, SUBSPACE, MAXSIM,"
			+ "\n                TENSOR, CONVOLUTION, PERMUTATION, PRINTQUERY"
			+ "\n<QUERYTERMS> should be a list of words, separated by spaces."
			+ "\n    If the term NOT is used, terms after that will be negated.";
    System.out.println(usageMessage);
  }

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See usage();
	 * @param numResults Number of search results to be returned in a ranked list.
	 * @return Linked list containing <code>numResults</code> search results.
   */
  public static LinkedList<SearchResult> RunSearch (String[] args, int numResults)
		throws IllegalArgumentException {
		/** 
		 * The RunSearch function has four main stages:
		 * i. Parse command line arguments.
		 * ii. Open corresponding vector and lucene indexes.
		 * iii. Based on search type, build query vector and perform search.
		 * iv. Return LinkedList of results, usually for main() to print out.
		 *
		 * Stage iii. is a large switch statement, that depends on the searchType.
		 *
		 * The code would be nicer if we combined stages i. and ii., but
		 * this would be hard to implement without forcing the user to use
		 * command line arguments in a fixed order, which would definitely
		 * lead to errors. So the trade-off is to make the code more
		 * complex and the usage simpler.
		 */

		// This will become overwritten if -results is passed in as a command line argument!
		numResults = numResults;

    if (args.length == 0) {
      usage();
    }

    int argc = 0;

    // Stage i. Parse all the command-line arguments.
    while (args[argc].substring(0, 1).equals("-")) {
			// If the args list is now too short, this is an error.
			if (args.length - argc <= 2) {
				usage();
				throw new IllegalArgumentException();
			}

      if (args[argc].equals("-q")) {
        queryFile = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-s")) {
        searchFile = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-l")) {
        lucenePath = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-results")) {
        numResults = Integer.parseInt(args[argc + 1]);
        argc += 2;
      }
			else if (args[argc].equals("-textindex")) {
				textIndex = true;
				argc += 1;
			}
			else if (args[argc].equals("-lowercase")) {
				if (args[argc + 1].equalsIgnoreCase("false")) {
					lowerCase = false;
				}
				argc += 2;
			}
			// The most complicated option is the search type: this will
			// lead to the most complex implementational differences later.
			// It would be nice if this behavior could be generated
			// automatically from the entries in the enum.
			else if (args[argc].equals("-searchtype")) {
				String searchTypeString = args[argc + 1];
				searchTypeString = searchTypeString.toLowerCase();
				if (searchTypeString.equals("sum")) {
					searchType = SearchType.SUM;
				}
				else if (searchTypeString.equals("sparsesum")) {
					searchType = SearchType.SPARSESUM;
				}
				else if (searchTypeString.equals("subspace")) {
					searchType = SearchType.SUBSPACE;
				}
				else if (searchTypeString.equals("maxsim")) {
					searchType = SearchType.MAXSIM;
				}
				else if (searchTypeString.equals("subspace")) {
					searchType = SearchType.SUBSPACE;
				}
				else if (searchTypeString.equals("tensor")) {
					searchType = SearchType.TENSOR;
				}
				else if (searchTypeString.equals("convolution")) {
					searchType = SearchType.CONVOLUTION;
				}else if (searchTypeString.equals("permutation")) {
					searchType = SearchType.PERMUTATION;
				}
				else if (searchTypeString.equals("printquery")) {
					searchType = SearchType.PRINTQUERY;
				}
				// Unrecognized search type.
				else {
					System.err.println("Unrecognized -searchtype: " + args[argc + 1]);
					System.err.print("Options are: ");
					for (SearchType option : SearchType.values()) {
						System.out.print(option + ", ");
					}
					System.out.println();
					usage();
					throw new IllegalArgumentException();
				}
				argc += 2;
			}
			// Unrecognized command line option.
      else {
				System.err.println("The following option is not recognized: " + args[argc]);
				usage();
				throw new IllegalArgumentException("Failed to recognize command line arguments.");
      }
    }

		// If searchFile wasn't set, it defaults to queryFile.
		if (searchFile.equals("")) {
			searchFile = queryFile;
		}

		// Stage ii. Open vector stores, and Lucene utils.
    try {
			// Default VectorStore implementation is (Lucene) VectorStoreReader.
      System.err.println("Opening query vector store from file: " + queryFile);
			if (textIndex) { queryVecReader = new VectorStoreReaderText(queryFile); }
			else { queryVecReader = new VectorStoreReader(queryFile); }

      // Open second vector store if search vectors are different from query vectors.
      if (queryFile == searchFile) {
				searchVecReader = queryVecReader;
			} else {
        System.err.println("Opening search vector store from file: " + searchFile);
				if (textIndex) { searchVecReader = new VectorStoreReaderText(searchFile); }
				else { searchVecReader = new VectorStoreReader(searchFile); }
      }

      if (lucenePath != null) {
        try { lUtils = new LuceneUtils(lucenePath); }
        catch (IOException e) {
          System.err.println("Couldn't open Lucene index at " + lucenePath);
        }
      }
		}
    catch (IOException e) {
      e.printStackTrace();
    }

		// This takes the slice of args from argc to end.
		String queryTerms[] = new String[args.length - argc];
		for (int j = 0; j < args.length - argc; ++j) {
			queryTerms[j] = args[j + argc];
			if (lowerCase) {
				System.err.println("Lowercasing term: " + queryTerms[j]);
				queryTerms[j] = queryTerms[j].toLowerCase();
			}
		}
		
		VectorSearcher vecSearcher;
		LinkedList<SearchResult> results = new LinkedList();
		// Stage iii. Perform search according to which searchType was selected.
		// Most options have corresponding dedicated VectorSearcher subclasses.
		switch(searchType) {
			// Simplest option, vector sum for composition, with possible negation.
		case SUM:
			// Create VectorSearcher and search for nearest neighbors.
			try {
				vecSearcher =
					new VectorSearcher.VectorSearcherCosine(queryVecReader,
																									searchVecReader,
																									lUtils,
																									queryTerms);
				System.err.print("Searching term vectors, searchtype SUM ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
			} catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}
			break;

			// Option for quantizing to sparse vectors before
			// comparing. This is for experimental purposes to see how much
			// we lose by compressing to a sparse bit vector.
		case SPARSESUM:
			// Create VectorSearcher and search for nearest neighbors.
			try {
				vecSearcher =
					new VectorSearcher.VectorSearcherCosineSparse(queryVecReader,
																												searchVecReader,
																												lUtils,
																												queryTerms);
				System.err.print("Searching term vectors, searchtype SPARSESUM ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
			} catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}
			break;

			// Tensor product.
		case TENSOR:
			// Create VectorSearcher and search for nearest neighbors.
			try {
				vecSearcher =
					new VectorSearcher.VectorSearcherTensorSim(queryVecReader,
																										 searchVecReader,
																										 lUtils,
																										 queryTerms);
				System.err.print("Searching term vectors, searchtype TENSOR ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
			} catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}				
			break;
			
			// Convolution product.
		case CONVOLUTION:
			// Create VectorSearcher and search for nearest neighbors.
			try {
				vecSearcher =
					new VectorSearcher.VectorSearcherConvolutionSim(queryVecReader,
																													searchVecReader,
																													lUtils,
																													queryTerms);
				System.err.print("Searching term vectors, searchtype CONVOLUTION ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
			} catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}
			break;

			// Quantum disjunction / subspace similarity.
		case SUBSPACE:
			// Create VectorSearcher and search for nearest neighbors.
			try {
				vecSearcher =
					new VectorSearcher.VectorSearcherSubspaceSim(queryVecReader,
																											 searchVecReader,
																											 lUtils,
																											 queryTerms);

				System.err.print("Searching term vectors, searchtype SUBSPACE ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
			}	catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}
			break;

			// Ranks by maximum similarity with any of the query terms.
		case MAXSIM:
			// Create VectorSearcher and search for nearest neighbors.
			try {
				vecSearcher =
					new VectorSearcher.VectorSearcherMaxSim(queryVecReader,
																									searchVecReader,
																									lUtils,
																									queryTerms);
				System.err.print("Searching term vectors, searchtype MAXSIM ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
			}	catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}
			break;
			
			// Permutes query vectors such that the most likely term in the position
			// of the "?" is retrieved
		case PERMUTATION:
			try {
				// Create VectorSearcher and search for nearest neighbors.
				vecSearcher =
					new VectorSearcher.VectorSearcherPerm(queryVecReader,searchVecReader,lUtils,queryTerms);	
				System.err.print("Searching term vectors, searchtype PERMUTATION ... ");
				results = vecSearcher.getNearestNeighbors(numResults);
				break;
			} catch (ZeroVectorException zve) {
				System.err.println(zve.getMessage());
				results = new LinkedList<SearchResult>();
			}
			
			// Simply prints out the query vector: doesn't do any searching.
		case PRINTQUERY:
			float[] queryVector = CompoundVectorBuilder.getQueryVector(queryVecReader,
																																 lUtils,
																																 queryTerms);
			VectorUtils.printVector(queryVector);
			return new LinkedList();
		
		default:
			System.err.println("Search type unrecognized ...");
			results = new LinkedList();
		}

		// Release filesystem resources.
		queryVecReader.close();
		if (!searchFile.equals(queryFile)) {
			searchVecReader.close();
		}

		return results;
	}

	/**
	 * Search wrapper that returns the list of ObjectVectors.
	 */
	public static ObjectVector[] getSearchResultVectors(String[] args, int numResults) 
		throws IllegalArgumentException { 
		LinkedList<SearchResult> results = Search.RunSearch(args, numResults);
		ObjectVector[] resultsList = new ObjectVector[results.size()];
		for (int i = 0; i < results.size(); ++i) {
			String term = ((ObjectVector)results.get(i).getObject()).getObject().toString();
			float[] tmpVector = searchVecReader.getVector(term);
			resultsList[i] = new ObjectVector(term, tmpVector);
		}
		return resultsList;
	}

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See usage();
   */
  public static void main (String[] args)
		throws IllegalArgumentException {
		int defaultNumResults = 20;
		LinkedList<SearchResult> results = RunSearch(args, defaultNumResults);
		// Print out results.
		if (results.size() > 0) {
			System.err.println("Search output follows ...");
			for (SearchResult result: results) {
				System.out.println(result.getScore() + ":" +
													 ((ObjectVector)result.getObject()).getObject().toString());
			}	
		} else {
			System.err.println("No search output.");
		}
	}
}
