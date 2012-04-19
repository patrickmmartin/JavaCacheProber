package martin.michael.patrick;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

/**
 * Simple class to deduce the CPU cache sizes, and cache lines through probing
 * the execution times of some data operations.
 * 
 * @author Patrick Martin
 * 
 */
public class SimpleCacheProber {


	/**
	 * Runs the cache detection process and outputs the detected estimates.
	 * 
	 * @param args
	 *            standard arguments for a Main class - unused
	 */
	@SuppressWarnings({ "nls", "boxing" })
	public static void main(String[] args) {

		SimpleCacheProber simpleCacheProber = new SimpleCacheProber();
		// get an estimate of the cache line size
		System.out.println("Starting probe of cache line");
		simpleCacheProber.probeCacheLine();

		// cache line size detection is generally quite reliable, but for the
		// purposes of exposition,
		// let's impose a sanity check
		int cacheLineSize = simpleCacheProber._cacheLine;

		if (cacheLineSize < 0) {
			System.err.println("failed to detect cache line size: assuming value of 64");
			// default it to a well-known value
			cacheLineSize = 64;
		}

		System.out.println(String.format("cache line size %d", cacheLineSize));
		
		System.out.println("Starting probe of level cache sizes");		
		simpleCacheProber.probeCaches(cacheLineSize);
		System.out.println(String.format("L1 cache %dK, L2 cache %dK",
											simpleCacheProber.getCacheL1() >> 10,
											simpleCacheProber.getCacheL2() >> 10));

	}

	
	/**
	 * Threshold of L1 cache detection
	 */
	static final double CACHE_L1_THRESHOLD = 0.5;
	/**
	 * threshold for L2 cache detection
	 */
	static final int CACHE_L2_THRESHOLD = 1;
	/**
	 * Number of loops for probing the cache line - needs to be sufficient to have acceptable probability
	 * of ensuring one sample captures lower bound.
	 */
	private static final int CACHE_LINE_LOOPS = 20;
	/**
	 * Array size for probing cache line.
	 */
	private static final int CACHE_LINE_ARRAY_SIZE = 16 * 1024 * 1024;
	/**
	 * Cache line step count.
	 */
	static final int CACHE_LINE_STEPS = 64 * 1024;
	/**
	 * Threshold for the jump in execution time 2nd differential when the cache line
	 * size is exceeded
	 */
	private static final double CACHE_LINE_THRESHOLD = 4;

	/**
	 * Number of loops for probing the level caches - needs to be sufficient to have acceptable probability
	 * of ensuring one sample captures lower bound.
	 */
	private static final int LEVEL_CACHE_LOOPS = 10;
	/**
	 * Cache array step count.
	 */
	static final int LEVEL_CACHE_STEPS = 1024 * 1024;

	/**
	 * detected cache line
	 */
	private int _cacheLine = -1;
	/**
	 * detected L1 cache size
	 */
	private int _cacheL1 = -1;
	/**
	 * detected L2 cache size
	 */
	private int _cacheL2 = -1;

	/**
	 * getter for cacheLine property
	 * 
	 * @return the detected cacheLine size in bytes
	 */
	public int getCacheLine() {
		return this._cacheLine;
	}

	/**
	 * getter for cacheL1 property
	 * 
	 * @return the detected cacheL1 size in bytes
	 */
	public int getCacheL1() {
		return this._cacheL1;
	}

	/**
	 * getter for cacheL2 property
	 * 
	 * @return the detected cacheL2 size in bytes
	 */
	public int getCacheL2() {
		return this._cacheL2;
	}

	// This class is not going to be persisted
	@SuppressWarnings("serial")
	class SampleList extends ArrayList<Long> {

		/**
		 * Performs the necessary tasks to ensure the getMin and getMax methods
		 * return the correct results.
		 */
		private void prepare() {
			Collections.sort(this);
		}

		/**
		 * Returns the minimum of the collection.
		 */
		@SuppressWarnings("boxing")
		long getMin() {
			prepare();
			return get(0);
		}

		/**
		 * Returns the maximum of the collection.
		 */
		@SuppressWarnings("boxing")
		long getMax() {
			prepare();
			return get(this.size() - 1);
		}

	}
	
	class ReportWriter {

		/**
		 * wrapped file instance
		 */
		FileWriter _reportFile = null;
		
		/**
		 * Constructor that initialises the instance and opens or not the passed filename
		 * @param reportFileName
		 */
		@SuppressWarnings("nls")
		ReportWriter (String reportFileName) {
			try {
				this._reportFile  = new FileWriter(reportFileName);
			} catch (IOException e) {
				System.err.println(String.format("WARN: output file [%s] not updated.", reportFileName));
			}
		}
		
		/**
		 * Writes a line to the file.
		 * @param reportLine
		 */
		@SuppressWarnings("nls")
		void writeReportLine( String reportLine) {
			if (null != this._reportFile) {
				try {
					this._reportFile.write(reportLine + "\n");
				} catch (IOException e) {
					// swallow this exception
				}
			}
		}

		/**
		 * Closes and flushes the file.
		 */
		void closeReport(){
			// close and flush
			if (null != this._reportFile)
				try {
					this._reportFile.close();
				} catch (IOException e) {
					// swallow exception
				}
		}
		
		
	}

	/**
	 * Modifies items in a byte array, moving by stride, wrapping at array end.
	 * 
	 * @param bytes
	 *            byte array to be modified
	 * @param stride
	 *            stride value
	 * @return execution time in nanoseconds of the loop.
	 */
	private long testByteArray(final byte[] bytes, final int stride) {

		final int lengthMod = bytes.length - 1;
		final long startTime = System.nanoTime();

		for (int i = 0; i < CACHE_LINE_STEPS; i++) {
			bytes[(i * stride) & lengthMod]++; // (x & lengthMod) avoids using %
		}
		return System.nanoTime() - startTime;

	}

	/**
	 * Sample the execution times for the loop over an adequate period of
	 * execution. We only need one observation to return a representative
	 * reading, as we're determining the lower bound.
	 */
	@SuppressWarnings({ "nls", "boxing" })
	public void probeCacheLine() {
		/**
		 * Chunk of sufficient size for probing cache line.
		 */
		byte[] arr = new byte[CACHE_LINE_ARRAY_SIZE];

		/**
		 * Map of line loop execution times versus the probed array size.
		 */
		TreeMap<Integer, Double> lineTimes = new TreeMap<Integer, Double>();

		ReportWriter reportWriter = new ReportWriter("line.csv");

		reportWriter.writeReportLine("Power,Size,Time Min,Duration Min,Duration Max");

		// it's going to be in the region of 64
		for (int pow = 2; pow < 18; pow++) {
			int lineStride = 1 << pow;
			SampleList loopTimes = new SampleList();
			for (int i = 0; i < CACHE_LINE_LOOPS; i++) {
				loopTimes.add(testByteArray(arr, lineStride));
			}
			// find the minimum of the loop times
			lineTimes.put(lineStride, (double) loopTimes.getMin()
					/ CACHE_LINE_STEPS);
			reportWriter.writeReportLine(String.format("%d, %d, %d, %f, %f", pow,
														lineStride, loopTimes.getMin(),
														(float) loopTimes.getMin() / CACHE_LINE_STEPS,
														(float) loopTimes.getMax() / CACHE_LINE_STEPS));

		}

		reportWriter.closeReport();
		
		this._cacheLine = findStep(lineTimes, 1, CACHE_LINE_THRESHOLD, DetectionStyle.SoftEdge);

	}


	/**
	 * Modifies items in an integer array, moving by a stride, wrapping at array
	 * end.
	 * 
	 * @param ints
	 *            integer array to be modified
	 * @param stride
	 *            stride value
	 * @return execution time in nanoseconds of the loop.
	 */

	private long testIntArray(final int[] ints, final int length,
			final int stride) {

		final int lengthMod = length - 1;

		final long startTime = System.nanoTime();

		for (int i = 0; i < LEVEL_CACHE_STEPS; i++) {
			ints[(i * stride) & lengthMod]++; // (x & lengthMod) is equal to (x
												// %
												// arr.Length)
		}
		return System.nanoTime() - startTime;

	}

	/**
	 * Probes the caches for their sizes by examining the execution times versus
	 * size of the data probed. We only need one instance to return a
	 * representative reading, as we're determining the lower bound.
	 * 
	 * @param cacheLineSize
	 */

	@SuppressWarnings({ "nls", "boxing" })
	void probeCaches(int cacheLineSize) {

		// 64 MB array
		// if running really low on memory, simply adjust maxShift
		final int maxShift = 26;
		int[] arr = new int[1 << maxShift];

		// Attempt to open output file
		ReportWriter reportWriter = new ReportWriter("caches.csv");

		reportWriter.writeReportLine( "Power,Duration Min (ns),Duration Max (ns),Size (bytes)");

		// sample the times over an adequate time to get at least one accurate
		// result, as we are determining the lower bound

		// Map of line loop execution times versus the probed array size.

		TreeMap<Integer, Double> lineTimes = new TreeMap<Integer, Double>();

		for (int shift = 7; shift < maxShift; shift += 1) {
			int arraySize = 1 << shift;

			SampleList loopTimes = new SampleList();
			for (int i = 0; i < LEVEL_CACHE_LOOPS; i++) {
				loopTimes.add(testIntArray(arr, arraySize, cacheLineSize));
			}

			lineTimes.put(arraySize, (double) loopTimes.getMin()
					/ LEVEL_CACHE_STEPS);

				reportWriter.writeReportLine(String.format("%d, %f, %f, %d",
															shift,
															(float) loopTimes.getMin() / LEVEL_CACHE_STEPS,
															(float) loopTimes.getMax() / LEVEL_CACHE_STEPS,
															4 * arraySize));
		}

		reportWriter.closeReport();
		
		// start finding the L1 cache from a reasonable start point
		this._cacheL1 = findStep(lineTimes, 1024, CACHE_L1_THRESHOLD);

		// and pick up for the cache at the next point on, which avoids the
		// noisy behaviour around the transition
		this._cacheL2 = findStep(lineTimes, this._cacheL1 * 2, CACHE_L2_THRESHOLD);

	}

	/**
	 *  Enumeration for the detection style of the jump in the data.
	 *  Essentially a "fudge factor" to distinguish between step-like and soft changes,
	 *  where the domain value at the peak in 2nd differential is due to terms from a transient component
	 *  and a comparable monotonic component, resulting in a shift of the observed local maximum.
	 *  
	 */
	enum DetectionStyle {
		SoftEdge, StepLike
	}

	/**
	 * Finds a step in the passed data and returns the point in the domain at which it was detected. 
	 * Note: currently reliant upon concrete class TreeMap to achieve sorted keys.  
	 * 
	 * @param lineTimes
	 *            Map of times versus parameter, a map because
	 * @param start
	 *            start key for search in lineTimes
	 * @param threshold
	 *            threshold for values in the range must exceed to be judged a
	 *            hit
	 * @param detectStyle
	 * 	           detection style to be applied to the data stream
	 * @return the point at which a step size is found, through identifying a
	 *         local maximum in the 2nd differential
	 */
	@SuppressWarnings("boxing")
	public int findStep(TreeMap<Integer, Double> lineTimes, int start, double threshold, DetectionStyle detectStyle) {

		// this Set will be sorted (as an implementation detail of the TreeMap
		// passed in)
		Set<Integer> params = lineTimes.keySet();
		//
		Iterator<Integer> paramIt = params.iterator();
		int param;

		// detection using signal in 2nd derivative
		double yb = 0.0, yc = 0.0, yf = 0.0;
		double y2c = 0.0, y2b;
		int paramb = 0;

		// find the first edge past the start index
		while (paramIt.hasNext()) {

			param = paramIt.next();
			// push the other values back
			yb = yc;
			yc = yf;
			y2b = y2c;
			// pop the next value
			yf = lineTimes.get(param);
			// calculate 2nd differential
			// strictly, the discrete second differential formula is (Yk+1 - 2Yk + Yk-1) / h^2
			// but we'll normalise the domain steps to 1
			if (0.0 != yb) {
				y2c = yf - (2 * yc) + yb;
			}

			// only apply the test past certain points
			if ((paramb > start) && (y2b > threshold) && (y2c < y2b)) {
				switch (detectStyle){
				// essentially to cater for the soft curve in the cache line data
				// return the prior domain value 
				case SoftEdge:
					return paramb;
				default:
					return param;
				}
			}
			paramb = param;
		}
		return -1;
	}

	/**
	 * Finds a step in the passed data and returns the point in the domain at which it was detected.
	 * The {@link DetectionStyle} is defaulted to StepLike.
	 * Best suited for L1/L2 cache detection.  
	 * 
	 * @param lineTimes
	 *            Map of times versus parameter, a map because
	 * @param start
	 *            start key for search in lineTimes
	 * @param threshold
	 *            threshold for values in the range must exceed to be judged a
	 *            hit
	 * @return the point at which a step size is found, through identifying a
	 *         local maximum in the 2nd differential. 
	 *         
	 */
	public int findStep(TreeMap<Integer, Double> lineTimes, int start, double threshold) {
		return findStep(lineTimes, start, threshold, DetectionStyle.StepLike);
	}

}
