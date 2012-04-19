package martin.michael.patrick;

import static org.junit.Assert.*;

import java.util.TreeMap;

import org.junit.Test;

import martin.michael.patrick.SimpleCacheProber.DetectionStyle;

/**
 * Test class for {@link SimpleCacheProber} : exercises the individual steps in the detection process.
 * Built against JUnit 4. 
 * @author Patrick Martin
 *
 */
public class SimpleCacheProberTest {

	/**
	 * Test method for {@link martin.michael.patrick.SimpleCacheProber#findStep(java.util.Map, int, double, DetectionStyle)}
	 * Checks detection of a profile without a single step works.
	 */
	@SuppressWarnings({ "nls", "boxing" })
	@Test
	public void testFindStepLine() {
		
		TreeMap<Integer, Double> dataset = new TreeMap<Integer, Double>();

		// Synthesise a data set with a step over several domain points 
		for (int i = 1 ; i < 16 ; i++) {
			dataset.put(1 << i, ((i <6)?1.0:3.0) + ((i <7)?1.0:3.0));
		}
				
		assertEquals("data step not detected correctly",
				     64,
				     new SimpleCacheProber().findStep(dataset, 1, 0.2, DetectionStyle.SoftEdge));
	}


	/**
	 * Test method for @link martin.michael.patrick.SimpleCacheProber#findStep(java.util.Map, int, double, DetectionStyle).
	 * Checks detection of a profile with a step works.
	 */
	@SuppressWarnings({ "nls", "boxing" })
	@Test
	public void testFindStepCache() {
		
		TreeMap<Integer, Double> dataset = new TreeMap<Integer, Double>();
		
		final int DOMAIN_STEP = 20;
		// Synthesise a data set with a step function 
		for (int i = 9 ; i < 26 ; i++) {
			dataset.put(1 << i, (i < (DOMAIN_STEP - 1))?1.0:3.0);
		}
						
		assertEquals("data step not detected correctly",
					 1 << DOMAIN_STEP, 
					 new SimpleCacheProber().findStep(dataset, 1, 0.2)); 
	}
	
	/**
	 * Test method for {@link martin.michael.patrick.SimpleCacheProber#findStep(java.util.Map, int, double, DetectionStyle) }
	 * Checks cache line detection works on real data.
	 */
	@SuppressWarnings("nls")
	@Test
	public void testFindCacheLine() {
		
		TreeMap<Integer, Double> dataset = getCacheLineTestData();
		
		assertEquals("cache line not detected correctly",
				     64,
				     new SimpleCacheProber().findStep(dataset, 1, 0.2, DetectionStyle.SoftEdge));
	}

	/**
	 * Test method for {@link martin.michael.patrick.SimpleCacheProber#findStep(java.util.Map, int, double, DetectionStyle)}.
	 * Checks L1 cache detection works on real data.
	 */
	@SuppressWarnings("nls")
	@Test
	public void testProbeL1Cache() {

		TreeMap<Integer, Double> dataset = getCacheCorei5TestData();

		assertEquals("L1 not detected correctly",
					 64 * 1024,
					 new SimpleCacheProber().findStep(dataset, 1024, 1, DetectionStyle.SoftEdge));		
		
	}
	
	/**
	 * Test method for {@link martin.michael.patrick.SimpleCacheProber#findStep(java.util.Map, int, double, DetectionStyle)}.
	 * Checks L2 cache detection works on real data.
	 */
	@SuppressWarnings("nls")
	@Test
	public void testProbeL2Cache() {

		TreeMap<Integer, Double> dataset = getCacheCorei5TestData();

		assertEquals("L2 not detected correctly",
				     4 * 1024 * 1024,
				     new SimpleCacheProber().findStep(dataset, 131072, 2, DetectionStyle.SoftEdge));		
		
	}

	/**
	 * Test method for {@link martin.michael.patrick.SimpleCacheProber#probeCacheLine()}.
	 * Checks cache line detection works live.
	 */
	@Test
	public void testProbeCacheLine() {
		SimpleCacheProber simpleCacheProber = new SimpleCacheProber();
		simpleCacheProber.probeCacheLine();
		assertEquals(64, simpleCacheProber.getCacheLine());
	}

	/**
	 * Test method for {@link martin.michael.patrick.SimpleCacheProber#probeCacheLine()}
 	 * - runs the test many times to get a handle on reproducibility.
	 */
	@SuppressWarnings({ "nls", "boxing" })
	@Test
	public void testProbeCacheLineMany() {
		SimpleCacheProber simpleCacheProber = new SimpleCacheProber();
		for (int i =0 ; i <20 ; i++) {
			simpleCacheProber.probeCacheLine();
			assertEquals(String.format("iteration %d", i),
					     64,
					     simpleCacheProber.getCacheLine());			
		}
	}

	/** Returns real cache line test data recorded on Core i5 CPU.
	 * @return test data set
	 */
	@SuppressWarnings("boxing")
	private TreeMap<Integer, Double> getCacheLineTestData() {
		TreeMap<Integer, Double> dataset = new TreeMap<Integer, Double>();
			
		dataset.put(1, 0.168668);
		dataset.put(2, 0.115932);
		dataset.put(4, 0.115932);
		dataset.put(8, 0.115095);
		dataset.put(16, 0.116769);
		dataset.put(32, 0.200476);
		dataset.put(64, 1.774983);
		dataset.put(128, 2.420774);
		dataset.put(256, 2.385199);
		dataset.put(512, 2.228668);
		return dataset;
	}

	/** Returns real cache test data recorded on Core i5 data.
	 * @return test data set
	 */
	@SuppressWarnings("boxing")
	private TreeMap<Integer, Double> getCacheCorei5TestData() {
		TreeMap<Integer, Double> dataset = new TreeMap<Integer, Double>();
	
		dataset.put(512, 2.847967);
		dataset.put(1024, 2.812983);
		dataset.put(2048, 2.821621);
		dataset.put(4096, 2.771953);
		dataset.put(8192, 2.812984);
		dataset.put(16384, 2.812984);
		dataset.put(32768, 2.812984);
		dataset.put(65536, 5.359051);
		dataset.put(131072, 5.36812);
		dataset.put(262144, 5.41995);
		dataset.put(524288, 6.737685);
		dataset.put(1048576, 7.687439);
		dataset.put(2097152, 7.731493);
		dataset.put(4194304, 19.25423);
		dataset.put(8388608, 23.400928);
		dataset.put(16777216, 23.57196);
		dataset.put(33554432, 23.46269);
		dataset.put(67108864, 23.275677);
		dataset.put(134217728, 23.433321);

		return dataset;
	}
	
}