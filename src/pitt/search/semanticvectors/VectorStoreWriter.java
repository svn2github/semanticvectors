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

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexOutput;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * This class provides methods for serializing a VectorStore to disk.
 * 
 * <p>
 * The serialization currently presumes that the object (in the ObjectVectors)
 * should be serialized as a String.
 * 
 * <p>
 * The implementation uses Lucene's I/O package, which proved much faster
 * than the native java.io.DataOutputStream.
 * 
 * @see ObjectVector
 */
public class VectorStoreWriter {
  private static final Logger logger = Logger.getLogger(VectorStoreWriter.class.getCanonicalName());
  
  /**
   * Generates a single string containing global header information for a vector store.
   * This includes the vector type and the dimension.
   * 
   * String created is in the form that {@code Flags} would expect to parse, e.g.,
   * "-vectortype real -dimension 100".
   */
  public static String generateHeaderString() {
    return "-vectortype " + Flags.vectortype + " -dimension " + Integer.toString(Flags.dimension);
  }

  /**
   * Writes vectors in text or lucene format depending on {@link Flags#indexfileformat}.
   * 
   * @param vectorFileName The name of the file to write to
   * @param objectVectors The vector store to be written to disk
   */
  public boolean writeVectors(String vectorFileName, VectorStore objectVectors) {
	if (Flags.indexfileformat.equals("lucene")) {
	  return writeVectorsInLuceneFormat(vectorFileName, objectVectors);
	} else if (Flags.indexfileformat.equals("text")) {
	  return writeVectorsInTextFormat(vectorFileName, objectVectors);
	} else {
	  throw new RuntimeException("Unrecognized indexfileformat: '" + Flags.indexfileformat + "'");
	}
  }
	 
  /**
   * Outputs a vector store in Lucene binary format.
   * 
   * @param vectorFileName The name of the file to write to
   * @param objectVectors The vector store to be written to disk
   */
  public boolean writeVectorsInLuceneFormat(String vectorFileName, VectorStore objectVectors) {
	logger.info("About to write " + objectVectors.getNumVectors() + " vectors of dimension "
			+ Flags.dimension + " to Lucene format file: " + vectorFileName);
	try {
      File vectorFile = new File(vectorFileName);
      String parentPath = vectorFile.getParent();
      if (parentPath == null) parentPath = "";
      FSDirectory fsDirectory = FSDirectory.open(new File(parentPath));
      IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName());
      writeToIndexOutput(objectVectors, outputStream);
      outputStream.close();
      fsDirectory.close();
      return true;
	} catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }
  
  /**
   * Writes the object vectors to this Lucene output stream.
   */
  public void writeToIndexOutput(VectorStore objectVectors, IndexOutput outputStream)
      throws IOException {
    // Write header giving vector type and dimension for all vectors.
    outputStream.writeString(generateHeaderString());
    Enumeration<ObjectVector> vecEnum = objectVectors.getAllVectors();

    // Write each vector.
    while (vecEnum.hasMoreElements()) {
      ObjectVector objectVector = vecEnum.nextElement();
      outputStream.writeString(objectVector.getObject().toString());
      objectVector.getVector().writeToLuceneStream(outputStream);
    }
    logger.info("Finished writing vectors.");
  }

  /**
   * Outputs a vector store as a plain text file.
   * 
   * @param vectorFileName The name of the file to write to
   * @param objectVectors The vector store to be written to disk
   */
  public boolean writeVectorsInTextFormat(String vectorFileName, VectorStore objectVectors) {
    logger.info("About to write " + objectVectors.getNumVectors() + " vectors of dimension "
    		+ Flags.dimension + " to text file: " + vectorFileName);
    try {
      BufferedWriter outBuf = new BufferedWriter(new FileWriter(vectorFileName));
      writeToTextBuffer(objectVectors, outBuf);
      outBuf.close();
      logger.info("Finished writing vectors.");
      return true;
    }
    catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public void writeToTextBuffer(VectorStore objectVectors, BufferedWriter outBuf)
      throws IOException {
    Enumeration<ObjectVector> vecEnum = objectVectors.getAllVectors();
    
    // Write header giving vector type and dimension for all vectors.
    outBuf.write(generateHeaderString() + "\n");

    // Write each vector.
    while (vecEnum.hasMoreElements()) {
      ObjectVector objectVector = vecEnum.nextElement();
      outBuf.write(objectVector.getObject().toString() + "|");
      outBuf.write(objectVector.getVector().writeToString());
      outBuf.write("\n");
    }    
  }
}
