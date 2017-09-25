package com.emc.mongoose.tests.system.json;

import com.github.akurilov.commons.system.SizeInBytes;
import com.emc.mongoose.api.common.env.PathUtil;
import com.emc.mongoose.api.model.io.IoType;
import com.emc.mongoose.scenario.json.JsonScenario;
import com.emc.mongoose.tests.system.base.OldScenarioTestBase;
import com.emc.mongoose.tests.system.base.params.Concurrency;
import com.emc.mongoose.tests.system.base.params.DriverCount;
import com.emc.mongoose.tests.system.base.params.ItemSize;
import com.emc.mongoose.tests.system.base.params.StorageType;
import com.emc.mongoose.tests.system.util.DirWithManyFilesDeleter;
import com.emc.mongoose.ui.log.LogUtil;
import static com.emc.mongoose.api.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.api.common.Constants.DIR_EXAMPLE_SCENARIO;

import org.apache.commons.csv.CSVRecord;

import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 Created by kurila on 15.06.17.
 */
public class SingleFixedUpdateAndSingleRandomReadTest
extends OldScenarioTestBase {
	
	private String itemOutputPath;
	private String stdOutput;
	private SizeInBytes expectedUpdateSize;
	private SizeInBytes expectedReadSize;
	
	private static final long EXPECTED_COUNT = 1000;

	public SingleFixedUpdateAndSingleRandomReadTest(
		final StorageType storageType, final DriverCount driverCount, final Concurrency concurrency,
		final ItemSize itemSize
	) throws Exception {
		super(storageType, driverCount, concurrency, itemSize);
	}

	@Override
	protected Path makeScenarioPath() {
		return Paths.get(
			getBaseDir(), DIR_EXAMPLE_SCENARIO, "json", "systest", "SingleFixedUpdateAndSingleRandomRead.json"
		);
	}

	@Override
	protected String makeStepId() {
		return SingleFixedUpdateAndSingleRandomReadTest.class.getSimpleName() + '-' +
			storageType.name() + '-' + driverCount.name() + 'x' + concurrency.name() + '-' +
			itemSize.name();
	}

	@Before
	public void setUp()
	throws Exception {
		/**
		 https://github.com/emc-mongoose/nagaina/issues/3
		 */
		super.setUp();
		expectedUpdateSize = new SizeInBytes(
			SizeInBytes.toFixedSize("5KB") - SizeInBytes.toFixedSize("2KB")
		);
		expectedReadSize = new SizeInBytes(1, itemSize.getValue().get(), 1);
		if(StorageType.FS.equals(storageType)) {
			itemOutputPath = Paths
				.get(Paths.get(PathUtil.getBaseDir()).getParent().toString(), stepId)
				.toString();
			config.getItemConfig().getOutputConfig().setPath(itemOutputPath);
		}
		scenario = new JsonScenario(config, scenarioPath.toFile());
		stdOutStream.startRecording();
		scenario.run();
		LogUtil.flushAll();
		stdOutput = stdOutStream.stopRecordingAndGet();
	}
	
	@After
	public void tearDown()
	throws Exception {
		if(StorageType.FS.equals(storageType)) {
			try {
				DirWithManyFilesDeleter.deleteExternal(itemOutputPath);
			} catch(final Exception e) {
				e.printStackTrace(System.err);
			}
		}
		super.tearDown();
	}
	
	@Override
	public void test()
	throws Exception {

		final LongAdder ioTraceRecCount = new LongAdder();
		final Consumer<CSVRecord> ioTraceRecFunc = ioTraceRec -> {
			if(ioTraceRecCount.sum() < EXPECTED_COUNT) {
				testIoTraceRecord(ioTraceRec, IoType.UPDATE.ordinal(),
					expectedUpdateSize
				);
			} else {
				testIoTraceRecord(ioTraceRec, IoType.READ.ordinal(), expectedReadSize);
			}
			ioTraceRecCount.increment();
		};
		testIoTraceLogRecords(ioTraceRecFunc);
		assertEquals(
			"There should be " + 2 * EXPECTED_COUNT + " records in the I/O trace log file",
			2 * EXPECTED_COUNT, ioTraceRecCount.sum()
		);

		final List<CSVRecord> totalMetrcisLogRecords = getMetricsTotalLogRecords();
		testTotalMetricsLogRecord(
			totalMetrcisLogRecords.get(0), IoType.UPDATE, concurrency.getValue(),
			driverCount.getValue(), expectedUpdateSize, EXPECTED_COUNT, 0
		);
		testTotalMetricsLogRecord(
			totalMetrcisLogRecords.get(1), IoType.READ, concurrency.getValue(),
			driverCount.getValue(), expectedReadSize, EXPECTED_COUNT, 0
		);

		final List<CSVRecord> metricsLogRecords = getMetricsLogRecords();
		final List<CSVRecord> updateMetricsRecords = new ArrayList<>();
		final List<CSVRecord> readMetricsRecords = new ArrayList<>();
		for(final CSVRecord metricsLogRec : metricsLogRecords) {
			if(IoType.UPDATE.name().equalsIgnoreCase(metricsLogRec.get("TypeLoad"))) {
				updateMetricsRecords.add(metricsLogRec);
			} else {
				readMetricsRecords.add(metricsLogRec);
			}
		}
		testMetricsLogRecords(
			updateMetricsRecords, IoType.UPDATE, concurrency.getValue(), driverCount.getValue(),
			expectedUpdateSize, EXPECTED_COUNT, 0,
			config.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);
		testMetricsLogRecords(
			readMetricsRecords, IoType.READ, concurrency.getValue(), driverCount.getValue(),
			expectedReadSize, EXPECTED_COUNT, 0,
			config.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);

		final String stdOutput = this.stdOutput.replaceAll("[\r\n]+", " ");
		testSingleMetricsStdout(
			stdOutput, IoType.UPDATE, concurrency.getValue(), driverCount.getValue(),
			expectedUpdateSize,
			config.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);
		testSingleMetricsStdout(
			stdOutput, IoType.READ, concurrency.getValue(), driverCount.getValue(),
			expectedReadSize,
			config.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);
	}
}
