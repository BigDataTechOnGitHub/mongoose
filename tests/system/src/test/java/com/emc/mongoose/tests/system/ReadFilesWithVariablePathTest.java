package com.emc.mongoose.tests.system;

import com.emc.mongoose.api.common.env.PathUtil;
import com.emc.mongoose.api.model.io.IoType;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.ScenarioTestBase;
import com.emc.mongoose.tests.system.base.params.Concurrency;
import com.emc.mongoose.tests.system.base.params.DriverCount;
import com.emc.mongoose.tests.system.base.params.ItemSize;
import com.emc.mongoose.tests.system.base.params.StorageType;
import com.emc.mongoose.tests.system.util.DirWithManyFilesDeleter;
import com.emc.mongoose.tests.system.util.EnvUtil;
import com.emc.mongoose.ui.log.LogUtil;
import static com.emc.mongoose.api.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.run.scenario.Scenario.DIR_SCENARIO;

import org.apache.commons.csv.CSVRecord;

import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 Created by andrey on 13.06.17.
 */
public class ReadFilesWithVariablePathTest
extends ScenarioTestBase {

	private static final int EXPECTED_COUNT = 10000;

	private String fileOutputPath;

	protected ReadFilesWithVariablePathTest(
		final StorageType storageType, final DriverCount driverCount, final Concurrency concurrency,
		final ItemSize itemSize
	) throws Exception {
		super(storageType, driverCount, concurrency, itemSize);
	}

	@Before
	public void setUp()
	throws Exception {
		configArgs.add("--item-naming-radix=16");
		configArgs.add("--item-naming-length=16");
		super.setUp();
		fileOutputPath = Paths
			.get(Paths.get(PathUtil.getBaseDir()).getParent().toString(), stepId)
			.toString();
		EnvUtil.set("FILE_OUTPUT_PATH", fileOutputPath);
		scenario = new JsonScenario(config, scenarioPath.toFile());
	}

	@After
	public void tearDown()
	throws Exception {
		try {
			DirWithManyFilesDeleter.deleteExternal(fileOutputPath);
		} catch(final Exception e) {
			e.printStackTrace(System.err);
		}
		super.tearDown();
	}

	@Override
	protected Path makeScenarioPath() {
		return Paths.get(getBaseDir(), DIR_SCENARIO, "systest", "ReadFilesWithVariablePath.json");
	}

	@Override
	protected String makeStepId() {
		return ReadFilesWithVariablePathTest.class.getSimpleName();
	}

	@Override
	public void test()
	throws Exception {

		stdOutStream.startRecording();
		scenario.run();
		LogUtil.flushAll();
		final String stdOutput = stdOutStream.stopRecordingAndGet();
		TimeUnit.SECONDS.sleep(5);

		testMetricsLogRecords(
			getMetricsLogRecords(),
			IoType.READ, concurrency.getValue(), driverCount.getValue(), itemSize.getValue(),
			EXPECTED_COUNT, 0, config.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);

		testTotalMetricsLogRecord(
			getMetricsTotalLogRecords().get(0),
			IoType.READ, concurrency.getValue(), driverCount.getValue(), itemSize.getValue(),
			EXPECTED_COUNT, 0
		);

		testSingleMetricsStdout(
			stdOutput.replaceAll("[\r\n]+", " "),
			IoType.READ, concurrency.getValue(), driverCount.getValue(), itemSize.getValue(),
			config.getOutputConfig().getMetricsConfig().getAverageConfig().getPeriod()
		);

		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		assertEquals(EXPECTED_COUNT, ioTraceRecords.size());

		// Item path should look like:
		// ${FILE_OUTPUT_PATH}/1/b/0123456789abcdef
		// ${FILE_OUTPUT_PATH}/b/fedcba9876543210
		final Pattern subPathPtrn = Pattern.compile("(/[0-9a-f]){1,2}/[0-9a-f]{16}");

		String nextFilePath;
		Matcher m;
		final int baseOutputPathLen = fileOutputPath.length();
		for(final CSVRecord ioTraceRecord : ioTraceRecords) {
			testIoTraceRecord(ioTraceRecord, IoType.READ.ordinal(), itemSize.getValue());
			nextFilePath = ioTraceRecord.get("ItemPath");
			assertTrue(nextFilePath.startsWith(fileOutputPath));
			nextFilePath = nextFilePath.substring(baseOutputPathLen);
			m = subPathPtrn.matcher(nextFilePath);
			assertTrue(m.matches());
		}
	}
}
