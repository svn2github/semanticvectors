/**
   Copyright (c) 2008, University of Pittsburgh.

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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Logger;

/**
 * This class is used for performing kMeans clustering on an entire
 * vector store.  It presumes that the vector store to be clustered is
 * represented in a file in text format (since it's unlikely that
 * you'd want to try this on large files anyway.
 * @see ClusterResults 
 */
public class ClusterVectorStore {
  private static final Logger logger = Logger.getLogger(
      ClusterVectorStore.class.getCanonicalName());

  /**
   * If set to true, writes the centroids of the clusters to a separate file.
   */
  public static final boolean writeCentroidsFile = false;
  
  /**
   * Whether to perform overlap measures on clusters. Only works for the King James corpus
   * so far. Code needs to be cleaned up before this can be made generally available.
   */
  public static final boolean measureClusterOverlaps = false;

  /**
   * Number of clustering runs used for overlap measure to mitigate skewed results from
   * random initialization. Only change from 1 if you intend to measure overlaps.
   */
  public static final int numRunsForOverlap = 1;

  /**
   * Prints the following usage message:
   * <code>
   * ClusterVectorStore class for clustering an entire (text) vector store. <br>
   * Usage: java.pitt.search.semanticvectors.ClusterVectorStore VECTORFILE <br>
   * Use --numclusters to change the number of clusters.
   * Do not try this for large vector stores, it will not scale well! <br>
   */
  public static void usage() {
    String message = "ClusterVectorStore class for clustering an entire (text) vector store.";
    message += "\nUsage: java.pitt.search.semanticvectors.ClusterVectorStore VECTORFILE";
    message += "\nUse --numclusters to change the number of clusters.";
    message += "\nDo not try this for large vector stores, it will not scale well!";
    System.out.println(message);
    return;
  }

  private static int getMaxValue(int[] values) {
    int max = values[0];
    for(int value: values) {
      if (value > max) {
        max = value;
      }
    }
    return max;
  }

  public static String[] getCluster(int ID, int[] clusterIDs, String[] names) {
    ArrayList<String> results = new ArrayList<String>();
    for (int i = 0; i < clusterIDs.length; ++i) {
      if (clusterIDs[i] == ID) {
        results.add(names[i]);
      }
    }
    String[] finalResults = new String[results.size()];
    for (int i = 0; i < results.size(); ++i) {
      finalResults[i] = results.get(i);
    }
    return finalResults;
  }

  /**
   * Measures the overlap between clusters; configured for the KJB corpus and not very general.
   */
  public static Hashtable<String, int[]> clusterOverlapMeasure(int[] clusterIDs, ObjectVector[] vectors) {
    String[] names = new String[vectors.length];
    Hashtable<String, int[]> internalResults = new Hashtable<String, int[]>();
    for (int i = 0; i < vectors.length; ++i) {
      names[i] = (new File(vectors[i].getObject().toString())).getParent();
      int[] matchAndTotal = {0, 0};
      internalResults.put(names[i], matchAndTotal);
    }
    int numClusters = getMaxValue(clusterIDs);
    for (int i = 0; i < numClusters; ++i) {
      String[] cluster = getCluster(i, clusterIDs, names);
      if (cluster.length < 2) {
        continue;
      }
      for (int j = 0; j < cluster.length; ++j) {
        for (int k = j+1; k < cluster.length; ++k) {
          int[] matchAndTotalJ = internalResults.get(cluster[j]);
          int[] matchAndTotalK = internalResults.get(cluster[k]);
          matchAndTotalJ[1]++;
          matchAndTotalK[1]++;
          if (cluster[k].equals(cluster[j])) {
            matchAndTotalJ[0]++;
            matchAndTotalK[0]++;
          }
        }
      }
    }
    return internalResults;
  }

  /**
   * Adds the totals of newTable into mainTable. Presumes keys are identical.
   */
  private static void mergeTables(
      Hashtable<String, int[]> newTable, Hashtable<String, int[]> mainTable) {
    for (String key : mainTable.keySet()) {
      int[] values = mainTable.get(key);
      int[] newValues = newTable.get(key);
      for (int i = 0; i < mainTable.get(key).length; ++i) {
        values[i] += newValues[i];
      }
    }
  }

  /**
   * Takes a number of clusters and a vector store (presumed to be
   * text format) as arguments and prints out clusters.
   */
  public static void main(String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;
    if (args.length != 1) {
      System.out.println("Wrong number of arguments.");
      usage();
      return;
    }

    CloseableVectorStore vecReader;
    try {
      vecReader = VectorStoreReader.openVectorStore(args[0], flagConfig);
    } catch (IOException e) {
      System.out.println("Failed to open vector store from file: '" + args[0] + "'");
      logger.info(e.getMessage());
      throw new IllegalArgumentException("Failed to parse arguments for ClusterVectorStore");
    }

    // Allocate vector memory and read vectors from store.
    logger.info("Reading vectors into memory ...");
    int numVectors = vecReader.getNumVectors();
    ObjectVector[] resultsVectors = new ObjectVector[numVectors];
    Enumeration<ObjectVector> vecEnum = vecReader.getAllVectors();
    int offset = 0;
    while (vecEnum.hasMoreElements()) {
      resultsVectors[offset] = vecEnum.nextElement();
      ++offset;
    }
    vecReader.close();

    Hashtable<String, int[]> mainOverlapResults = null;

    for (int runNumber = 0; runNumber < numRunsForOverlap; ++runNumber) {
      // Perform clustering and print out results.
      logger.info("Clustering vectors ...");
      ClusterResults.Clusters clusters = ClusterResults.kMeansCluster(resultsVectors, flagConfig);

      printAllCusters(flagConfig, resultsVectors, clusters);

      if (writeCentroidsFile) {
        ClusterResults.writeCentroidsToFile(clusters, flagConfig);
      }

      /**
       * The following block is only relevant and working for bible chapters at the moment.
       */
      if (measureClusterOverlaps) {
        Hashtable<String, int[]> newOverlapResults = clusterOverlapMeasure(
            clusters.clusterMappings, resultsVectors);

        if (mainOverlapResults == null) {
          mainOverlapResults = newOverlapResults;
        } else {
          mergeTables(newOverlapResults, mainOverlapResults);
        }

        for (Enumeration<String> keys = mainOverlapResults.keys(); keys.hasMoreElements();) {
          String key = keys.nextElement(); 
          int[] matchAndTotal = mainOverlapResults.get(key);
          System.out.println(key + "\t" + (float) matchAndTotal[0] / (float) matchAndTotal[1]);
        }
      }
    }
  }

  /**
   * Simple routine that prints clusters to the console output.
   */
  private static void printAllCusters(FlagConfig flagConfig,
      ObjectVector[] resultsVectors, ClusterResults.Clusters clusters) {
    // Main cluster printing routine.
    for (int i = 0; i < flagConfig.numclusters(); ++i) {
      System.out.println("Cluster " + i);
      for (int j = 0; j < clusters.clusterMappings.length; ++j) {
        if (clusters.clusterMappings[j] == i) {
          System.out.print(resultsVectors[j].getObject() + "\t");
        }
      }
      System.out.println("\n*********\n");
    }
  }
}
