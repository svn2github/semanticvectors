
/**
   Copyright (c) 2007, University of Pittsburgh
   Copyright (c) 2008 and ongoing, the SemanticVectors authors

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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import pitt.search.semanticvectors.vectors.BinaryVectorUtils;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

/**
 * This class contains methods for manipulating queries, e.g., taking
 * a list of queryterms and producing a (possibly weighted) aggregate
 * query vector. In the fullness of time this will hopefully include
 * parsing and building queries that include basic (quantum) logical operations.
 * So far these basic operations include negation of one or more terms.
 */
public class CompoundVectorBuilder {
  private VectorStore vecReader;
  private LuceneUtils lUtils;

  private static final Logger logger =
      Logger.getLogger(CompoundVectorBuilder.class.getCanonicalName());

  public CompoundVectorBuilder (VectorStore vecReader, LuceneUtils lUtils) {
    this.vecReader = vecReader;
    this.lUtils = lUtils;
  }

  /**
   * Constructor that defaults LuceneUtils to null.
   */
  public CompoundVectorBuilder (VectorStore vecReader) {
    this.vecReader = vecReader;
    this.lUtils = null;
  }

  /**
   * Returns a vector representation containing both content and positional information
   * @param queryTerms String array of query terms to look up. Expects a single "?" entry, which
   * denotes the query term position. E.g., "martin ? king" might pick out "luther".
   */
  public static Vector getPermutedQueryVector(VectorStore vecReader,
      LuceneUtils lUtils,
      String[] queryTerms) throws IllegalArgumentException {

    // Check basic invariant that there must be one and only one "?" in input.
    int queryTermPosition = -1;
    for (int j = 0; j < queryTerms.length; ++j) {
      if (queryTerms[j].equals("?")) {
        if (queryTermPosition == -1) {
          queryTermPosition = j;
        } else {
          // If we get to here, there was more than one "?" argument.
          logger.severe("Illegal query argument: arguments to getPermutedQueryVector must " +
              "have only one '?' string to denote target term position.");
          throw new IllegalArgumentException();
        }
      }
    }
    // If we get to here, there were no "?" arguments.
    if (queryTermPosition == -1) {
      logger.severe("Illegal query argument: arguments to getPermutedQueryVector must " +
          "have exactly one '?' string to denote target term position.");
      throw new IllegalArgumentException();
    }

    // Initialize other arguments.
    Vector queryVec = VectorFactory.createZeroVector(
        vecReader.getVectorType(), vecReader.getDimension());
    Vector tmpVec = VectorFactory.createZeroVector(
        vecReader.getVectorType(), vecReader.getDimension());
    float weight = 1;

    for (int j = 0; j < queryTerms.length; ++j) {
      if (j != queryTermPosition)	{
        tmpVec = vecReader.getVector(queryTerms[j]);
        int shift = j - queryTermPosition;

        if (lUtils != null) {
          weight = lUtils.getGlobalTermWeightFromString(queryTerms[j]);
          logger.log(Level.FINE, "Term {0} weight {1}", new Object[]{queryTerms[j], weight});
        } else {
          weight = 1;
        }

        if (tmpVec != null) {
          queryVec.superpose(tmpVec, weight,
              PermutationUtils.getShiftPermutation(
                  vecReader.getVectorType(), vecReader.getDimension(), shift));
        } else {
          VerbatimLogger.warning("No vector for '" + queryTerms[j] + "'\n");
        }
      }
    }
    queryVec.normalize();

    return queryVec;
  }

  /**
   * Method gets a query vector from a query string of the form:
   * S(concept1)*E(relation2)+S(concept3)*E(relation4) 
   * 
   * the resulting vector will be the bundle of the semantic vector for each concept
   * bound to the elemental vector of the relevant relation
   * 
   * @param vecReader
   * @param queryString
   * @return the resulting query vector
   */

  
  private static Vector getVector(VectorStore semanticVectors, VectorStore elementalVectors, String term)
  {
 	 if (term.startsWith("E(") && term.endsWith(")"))
 			 return elementalVectors.getVector(term.substring(2,term.length()-1)).copy();
     else if (term.startsWith("S(") && term.endsWith(")"))
     		return semanticVectors.getVector(term.substring(2,term.length()-1)).copy();
     else return VectorFactory.createZeroVector(semanticVectors.getVectorType(), semanticVectors.getDimension());
  }
   
   
  public static Vector getBoundProductQueryVectorFromString(VectorStore semanticVectors, VectorStore elementalVectors, String queryString)
  {
      //allow for bundling of multiple concepts/relations - split initially at "+" to construct vectors to be superposed
      StringTokenizer bundlingTokenizer = new StringTokenizer(queryString,"+");
      Vector bundled_queryvector = VectorFactory.createZeroVector(semanticVectors.getVectorType(), semanticVectors.getDimension());
      while (bundlingTokenizer.hasMoreTokens())
      { 
      //allow for binding of multiple concepts/relations
      StringTokenizer bindingTokenizer = new StringTokenizer(bundlingTokenizer.nextToken(),"*");
      
      String nextToken = bindingTokenizer.nextToken();
      Vector bound_queryvector = null;
      
      //get vector for first token
      bound_queryvector = getVector(semanticVectors, elementalVectors, nextToken).copy();
          
      
      while (bindingTokenizer.hasMoreTokens())
    	  {
    	 nextToken = bindingTokenizer.nextToken();
         
    	 Vector bound_queryvector2 = null;
         bound_queryvector2 = getVector(semanticVectors, elementalVectors, nextToken).copy();
         
          //sequence of operations important for complex vectors and permuted binary vectors
          bound_queryvector2.release(bound_queryvector);
          bound_queryvector = bound_queryvector2;
         
    	  }
      
       bundled_queryvector.superpose(bound_queryvector, 1, null);
      
      }
      
      bundled_queryvector.normalize();
      return bundled_queryvector;
  }

  
  /**
   * Method gets a query vector from a query string of the form:
   * concept1*relation2+concept3*relation4 
   * 
   * the resulting vector will be the bundle of the bound product of concept1 and relation 1, and the
   * bound product of concept 2 and relation 2
   * 
   * @param vecReader
   * @param queryString
   * @return the resulting query vector
   */

  
  public static Vector getBoundProductQueryVectorFromString(VectorStore vecReader, String queryString)
  {
    //allow for bundling of multiple concepts/relations - split initially at "+" to construct vectors to be superposed
    StringTokenizer bundlingTokenizer = new StringTokenizer(queryString,"+");
    Vector bundled_queryvector = VectorFactory.createZeroVector(vecReader.getVectorType(), vecReader.getDimension());
    while (bundlingTokenizer.hasMoreTokens())
    { 
      //allow for binding of multiple concepts/relations
      StringTokenizer bindingTokenizer = new StringTokenizer(bundlingTokenizer.nextToken(),"*");
      Vector bound_queryvector = vecReader.getVector(bindingTokenizer.nextToken()).copy();

      while (bindingTokenizer.hasMoreTokens())
        bound_queryvector.release(vecReader.getVector(bindingTokenizer.nextToken()));

      bundled_queryvector.superpose(bound_queryvector, 1, null);

    }
    
    

    bundled_queryvector.normalize();
    return bundled_queryvector;
  }


  /**
   * Method gets a query subspace from a query string of the form:
   * relation1*relation2+relation3*relation4 
   * 
   * The resulting subspace (or binary approximation) will be derived from the bound product of concept1 and r1*r2, and the
   * bound product of the concept vector  and relation r3*r4.
   * 
   * This method facilitates the combination of single or dual predicate paths using the quantum OR operator, or a binary approximation thereof
   * 
   * @param vecReader Vector store reader for input
   * @param queryString Query expression to be turned into vector subspace
   * @return List of vectors that are basis elements for subspace
   */
  public static ArrayList<Vector> getBoundProductQuerySubSpaceFromString(
      VectorStore vecReader, Vector conceptVector, String queryString) {
    ArrayList<Vector> disjunctSpace = new ArrayList<Vector>();
    // Split initially at "+" to construct derive components.
    StringTokenizer subspaceTokenizer = new StringTokenizer(queryString,"+");

    while (subspaceTokenizer.hasMoreTokens()) { 
      // Allow for binding of multiple concepts/relations.
      StringTokenizer bindingTokenizer = new StringTokenizer(subspaceTokenizer.nextToken(),"*");
      Vector boundQueryvector = vecReader.getVector(bindingTokenizer.nextToken()).copy();

      while (bindingTokenizer.hasMoreTokens()) {
        boundQueryvector.release(vecReader.getVector(bindingTokenizer.nextToken()));
      }

      Vector copyConceptVector = conceptVector.copy();
       copyConceptVector.release(boundQueryvector);
      
      disjunctSpace.add(copyConceptVector);
      
      
    }

     if (vecReader.getVectorType().equals(VectorType.BINARY)) {
        BinaryVectorUtils.orthogonalizeVectors(disjunctSpace);
      } else {
        VectorUtils.orthogonalizeVectors(disjunctSpace);
      }
    
     
     
     
    return disjunctSpace;
  }
  
  /**
   * Method gets a query subspace from a query string of the form:
   * E(C1)*S(C2) + E(C3)*S(C4)
   * 
   * 
   * This method facilitates the combination of single or dual predicate paths using the quantum OR operator, or a binary approximation thereof
   * 
   * @param vecReader Vector store reader for input
   * @param queryString Query expression to be turned into vector subspace
   * @return List of vectors that are basis elements for subspace
   */
  public static ArrayList<Vector> getBoundProductQuerySubspaceFromString(VectorStore semanticVectors, VectorStore elementalVectors, String queryString)
  {
	  ArrayList<Vector> disjunctSpace = new ArrayList<Vector>();
	  
      //allow for bundling of multiple concepts/relations - split initially at "+" to construct vectors to be superposed
      StringTokenizer bundlingTokenizer = new StringTokenizer(queryString,"+");
       while (bundlingTokenizer.hasMoreTokens())
      { 
      //allow for binding of multiple concepts/relations
      StringTokenizer bindingTokenizer = new StringTokenizer(bundlingTokenizer.nextToken(),"*");
      
      String nextToken = bindingTokenizer.nextToken();
      Vector bound_queryvector = null;
      
      bound_queryvector = getVector(semanticVectors, elementalVectors, nextToken).copy();
          
      
      while (bindingTokenizer.hasMoreTokens())
    	  {
     	nextToken = bindingTokenizer.nextToken();
         Vector bound_queryvector2 = null;
         bound_queryvector2 = getVector(semanticVectors, elementalVectors, nextToken).copy();
         
          //sequence of operations important for complex vectors
          bound_queryvector2.release(bound_queryvector);
          bound_queryvector = bound_queryvector2;
         
    	  }
      
       disjunctSpace.add(bound_queryvector);
      
      }
      
         if (semanticVectors.getVectorType().equals(VectorType.BINARY)) {
            BinaryVectorUtils.orthogonalizeVectors(disjunctSpace);
          } else {
            VectorUtils.orthogonalizeVectors(disjunctSpace);
          
        }
  
      return disjunctSpace;
  }
  

  /**
   * Method gets a query vector from a query string, i.e., a
   * space-separated list of queryterms.
   */
  public static Vector getQueryVectorFromString(VectorStore vecReader,
      LuceneUtils lUtils,
      String queryString) {
    String[] queryTerms = queryString.split("\\s");
    return getQueryVector(vecReader, lUtils, queryTerms);
  }

  /**
   * Gets a query vector from an array of query terms. The
   * method is static and creates its own CompoundVectorBuilder.  This
   * enables client code just to call "getQueryVector" without
   * creating an object first, though this may be slightly less
   * efficient for multiple calls.
   * 
   * @param vecReader The vector store reader to use.
   * @param lUtils Lucene utilities for getting term weights.
   * @param queryTerms Query expression, e.g., from command line.  If
   *        the term NOT appears in queryTerms, terms after that will
   *        be negated.
   * @return queryVector, a vector representing the user's query.
   */
  public static Vector getQueryVector(VectorStore vecReader,
      LuceneUtils lUtils,
      String[] queryTerms) {
    CompoundVectorBuilder builder = new CompoundVectorBuilder(vecReader, lUtils);
    Vector returnVector = VectorFactory.createZeroVector(
        vecReader.getVectorType(), vecReader.getDimension());
    // Check through args to see if we need to do negation.
    if (!Flags.suppressnegatedqueries) {
      for (int i = 0; i < queryTerms.length; ++i) {
        if (queryTerms[i].equalsIgnoreCase("NOT")) {
          // If, so build negated query and return.
          return builder.getNegatedQueryVector(queryTerms, i);
        }
      }
    }
    if (Flags.vectorlookupsyntax.equals("regex")) {
      returnVector = builder.getAdditiveQueryVectorRegex(queryTerms);
    } else {
      returnVector = builder.getAdditiveQueryVector(queryTerms);
    }
    return returnVector;
  }

  /**
   * Returns a (possibly weighted) normalized query vector created
   * by adding together vectors retrieved from vector store.
   * @param queryTerms String array of query terms to look up.
   */
  protected Vector getAdditiveQueryVector (String[] queryTerms) {
    Vector queryVec = VectorFactory.createZeroVector(
        vecReader.getVectorType(), vecReader.getDimension());
    float weight = 1;

    for (int j = 0; j < queryTerms.length; ++j) {
      Vector tmpVec = vecReader.getVector(queryTerms[j]);

      if (lUtils != null) {
        weight = lUtils.getGlobalTermWeightFromString(queryTerms[j]);
      } else {
        weight = 1;
      }

      if (tmpVec != null) {
        queryVec.superpose(tmpVec, weight, null);
      } else {
        VerbatimLogger.warning("No vector for '" + queryTerms[j] + "'\n");
      }
    }

    queryVec.normalize();
    return queryVec;
  }

  /**
   * Returns a (possibly weighted) normalized query vector created by
   * adding together all vectors retrieved from vector store whose
   * objects match a particular regular expression.
   * 
   * @param queryTerms String array of query terms to look up.
   */
  protected Vector getAdditiveQueryVectorRegex (String[] queryTerms) {
    Vector queryVec = VectorFactory.createZeroVector(Flags.vectortype, Flags.dimension);
    float weight = 1;

    for (int j = 0; j < queryTerms.length; ++j) {
      // Compile a regular expression for matching anything containing this term.
      Pattern pattern = Pattern.compile(queryTerms[j]);
      logger.log(Level.FINER,"Query term pattern: {0}",pattern.pattern());
      Enumeration<ObjectVector> vecEnum = vecReader.getAllVectors();
      while (vecEnum.hasMoreElements()) {
        // Test this element.
        ObjectVector testElement = vecEnum.nextElement();
        Matcher matcher = pattern.matcher(testElement.getObject().toString());
        if (matcher.find()) {
          Vector tmpVec = testElement.getVector();

          if (lUtils != null) {
            weight = lUtils.getGlobalTermWeightFromString(testElement.getObject().toString());
          }
          else { weight = 1; }

          queryVec.superpose(tmpVec, weight, null);
        }
      }
    }
    queryVec.normalize();
    return queryVec;
  }

  /**
   * Creates a vector, including orthogonalizing negated terms.
   * 
   * @param queryTerms List of positive and negative terms.
   * @param split Position in this list of the NOT mark: terms
   * before this are positive, those after this are negative.
   * @return Single query vector, the sum of the positive terms,
   * projected to be orthogonal to all negative terms.
   * @see VectorUtils#orthogonalizeVectors
   */
  protected Vector getNegatedQueryVector(String[] queryTerms, int split) {
    int numNegativeTerms = queryTerms.length - split - 1;
    int numPositiveTerms = split;
    logger.log(Level.FINER, "Number of negative terms: {0}", numNegativeTerms);
    logger.log(Level.FINER, "Number of positive terms: {0}", numPositiveTerms);
    ArrayList<Vector> vectorList = new ArrayList<Vector>();
    for (int i = 1; i <= numNegativeTerms; ++i) {
      Vector tmpVector = vecReader.getVector(queryTerms[split + i]);
      if (tmpVector != null) {
        vectorList.add(tmpVector);
      }
    }
    String[] positiveTerms = new String[numPositiveTerms];
    for (int i = 0; i < numPositiveTerms; ++i) {
      positiveTerms[i] = queryTerms[i];
    }
    vectorList.add(getAdditiveQueryVector(positiveTerms));
    if (!vecReader.getVectorType().equals(VectorType.BINARY))
      VectorUtils.orthogonalizeVectors(vectorList);
    else BinaryVectorUtils.orthogonalizeVectors(vectorList);

    return vectorList.get(vectorList.size() - 1);
  }
}
