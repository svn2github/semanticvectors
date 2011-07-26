package pitt.search.semanticvectors;

import pitt.search.semanticvectors.vectors.*;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

  public static Test suite() {
    TestSuite suite = new TestSuite("Test for pitt.search.semanticvectors");
    //$JUnit-BEGIN$
    suite.addTestSuite(VectorUtilsTest.class);
    suite.addTestSuite(FlagsTest.class);
    suite.addTestSuite(CompoundVectorBuilderTest.class);
    suite.addTestSuite(VectorStoreWriterTest.class);
    suite.addTestSuite(VectorStoreRAMTest.class);
    suite.addTestSuite(VectorStoreSparseRAMTest.class);
    suite.addTestSuite(RealVectorTest.class);
    suite.addTestSuite(BinaryVectorTest.class);
    suite.addTestSuite(ComplexVectorTest.class);
    suite.addTestSuite(PermutationUtilsTest.class);
    //$JUnit-END$
    return suite;
  }

}
