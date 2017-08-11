package com.emc.mongoose.tests.system.deprecated;

import com.emc.mongoose.api.common.SizeInBytes;
import com.emc.mongoose.api.common.env.PathUtil;
import com.emc.mongoose.api.model.io.IoType;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.deprecated.EnvConfiguredScenarioTestBase;
import com.emc.mongoose.tests.system.util.DirWithManyFilesDeleter;
import static com.emc.mongoose.api.common.Constants.KEY_TEST_STEP_ID;
import static com.emc.mongoose.api.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.run.scenario.Scenario.DIR_SCENARIO;

import com.emc.mongoose.ui.log.LogUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.Frequency;
import org.apache.logging.log4j.ThreadContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 Created by andrey on 06.02.17.
 * 2.2.1. Items Input File
 * 2.3.2. Items Output File
 * 5. Circularity
 * 6.2.2. Limit Load Job by Processed Item Count
 * 8.2.1. Create New Items
 * 8.4.3.4. Append
 * 9.3. Custom Scenario File
 * 9.4.1. Override Default Configuration in the Scenario
 * 9.5.5. Sequential Job
 * 10.1.2. Two Local Separate Storage Driver Services (at different ports)
 */
@Ignore public class CircularAppendTest
extends EnvConfiguredScenarioTestBase {

	private static final int EXPECTED_APPEND_COUNT = 100;
	private static final long EXPECTED_COUNT = 100;
	private static final String ITEM_OUTPUT_FILE_1 = "CircularAppendTest1.csv";

	private static String STD_OUTPUT;
	private static String ITEM_OUTPUT_PATH;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		EXCLUDE_PARAMS.clear();
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_CONCURRENCY, Arrays.asList(1000));
		EXCLUDE_PARAMS.put(
			KEY_ENV_ITEM_DATA_SIZE,
			Arrays.asList(new SizeInBytes(0), new SizeInBytes("100MB"), new SizeInBytes("10GB"))
		);
		STEP_ID = CircularAppendTest.class.getSimpleName();
		SCENARIO_PATH = Paths.get(getBaseDir(), DIR_SCENARIO, "systest", "CircularAppend.json");
		ThreadContext.put(KEY_TEST_STEP_ID, STEP_ID);
		CONFIG_ARGS.add("--storage-net-http-namespace=ns1");
		EnvConfiguredScenarioTestBase.setUpClass();
		if(SKIP_FLAG) {
			return;
		}
		if(STORAGE_DRIVER_TYPE.equals(STORAGE_TYPE_FS)) {
			ITEM_OUTPUT_PATH = Paths.get(
				Paths.get(PathUtil.getBaseDir()).getParent().toString(), STEP_ID
			).toString();
			CONFIG.getItemConfig().getOutputConfig().setPath(ITEM_OUTPUT_PATH);
		}
		SCENARIO = new JsonScenario(CONFIG, SCENARIO_PATH.toFile());
		STD_OUT_STREAM.startRecording();
		SCENARIO.run();
		STD_OUTPUT = STD_OUT_STREAM.stopRecordingAndGet();
		LogUtil.flushAll();
		TimeUnit.SECONDS.sleep(10);
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		if(! SKIP_FLAG) {
			if(STORAGE_DRIVER_TYPE.equals(STORAGE_TYPE_FS)) {
				try {
					DirWithManyFilesDeleter.deleteExternal(ITEM_OUTPUT_PATH);
				} catch(final Exception e) {
					e.printStackTrace(System.err);
				}
			}
		}
		EnvConfiguredScenarioTestBase.tearDownClass();
	}

	@Test
	public void testMetricsLogFile()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		try {
			final List<CSVRecord> metricsLogRecords = getMetricsLogRecords();
			assertTrue(
				"There should be more than 0 metrics records in the log file",
				metricsLogRecords.size() > 0
			);
			testMetricsLogRecords(
				metricsLogRecords, IoType.UPDATE, CONCURRENCY, STORAGE_DRIVERS_COUNT,
				ITEM_DATA_SIZE, EXPECTED_APPEND_COUNT * EXPECTED_COUNT, 0,
				CONFIG.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
			);
		} catch(final FileNotFoundException ignored) {
			// there may be no metrics file if append step duration is less than 10s
		}
	}

	@Test
	public void testTotalMetricsLogFile()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		final List<CSVRecord> totalMetrcisLogRecords = getMetricsTotalLogRecords();
		assertEquals(
			"There should be 1 total metrics records in the log file", 1,
			totalMetrcisLogRecords.size()
		);
		testTotalMetricsLogRecord(
			totalMetrcisLogRecords.get(0), IoType.UPDATE, CONCURRENCY, STORAGE_DRIVERS_COUNT,
			ITEM_DATA_SIZE, 0, 0
		);
	}

	@Test
	public void testMetricsStdout()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		testSingleMetricsStdout(
			STD_OUTPUT.replaceAll("[\r\n]+", " "),
			IoType.UPDATE, CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE,
			CONFIG.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);
	}

	@Test
	public void testIoTraceLogFile()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		assertTrue(
			"There should be more than " + EXPECTED_COUNT +
				" records in the I/O trace log file, but got: " + ioTraceRecords.size(),
			EXPECTED_COUNT < ioTraceRecords.size()
		);
		for(final CSVRecord ioTraceRecord : ioTraceRecords) {
			testIoTraceRecord(ioTraceRecord, IoType.UPDATE.ordinal(), ITEM_DATA_SIZE);
		}
	}

	@Test
	public void testUpdatedItemsOutputFile()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		final List<CSVRecord> items = new ArrayList<>();
		try(final BufferedReader br = new BufferedReader(new FileReader(ITEM_OUTPUT_FILE_1))) {
			final CSVParser csvParser = CSVFormat.RFC4180.parse(br);
			for(final CSVRecord csvRecord : csvParser) {
				items.add(csvRecord);
			}
		}
		final int itemIdRadix = CONFIG.getItemConfig().getNamingConfig().getRadix();
		final Frequency freq = new Frequency();
		String itemPath, itemId;
		long itemOffset;
		long itemSize;
		final SizeInBytes expectedFinalSize = new SizeInBytes(
			(EXPECTED_APPEND_COUNT + 1) * ITEM_DATA_SIZE.get() / 3,
			3 * (EXPECTED_APPEND_COUNT + 1) * ITEM_DATA_SIZE.get(),
			1
		);
		final int n = items.size();
		CSVRecord itemRec;
		for(int i = 0; i < n; i ++) {
			itemRec = items.get(i);
			itemPath = itemRec.get(0);
			for(int j = i; j < n; j ++) {
				if(i != j) {
					assertFalse(itemPath.equals(items.get(j).get(0)));
				}
			}
			itemId = itemPath.substring(itemPath.lastIndexOf('/') + 1);
			if(!STORAGE_DRIVER_TYPE.equals("atmos")) {
				itemOffset = Long.parseLong(itemRec.get(1), 0x10);
				assertEquals(Long.parseLong(itemId, itemIdRadix), itemOffset);
				freq.addValue(itemOffset);
			}
			itemSize = Long.parseLong(itemRec.get(2));
			assertTrue(
				"Expected size: " + expectedFinalSize.toString() + ", actual: " + itemSize,
				expectedFinalSize.getMin() <= itemSize && itemSize <= expectedFinalSize.getMax()
			);
			assertEquals("0/0", itemRec.get(3));
		}
		if(!STORAGE_DRIVER_TYPE.equals("atmos")) {
			assertEquals(EXPECTED_COUNT, freq.getUniqueCount());
		}
	}
}
