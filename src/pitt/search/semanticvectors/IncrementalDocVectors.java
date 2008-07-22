

package pitt.search.semanticvectors;

import java.io.IOException;
import java.lang.Integer;
import org.apache.lucene.index.*;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;

/**
 * generates document vectors incrementally
 * requires a
 * @param termVectorData Has all the information needed to create doc vectors.
 * @param vectorFile Filename for the document vectors
 */
public class IncrementalDocVectors {

	private TermVectorsFromLucene termVectorData;
	private IndexReader indexReader;

	/**
	 * Constructor that gets everything it needs from a
	 * TermVectorsFromLucene object and writes to a named file.
	 * @param termVectorData Has all the information needed to create doc vectors.
	 * @param vectorFile Filename for the document vectors
	 */
	public IncrementalDocVectors (TermVectorsFromLucene termVectorData, String vectorFile)
		throws IOException {
		this.termVectorData = termVectorData;
		this.indexReader = termVectorData.getIndexReader();

		/* Check that the Lucene index contains Term Positions */
		java.util.Collection fields_with_positions =
			indexReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION);

		if (fields_with_positions.isEmpty()) {
			System.err.println("Incremental document indexing requires a Lucene index containing TermPositionVectors");
			System.err.println("Try rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
			System.exit(0);
		}

		int numdocs = indexReader.numDocs();

		// Open file and write headers.
		MMapDirectory dir = new MMapDirectory();
		IndexOutput outputStream = dir.createOutput(vectorFile);
		float[] tmpVector = new float[ObjectVector.vecLength];

		int counter = 0;
		System.err.println("Write vectors incrementally to file " + vectorFile);

		// Write header giving number of dimensions for all vectors.
		outputStream.writeString("-dimensions");
		outputStream.writeInt(ObjectVector.vecLength);


		// Iterate through documents.
		for (int dc=0; dc < numdocs; dc++) {
			/* output progress counter */
			if (( dc % 10000 == 0 ) || ( dc < 10000 && dc % 1000 == 0 )) {
				System.err.print(dc + " ... ");
			}


			String docID = Integer.toString(dc);

			// Use filename and path rather than Lucene index number for document vector.
			if (this.indexReader.document(dc).getField("path") != null) {
				docID = this.indexReader.document(dc).getField("path").stringValue();
			} else {
				// For bilingual docs, we index "filename" not "path",
				// since there are two system paths, one for each
				// language. So if there was no "path", get the "filename".
				docID = this.indexReader.document(dc).getField("filename").stringValue();
			}

			float[] docVector = new float[ObjectVector.vecLength];

			for (String fieldName: termVectorData.getFieldsToIndex()) {
				TermPositionVector vex =
					(TermPositionVector) indexReader.getTermFreqVector(dc, fieldName);

				if (vex !=null) {
					// Get terms in document and term frequencies.
					String[] terms = vex.getTerms();
					int[] freqs = vex.getTermFrequencies();

					for (int b = 0; b < freqs.length; ++b) {
						String term = terms[b];
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
						if (termVector.length > 0) {
							for (int j = 0; j < ObjectVector.vecLength; ++j) {
								docVector[j] += freq * termVector[j];
							}
						}
					}
				}
				// All fields in document have been processed.
				// Write out documentID and normalized vector.
				outputStream.writeString(docID);
				docVector = VectorUtils.getNormalizedVector(docVector);

				for (int i = 0; i < ObjectVector.vecLength; ++i) {
					outputStream.writeInt(Float.floatToIntBits(docVector[i]));
				}
			}
		} // Finish iterating through documents.

		System.err.println("Finished writing vectors.");
		outputStream.flush();
		outputStream.close();
	}
}
