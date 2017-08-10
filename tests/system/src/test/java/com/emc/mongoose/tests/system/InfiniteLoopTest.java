package com.emc.mongoose.tests.system;

import com.emc.mongoose.api.common.SizeInBytes;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.EnvConfiguredScenarioTestBase;
import static com.emc.mongoose.api.common.Constants.KEY_TEST_STEP_ID;
import static com.emc.mongoose.api.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.run.scenario.Scenario.DIR_SCENARIO;

import com.emc.mongoose.ui.log.LogUtil;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.ThreadContext;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 Created by andrey on 08.06.17.
 */
public class InfiniteLoopTest
extends EnvConfiguredScenarioTestBase {

	private static final int SCENARIO_TIMEOUT = 50;
	private static final int EXPECTED_STEP_TIME = 5;
	private static final int EXPECTED_LOOP_COUNT = SCENARIO_TIMEOUT / EXPECTED_STEP_TIME;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		EXCLUDE_PARAMS.clear();
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_TYPE, Arrays.asList("atmos", "fs", "swift"));
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_COUNT, Arrays.asList(1));
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_CONCURRENCY, Arrays.asList(1, 10));
		EXCLUDE_PARAMS.put(
			KEY_ENV_ITEM_DATA_SIZE,
			Arrays.asList(
				new SizeInBytes(0), new SizeInBytes("1MB"), new SizeInBytes("100MB"),
				new SizeInBytes("10GB")
			)
		);
		STEP_ID = InfiniteLoopTest.class.getSimpleName();
		SCENARIO_PATH = Paths.get(
			getBaseDir(), DIR_SCENARIO, "systest", "InfiniteLoop.json"
		);
		ThreadContext.put(KEY_TEST_STEP_ID, STEP_ID);
		CONFIG_ARGS.add("--item-output-path=/default");
		CONFIG_ARGS.add("--test-step-limit-time=" + EXPECTED_STEP_TIME);
		EnvConfiguredScenarioTestBase.setUpClass();
		if(SKIP_FLAG) {
			return;
		}
		SCENARIO = new JsonScenario(CONFIG, SCENARIO_PATH.toFile());
		final Thread runner = new Thread(() -> SCENARIO.run());
		runner.start();
		TimeUnit.SECONDS.timedJoin(runner, SCENARIO_TIMEOUT);
		runner.interrupt();
		runner.join();
		TimeUnit.SECONDS.sleep(10);
		LogUtil.flushAll();
	}

	@Test
	public final void testTotalMetricsLogFile()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		final List<CSVRecord> totalRecs = getMetricsTotalLogRecords();
		assertEquals(
			"Expected steps count: " + EXPECTED_LOOP_COUNT + ", but was: " + totalRecs.size(),
			EXPECTED_LOOP_COUNT, totalRecs.size(), 1
		);
	}
}
