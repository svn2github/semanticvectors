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

import java.io.File;
import java.io.IOException;
import java.lang.Float;
import java.util.Enumeration;
import java.util.StringTokenizer;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexInput;

/**
   This class provides methods for reading a VectorStore from disk. <p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   The implementation uses Lucene's I/O package, which proved much faster
   than the native java.io.DataOutputStream
   @see ObjectVector
**/
public class VectorStoreReader implements CloseableVectorStore {
	private String vectorFileName;
	private File vectorFile;
	private FSDirectory fsDirectory;
  private IndexInput indexInput;
  private boolean hasHeader;

	public FSDirectory fsDirectory() {
		return this.fsDirectory;
	}

  public VectorStoreReader (String vectorFileName) throws IOException {
		this.vectorFileName = vectorFileName;
		this.vectorFile = new File(vectorFileName);
    try {
			String parentPath = this.vectorFile.getParent();
			if (parentPath == null) parentPath = "";
			this.fsDirectory = FSDirectory.getDirectory(parentPath);
			this.indexInput = fsDirectory.openInput(vectorFile.getName());
      // Read number of dimensions from header information.
      String test = indexInput.readString();
      // Include "-" character to avoid unlikely case that first term is "dimensions"!
      if ((test.equalsIgnoreCase("-dimensions"))) {
        Flags.dimension = indexInput.readInt();
        this.hasHeader = true;
      }
      else {
        System.err.println("No file header for file " + vectorFile +
                           "\nAttempting to process with default vector length: " +
                           Flags.dimension +
                           "\nIf this fails, consider rebuilding indexes - existing " +
                           "ones were probably created with old version of software.");
        this.hasHeader = false;
      }
    } catch (IOException e) {
      System.out.println("Cannot open file: " + this.vectorFileName + "\n" + e.getMessage());
    }
  }

	public void close() {
		try {
			this.indexInput.close();
		}	catch (IOException e) {
			System.err.println("Cannot close resources from file: " + this.vectorFile
												 + "\n" + e.getMessage());
		}
		this.fsDirectory.close();
	}

  public Enumeration getAllVectors() {
    try {
      indexInput.seek(0);
      if (hasHeader) {
        indexInput.readString();
        indexInput.readInt();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return new VectorEnumeration(indexInput);
  }

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * @param desiredObject - the string you're searching for
	 * @return vector from the VectorStore, or null if not found. 
   */
  public float[] getVector(Object desiredObject) {
    try {
      indexInput.seek(0);
      if (hasHeader) {
        indexInput.readString();
        indexInput.readInt();
      }
      while (indexInput.getFilePointer() < indexInput.length() - 1) {
        if (indexInput.readString().equals(desiredObject)) {
          float[] vector = new float[Flags.dimension];
          for (int i = 0; i < Flags.dimension; ++i) {
            vector[i] = Float.intBitsToFloat(indexInput.readInt());
          }
          return vector;
        }
        else{
          indexInput.seek(indexInput.getFilePointer() + 4*Flags.dimension);
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    System.err.println("Didn't find vector for '" + desiredObject + "'");
    return null;
  }

	/**
	 * Trivial (costly) implementation of getNumVectors that iterates and counts vectors.
	 */
	public int getNumVectors() {
		Enumeration allVectors = this.getAllVectors();
		int i = 0;
		while (allVectors.hasMoreElements()) {
			allVectors.nextElement();
			++i;
		}
		return i;
	}

  /**
   * Implements the hasMoreElements() and nextElement() methods
   * to give Enumeration interface from store on disk.
   */
  public class VectorEnumeration implements Enumeration {
    IndexInput indexInput;

    public VectorEnumeration (IndexInput indexInput) {
      this.indexInput = indexInput;
    }

    public boolean hasMoreElements() {
      return (indexInput.getFilePointer() < indexInput.length());
    }

    public ObjectVector nextElement() {
      String object = null;
      float[] vector = new float[Flags.dimension];
      try {
        object = indexInput.readString();
        for (int i = 0; i < Flags.dimension; ++i) {
          vector[i] = Float.intBitsToFloat(indexInput.readInt());
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      return new ObjectVector(object, vector);
    }
  }
}
