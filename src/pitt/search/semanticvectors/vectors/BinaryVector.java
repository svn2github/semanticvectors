package pitt.search.semanticvectors.vectors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.OpenBitSet;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.hashing.Bobcat;

/**
 * Binary implementation of Vector.
 * 
 * Uses an "elemental" representation which is a single bit string (Lucene OpenBitSet).
 * 
 * Superposes on this a "semantic" representation which contains the weights with which different
 * vectors have been added (superposed) onto this one.  Calling {@link #superpose} causes the
 * voting record to be updated, but for performance the votes are not tallied back into the 
 * elemental bit set representation until {@link #normalize} or one of the writing functions 
 * is called.  
 *
 * @author Trevor Cohen
 */
public class BinaryVector implements Vector {
  public static final Logger logger = Logger.getLogger(BinaryVector.class.getCanonicalName());

  // TODO: Determing proper interface for default constants.
  /**
   * Number of decimal places to consider in weighted superpositions of binary vectors.
   * Higher precision requires additional memory during training.
   */
  public static final int BINARY_VECTOR_DECIMAL_PLACES = 2;
  public static final boolean BINARY_BINDING_WITH_PERMUTE = false;
 
  
  private static final int DEBUG_PRINT_LENGTH = 64;
  private Random random;
  private final int dimension;
  
  /**
   * Elemental representation for binary vectors. 
   */
  protected OpenBitSet bitSet;
  private boolean isSparse;

  /** 
   * Representation of voting record for superposition. Each OpenBitSet object contains one bit
   * of the count for the vote in each dimension. The count for any given dimension is derived from
   * all of the bits in that dimension across the OpenBitSets in the voting record.
   * 
   * The precision of the voting record (in number of decimal places) is defined upon initialization.
   * By default, if the first weight added is an integer, rounding occurs to the nearest integer.
   * Otherwise, rounding occurs to the second binary place.
   */ 
  private ArrayList<OpenBitSet> votingRecord;
  
  int decimalPlaces = 0;
  /** Accumulated sum of the weights with which vectors have been added into the voting record */
  int totalNumberOfVotes = 0;
  // TODO(widdows) Understand and comment this.
  int minimum = 0;

  // Used only for temporary internal storage.
  private OpenBitSet tempSet;
  
  public BinaryVector(int dimension) {
    // Check "multiple-of-64" constraint, to facilitate permutation of 64-bit chunks
    if (dimension % 64 != 0) {
      throw new IllegalArgumentException("Dimension should be a multiple of 64: "
          + dimension + " will lead to trouble!");
    }
    this.dimension = dimension;
    this.bitSet = new OpenBitSet(dimension);
    this.isSparse = true;
    this.random = new Random();
     
    }

  /**
   * Returns a new copy of this vector, in dense format.
   */
  @SuppressWarnings("unchecked")
  public BinaryVector copy() {
    BinaryVector copy = new BinaryVector(dimension);
    copy.bitSet = (OpenBitSet) bitSet.clone();
    if (!isSparse)
      copy.votingRecord = (ArrayList<OpenBitSet>) votingRecord.clone();
    return copy;
  }

  public String toString() {
    StringBuilder debugString = new StringBuilder("BinaryVector.");
    if (isSparse) {
      debugString.append("  Elemental.  First " + DEBUG_PRINT_LENGTH + " values are:\n");
      for (int x = 0; x < DEBUG_PRINT_LENGTH; x++) debugString.append(bitSet.getBit(x) + " ");
      debugString.append("\nCardinality " + bitSet.cardinality()+"\n");
    }
    else {
      debugString.append("  Semantic.  First " + DEBUG_PRINT_LENGTH + " values are:\n");
      // output voting record for first DEBUG_PRINT_LENGTH dimension
      debugString.append("\nVOTING RECORD: \n");
      for (int y =0; y < votingRecord.size(); y++)
      {
       for (int x = 0; x < DEBUG_PRINT_LENGTH; x++) debugString.append(votingRecord.get(y).getBit(x) + " ");
      debugString.append("\n");
      }
      
      // TODO - output count from first DEBUG_PRINT_LENGTH dimension
      debugString.append("\nNORMALIZED: ");
      this.normalize();
      for (int x = 0; x < DEBUG_PRINT_LENGTH; x++) debugString.append(bitSet.getBit(x) + " ");
      debugString.append("\n");
      
      // Calculate actual values for first 20 dimension
      double[] actualvals = new double[DEBUG_PRINT_LENGTH];
      debugString.append("COUNTS    : ");

      for (int x =0; x < votingRecord.size(); x++) {
        for (int y = 0; y < DEBUG_PRINT_LENGTH; y++) {
          if (votingRecord.get(x).fastGet(y)) actualvals[y] += Math.pow(2, x); 
        }
      }

      for (int x = 0; x < DEBUG_PRINT_LENGTH; x++) {
        debugString.append((int) ((minimum + actualvals[x]) / Math.pow(10, decimalPlaces)) + " ");
      }

      debugString.append("\nCardinality " + bitSet.cardinality()+"\n");
      debugString.append("Votes " + totalNumberOfVotes+"\n");
      debugString.append("Minimum " + minimum + "\n");
    }
    return debugString.toString();
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  public BinaryVector createZeroVector(int dimension) {
    // Check "multiple-of-64" constraint, to facilitate permutation of 64-bit chunks
    if (dimension % 64 != 0) {
      logger.severe("Dimension should be a multiple of 64: "
          + dimension + " will lead to trouble!");
    }
    return new BinaryVector(dimension);
  }

  @Override
  public boolean isZeroVector() {
    if (isSparse) 
    	{
      return bitSet.cardinality() == 0;
    } else {
      return (votingRecord == null) || (votingRecord.size() == 0);
    }
  }

  @Override
  /**
   * Generates a basic elemental vector with a given number of 1's and otherwise 0's.
   * For binary vectors, the numnber of 1's and 0's must be the same, half the dimension.
   *
   * @return representation of basic binary vector.
   */
  public BinaryVector generateRandomVector(int dimension, int numEntries, Random random) {
    // Check "multiple-of-64" constraint, to facilitate permutation of 64-bit chunks
    if (dimension % 64 != 0) {
      throw new IllegalArgumentException("Dimension should be a multiple of 64: "
          + dimension + " will lead to trouble!");
    }
    // Check for balance between 1's and 0's
    if (numEntries != dimension / 2) {
      logger.severe("Attempting to create binary vector with unequal number of zeros and ones."
          + " Unlikely to produce meaningful results. Therefore, seedlength has been set to "
          + " dimension/2, as recommended for binary vectors");
      numEntries = dimension / 2;
    }

    BinaryVector randomVector = new BinaryVector(dimension);
    randomVector.bitSet = new OpenBitSet(dimension);
    int testPlace = dimension - 1, entryCount = 0;

    // Iterate across dimension of bitSet, changing 0 to 1 if random(1) > 0.5
    // until dimension/2 1's added.
    while (entryCount < numEntries) {	
      testPlace = random.nextInt(dimension);
      if (!randomVector.bitSet.fastGet(testPlace)) {
        randomVector.bitSet.fastSet(testPlace);
        entryCount++;	
      }
    }
    return randomVector;
  }

  @Override
  /**
   * Measures overlap of two vectors using 1 - normalized Hamming distance
   * 
   * Causes this and other vector to be converted to dense representation.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (isZeroVector()) return 0;
    BinaryVector binaryOther = (BinaryVector) other;
    if (binaryOther.isZeroVector()) return 0;
        
    // Calculate hamming distance in place using cardinality and XOR, then return bitset to
    // original state.
    double hammingDistance = OpenBitSet.xorCount(binaryOther.bitSet, this.bitSet);
    return 2*(0.5 - (hammingDistance / (double) dimension));
  }

  @Override
  /**
   * Adds the other vector to this one. If this vector was an elemental vector, the 
   * "semantic vector" components (i.e. the voting record and temporary bitset) will be
   * initialized.
   * 
   * Note that the precision of the voting record (in decimal places) is decided at this point:
   * if the initialization weight is an integer, rounding will occur to the nearest integer.
   * If not, rounding will occur to the second decimal place.
   * 
   * This is an attempt to save space, as voting records can be prohibitively expansive
   * if not contained.
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (weight == 0d) return;
    if (other.isZeroVector()) return;
    BinaryVector binaryOther = (BinaryVector) other;
    if (isSparse) {
      if (Math.round(weight) != weight) {
        decimalPlaces = BINARY_VECTOR_DECIMAL_PLACES; 
      }
      elementalToSemantic();
    }

    if (permutation != null) {
      // Rather than permuting individual dimensions, we permute 64 bit groups at a time.
      // This should be considerably quicker, and dimension/64 should allow for sufficient
      // permutations
      if (permutation.length != dimension / 64) {
        throw new IllegalArgumentException("Binary vector of dimension " + dimension
            + " must have permutation of length " + dimension / 64
            + " not " + permutation.length);
      }
      //TODO permute in place and reverse, to avoid creating a new BinaryVector here
      BinaryVector temp = binaryOther.copy();
      temp.permute(permutation);
      superposeBitSet(temp.bitSet, weight);
    }
    else {
      superposeBitSet(binaryOther.bitSet, weight);
    }
  }

  /**
   * This method is the first of two required to facilitate superposition. The underlying representation
   * (i.e. the voting record) is an ArrayList of OpenBitSet, each with dimension "dimension", which can
   * be thought of as an expanding 2D array of bits. Each column keeps count (in binary) for the respective
   * dimension, and columns are incremented in parallel by sweeping a bitset across the rows. In any dimension
   * in which the BitSet to be added contains a "1", the effect will be that 1's are changed to 0's until a
   * new 1 is added (e.g. the column '110' would become '001' and so forth).
   * 
   * The first method deals with floating point issues, and accelerates superposition by decomposing
   * the task into segments.
   * 
   * @param incomingBitSet
   * @param weight
   */
    protected void superposeBitSet(OpenBitSet incomingBitSet, double weight) {
    // If fractional weights are used, encode all weights as integers (1000 x double value).
    weight = (int) Math.round(weight * Math.pow(10, decimalPlaces));
    if (weight == 0) return;

    // Keep track of number (or cumulative weight) of votes.
    totalNumberOfVotes += weight;

    // Decompose superposition task such that addition of some power of 2 (e.g. 64) is accomplished
    // by beginning the process at the relevant row (e.g. 7) instead of starting multiple (e.g. 64)
    // superposition processes at the first row.
    int logFloorOfWeight = (int) (Math.floor(Math.log(weight)/Math.log(2)));

    if (logFloorOfWeight < votingRecord.size() - 1) {
      while (logFloorOfWeight > 0) {
        superposeBitSetFromRowFloor(incomingBitSet, logFloorOfWeight);	
        weight = weight - (int) Math.pow(2,logFloorOfWeight);
        logFloorOfWeight = (int) (Math.floor(Math.log(weight)/Math.log(2)));	
      }
    }

    // Add remaining component of weight incrementally.
    for (int x = 0; x < weight; x++)
      superposeBitSetFromRowFloor(incomingBitSet, 0);
  }

  /**
   * Performs superposition from a particular row by sweeping a bitset across the voting record
   * such that for any column in which the incoming bitset contains a '1', 1's are changed
   * to 0's until a new 1 can be added, facilitating incrementation of the
   * binary number represented in this column.
   * 
   * @param incomingBitSet the bitset to be added
   * @param rowfloor the index of the place in the voting record to start the sweep at
   */
  protected void superposeBitSetFromRowFloor(OpenBitSet incomingBitSet, int rowfloor) {
    // Attempt to save space when minimum value across all columns > 0
    // by decrementing across the board and raising the minimum where possible.
    int max = getMaximumSharedWeight();	

    if (max > 0) {	
      decrement(max);
    }

    // Handle overflow: if any column that will be incremented
    // contains all 1's, add a new row to the voting record.
    tempSet.xor(tempSet);
    tempSet.xor(incomingBitSet);

    for (int x = rowfloor; x < votingRecord.size() && tempSet.cardinality() > 0; x++) {
      tempSet.and(votingRecord.get(x));
    }

    if (tempSet.cardinality() > 0) {
      votingRecord.add(new OpenBitSet(dimension));
    }

    // Sweep copy of bitset to be added across rows of voting record.
    // If a new '1' is added, this position in the copy is changed to zero
    // and will not affect future rows.
    // The xor step will transform 1's to 0's or vice versa for 
    // dimension in which the temporary bitset contains a '1'.
    votingRecord.get(rowfloor).xor(incomingBitSet);

    tempSet.xor(tempSet);
    tempSet.xor(incomingBitSet);

    for (int x = rowfloor + 1; x < votingRecord.size(); x++) {	
      tempSet.andNot(votingRecord.get(x-1)); //if 1 already added, eliminate dimension from tempSet
      votingRecord.get(x).xor(tempSet);	
      votingRecord.get(x).trimTrailingZeros(); //attempt to save in sparsely populated rows
    }
  }

  /**
   * Reverses a string - simplifies the decoding of the binary vector for the 'exact' method
   * although it wouldn't be difficult to reverse the counter instead
   */
  public static String reverse(String str) {
    if ((null == str) || (str.length() <= 1)) {
      return str;
    }
    return new StringBuffer(str).reverse().toString();
  }

  /**
   * Sets {@link #tempSet} to be a bitset with a "1" in the position of every dimension
   * in the {@link #votingRecord} that exactly matches the target number.
   */
  private void setTempSetToExactMatches(int target) {
    if (target == 0) {
      tempSet.set(0, dimension);
      tempSet.xor(votingRecord.get(0));
      for (int x = 1; x < votingRecord.size(); x++)
        tempSet.andNot(votingRecord.get(x));
    } else
    {
    String inbinary = reverse(Integer.toBinaryString(target));
    tempSet.xor(tempSet);
    tempSet.xor(votingRecord.get(inbinary.indexOf("1")));
     
    for (int q =0; q < votingRecord.size(); q++) {
      if (q < inbinary.length() && inbinary.charAt(q) == '1')
          tempSet.and(votingRecord.get(q));	
        else 
          tempSet.andNot(votingRecord.get(q));	
    		}
    }
  }

  /**
   * This method is used determine which dimension will receive 1 and which 0 when the voting
   * process is concluded. It produces an OpenBitSet in which
   * "1" is assigned to all dimension with a count > 50% of the total number of votes (i.e. more 1's than 0's added)
   * "0" is assigned to all dimension with a count < 50% of the total number of votes (i.e. more 0's than 1's added)
   * "0" or "1" are assigned to all dimension with a count = 50% of the total number of votes (i.e. equal 1's and 0's added)
   * 
   * @return an OpenBitSet representing the superposition of all vectors added up to this point
   */
  protected OpenBitSet concludeVote() {
    if (votingRecord.size() == 0 || votingRecord.size() == 1 && votingRecord.get(0).cardinality() ==0) return new OpenBitSet(dimension);
    else return concludeVote(totalNumberOfVotes);
  }

  protected OpenBitSet concludeVote(int target) {
    int target2 = (int) Math.ceil((double) target / (double) 2);
    target2 = target2 - minimum;

    // Unlikely other than in testing: minimum more than half the votes
    if (target2 < 0) {
      OpenBitSet ans = new OpenBitSet(dimension);
      ans.set(0, dimension);
      return ans;
    }

    boolean even = (target % 2 == 0);
    OpenBitSet result = concludeVote(target2, votingRecord.size() - 1);

    if (even) {
      setTempSetToExactMatches(target2);
      boolean switcher = true;
      // 50% chance of being true with split vote.
      for (int q = 0; q < dimension; q++) {
        if (tempSet.fastGet(q)) {
          switcher = !switcher;
          if (switcher) tempSet.fastClear(q);
        }
      }
      result.andNot(tempSet);
    }
    return result;
  }

  protected OpenBitSet concludeVote(int target, int row_ceiling) {
    /**
	  logger.info("Entering conclude vote, target " + target + " row_ceiling " + row_ceiling + 
    		"voting record " + votingRecord.size() + 
    		" minimum "+ minimum + " index "+  Math.log(target)/Math.log(2) +
         " vector\n" + toString());
     **/
    if (target == 0) {
      OpenBitSet atLeastZero = new OpenBitSet(dimension);
      atLeastZero.set(0, dimension);
      return atLeastZero;
    }

    double rowfloor = Math.log(target)/Math.log(2);
    int row_floor = (int) Math.floor(rowfloor);  //for 0 index
    int remainder =  target - (int) Math.pow(2,row_floor);

    //System.out.println(target+"\t"+rowfloor+"\t"+row_floor+"\t"+remainder);

    if (row_ceiling == 0 && target == 1) {
      return votingRecord.get(0);
    }

    if (remainder == 0) {
      // Simple case - the number we're looking for is 2^n, so anything with a "1" in row n or above is true.
      OpenBitSet definitePositives = new OpenBitSet(dimension);
      for (int q = row_floor; q <= row_ceiling; q++)
        definitePositives.or(votingRecord.get(q));
      return definitePositives;
    }
    else {
      // Simple part of complex case: first get anything with a "1" in a row above n (all true).
      OpenBitSet definitePositives = new OpenBitSet(dimension);
      for (int q = row_floor+1; q <= row_ceiling; q++)
        definitePositives.or(votingRecord.get(q));

      // Complex part of complex case: get those that have a "1" in the row of n.
      OpenBitSet possiblePositives = (OpenBitSet) votingRecord.get(row_floor).clone();
      OpenBitSet definitePositives2 = concludeVote(remainder, row_floor-1);

      possiblePositives.and(definitePositives2);
      definitePositives.or(possiblePositives);		
      return definitePositives;
    }
  }

  /**
   * Decrement every dimension. Assumes at least one count in each dimension
   * i.e: no underflow check currently - will wreak havoc with zero counts
   */
  public void decrement() {	
    tempSet.set(0, dimension);
    for (int q = 0; q < votingRecord.size(); q++) {
      votingRecord.get(q).xor(tempSet);
      tempSet.and(votingRecord.get(q));
    }
  }

  /**
   * Decrement every dimension by the number passed as a parameter. Again at least one count in each dimension
   * i.e: no underflow check currently - will wreak havoc with zero counts
   */
  public void decrement(int weight) {
    if (weight == 0) return;
    minimum+= weight;

    int logfloor = (int) (Math.floor(Math.log(weight)/Math.log(2)));

    if (logfloor < votingRecord.size() - 1) {
      while (logfloor > 0) {
        selectedDecrement(logfloor);	
        weight = weight - (int) Math.pow(2,logfloor);
        logfloor = (int) (Math.floor(Math.log(weight)/Math.log(2)));
      }
    }

    for (int x = 0; x < weight; x++) {
      decrement();
    }
  }

  public void selectedDecrement(int floor) {
    tempSet.set(0, dimension);
    for (int q = floor; q < votingRecord.size(); q++) {
      votingRecord.get(q).xor(tempSet);
      tempSet.and(votingRecord.get(q));
    }
  }

  /**
   * Returns the highest value shared by all dimensions.
   */
  protected int getMaximumSharedWeight() {
    int thismaximum = 0;
    tempSet.xor(tempSet);  // Reset tempset to zeros.
    for (int x = votingRecord.size() - 1; x >= 0; x--) {
      tempSet.or(votingRecord.get(x));
      if (tempSet.cardinality() == dimension) {
        thismaximum += (int) Math.pow(2, x);
        tempSet.xor(tempSet);
      }
    }
    return thismaximum;	
  }

  @Override
  /**
   * Implements binding using permutations and XOR. 
   */
  public void bind(Vector other, int direction) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    BinaryVector binaryOther = (BinaryVector) other.copy();
    if (direction > 0) {
    	//as per Kanerva 2009: bind(A,B) = perm+(A) XOR B = C
    	//this also functions as the left inverse:  left inverse (A,C) = perm+(A) XOR C  = B 
    	this.permute(PermutationUtils.getShiftPermutation(VectorType.BINARY, dimension, 1)); //perm+(A)
    	this.bitSet.xor(binaryOther.bitSet); //perm+(A) XOR B
      
    } else {
    	//as per Kanerva 2009: right inverse(C,B) =  perm-(C XOR B) = perm-(perm+(A)) = A 
    	this.bitSet.xor(binaryOther.bitSet); //C XOR B
    	this.permute(PermutationUtils.getShiftPermutation(VectorType.BINARY, dimension, -1)); //perm-(C XOR B) = A
 }
  }
  
  
  @Override
  /**
   * Implements inverse of binding using permutations and XOR. 
   */
  public void release(Vector other, int direction) {
    if (!BINARY_BINDING_WITH_PERMUTE)
	   bind(other);
	  else
		  bind (other, direction);
  }
  
  @Override
  /**
   * Implements binding using exclusive OR. 
   */
  public void bind(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (!BINARY_BINDING_WITH_PERMUTE) {
      BinaryVector binaryOther = (BinaryVector) other;
      this.bitSet.xor(binaryOther.bitSet);
    } else {
    	bind(other, 1);
    }
  }
  
  @Override
  /**
   * Implements inverse binding using exclusive OR. 
   */
  public void release(Vector other) {
	 
	 if (!BINARY_BINDING_WITH_PERMUTE)
	   bind(other);
	 else
		 bind(other, -1);
  }

  @Override
  /**
   * Normalizes the vector, converting sparse to dense representations in the process. This approach deviates from the "majority rule" 
   * approach that is standard in the Binary Spatter Code(). Rather, the probability of assigning a one in a particular dimension 
   * is a function of the probability of encountering the number of votes in the voting record in this dimension.
   *
   * This will be slower than normalizeBSC() below, but discards less information with positive effects on accuracy in preliminary experiments
   * 
   * As a simple example to illustrate why this would be the case, consider the superposition of vectors for the terms "jazz","jazz" and "rock" 
   * With the BSC normalization, the vector produced is identical to "jazz" (as jazz wins the vote in each case). With probabilistic normalization,
   * the vector produced is somewhat similar to both "jazz" and "rock", with a similarity that is proportional to the weights assigned to the 
   * superposition, e.g. 0.624000:jazz;  0.246000:rock
   * 
   */
  
 public void normalize() {
	  
	  
	  if (votingRecord == null) return;
	  if (votingRecord.size() == 1)
	  {
		  this.bitSet = votingRecord.get(0);
		  return;
	  }
	  //clear bitset;
	  this.bitSet.xor(this.bitSet);
	  
	  //Ensure that the same set of superposed vectors will always produce the same result
	  long theSuperpositionSeed = 0;
	  for (int q =0; q < votingRecord.size(); q++)
		  theSuperpositionSeed += votingRecord.get(q).getBits()[0];
	  
	  random.setSeed(theSuperpositionSeed);
	  
	  //Determine value above the universal minimum for each dimension of the voting record
	  int[] counts = new int[dimension];
	  int max = totalNumberOfVotes;
	   
	  //Determine the maximum possible votes on the voting record
	  int maxpossiblevotesonrecord = 0;
	  
	  for (int q=0; q < votingRecord.size(); q++)
		maxpossiblevotesonrecord += Math.pow(2, q);  
	  
	  //For each possible value on the record, get a BitSet with a "1" in the 
	  //position of the dimensions that match this value
	  for (int x = 1; x <= maxpossiblevotesonrecord; x++)
	  {
		    this.setTempSetToExactMatches(x);
		 	
		 	//For each y==1 on said BitSet (indicating votes in dimension[y] == x)
		    int y = tempSet.nextSetBit(0);
		 	
		 	  while (y != -1)
		 	  {
		 		  //determine total number of votes 
				  double votes = minimum+x;
				  
				  //calculate standard deviations above/below the mean of max/2 
				  double z = (votes - (max/2)) / (Math.sqrt(max)/2);
				  
				  //find proportion of data points anticipated within z standard deviations of the mean (assuming approximately normal distribution)
				  double proportion = erf(z/Math.sqrt(2));
				  
				  //convert into a value between 0 and 1 (i.e. centered on 0.5 rather than centered on 0)
				  proportion = (1+proportion) /2;
				  
				  //probabilistic normalization
				  if ((random.nextDouble()) <= proportion) this.bitSet.fastSet(y);
		 		  
				  y++;
		 		  y = tempSet.nextSetBit(y);
		 	  }
			  
	  }
		  
	  //housekeeping
	    votingRecord = new ArrayList<OpenBitSet>();
	    votingRecord.add((OpenBitSet) bitSet.clone());
	    totalNumberOfVotes = 1;
	    tempSet = new OpenBitSet(dimension);
	    minimum = 0;
  }
  

  // approximation of error function, equation 7.1.27 from
  // Abramowitz, M. and Stegun, I. A. (Eds.). "Repeated Integrals of the Error Function." §7.2 
  //in Handbook of Mathematical Functions with Formulas, Graphs, and Mathematical Tables, 
  //9th printing. New York: Dover, pp. 299-300, 1972.
  // error of approximation <= 5*10^-4
  
  public double erf(double z)
  {
	  //erf(-x) == -erf(x)
	  double sign = Math.signum(z);
	  z = Math.abs(z);
	
	  double a1 = 0.278393, a2 = 0.230389, a3 = 0.000972, a4 = 0.078108;
	  double sumterm = 1 + a1*z + a2*Math.pow(z,2) + a3*Math.pow(z,3) + a4*Math.pow(z,4);
	  return sign * ( 1-1/(Math.pow(sumterm, 4)));
	  
  }
  
  
  
 /**
  * Faster normalization according to the Binary Spatter Code's "majority" rule 
  */
  
  public void normalizeBSC() {
    if (!isSparse)
    this.bitSet = concludeVote();
    
    votingRecord = new ArrayList<OpenBitSet>();
    votingRecord.add((OpenBitSet) bitSet.clone());
    totalNumberOfVotes = 1;
    tempSet = new OpenBitSet(dimension);
    minimum = 0;
    
  }
  
  /**
   * Counts votes without normalizing vector (i.e. voting record is not altered). Used in SemanticVectorCollider.
   */
  
  public void tallyVotes() {
	  if (!isSparse)
		    this.bitSet = concludeVote();
  }

  @Override
  /**
   * Writes vector out to object output stream.  Converts to dense format if necessary.
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
    if (isSparse) {
      elementalToSemantic();
    }
    long[] bitArray = bitSet.getBits();

    for (int i = 0; i < bitArray.length; i++) {
      try {
        outputStream.writeLong(bitArray[i]);
      } catch (IOException e) {
        logger.severe("Couldn't write binary vector to lucene output stream.");
        e.printStackTrace();
      }
    }
  }

  
  
  
  @Override
  /**
   * Reads a (dense) version of a vector from a Lucene input stream. 
   */
  public void readFromLuceneStream(IndexInput inputStream) {
    long bitArray[] = new long[(dimension / 64)];

    for (int i = 0; i < dimension / 64; ++i) {
      try {
        bitArray[i] = inputStream.readLong();
      } catch (IOException e) {
        logger.severe("Couldn't read binary vector from lucene output stream.");
        e.printStackTrace();
      }
    }
    this.bitSet = new OpenBitSet(bitArray, bitArray.length);
    this.isSparse = true;
  }

  @Override
  /**
   * Writes vector to a string of the form 010 etc. (no delimiters). 
   * 
   * No terminating newline or delimiter.
   */
  public String writeToString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < dimension; ++i) {
      builder.append(Integer.toString(bitSet.getBit(i)));
    }
    return builder.toString();
  }

  /**
   * Writes vector to a string of the form 010 etc. (no delimiters). 
   * 
   * No terminating newline or delimiter.
   */
  public String writeLongToString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < (bitSet.getBits().length); ++i) {
      builder.append(Long.toString(bitSet.getBits()[i])+"|");
    }
    return builder.toString();
  }

  @Override
  /**
   * Writes vector from a string of the form 01001 etc.
   */
  public void readFromString(String input) {
    if (input.length() != dimension) {
      throw new IllegalArgumentException("Found " + (input.length()) + " possible coordinates: "
          + "expected " + dimension);
    }

    for (int i = 0; i < dimension; ++i) {
      if (input.charAt(i) == '1')
        bitSet.fastSet(i);
    }
  }

  /**
   * Automatically translate elemental vector (no storage capacity) into 
   * semantic vector (storage capacity initialized, this will occupy RAM)
   */
  protected void elementalToSemantic() {
    if (!isSparse) {
      logger.warning("Tried to transform an elemental vector which is not in fact elemental."
          + "This may be a programming error.");
      return;
    }
    votingRecord = new ArrayList<OpenBitSet>();
    votingRecord.add((OpenBitSet) bitSet.clone());
    tempSet = new OpenBitSet(dimension);
       
    isSparse = false;
  }

  /**
   * Permute the long[] array underlying the OpenBitSet binary representation
   */
  public void permute(int[] permutation) {
    if (permutation.length != getDimension() / 64) {
      throw new IllegalArgumentException("Binary vector of dimension " + getDimension()
          + " must have permutation of length " + getDimension() / 64
          + " not " + permutation.length);
    }
    //TODO permute in place without creating additional long[] (if proves problematic at scale)
    long[] coordinates = bitSet.getBits();
    long[] newCoordinates = new long[coordinates.length];
    for (int i = 0; i < coordinates.length; ++i) {
      int positionToAdd = i;
      positionToAdd = permutation[positionToAdd];
      newCoordinates[i] = coordinates[positionToAdd];
    }
    bitSet.setBits(newCoordinates);
  }

  // Available for testing and copying.
  protected BinaryVector(OpenBitSet inSet) {
    this.dimension = (int) inSet.size();
    this.bitSet = inSet;
  }

  // Available for testing
  protected int bitLength() {
    return bitSet.getBits().length;
  }

  // Monitor growth of voting record.
  protected int numRows() {
    if (isSparse) return 0;
    return votingRecord.size();
  }
  
  
}

