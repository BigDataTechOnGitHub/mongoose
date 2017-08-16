package com.emc.mongoose.tests.system.deprecated;

import com.emc.mongoose.api.common.env.PathUtil;
import com.emc.mongoose.api.model.io.IoType;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.deprecated.EnvConfiguredScenarioTestBase;
import com.emc.mongoose.tests.system.util.DirWithManyFilesDeleter;
import com.emc.mongoose.tests.system.util.OpenFilesCounter;
import com.emc.mongoose.tests.system.util.PortTools;
import com.emc.mongoose.ui.log.LogUtil;
import static com.emc.mongoose.api.common.Constants.KEY_TEST_STEP_ID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 Created by andrey on 04.06.17.
 */
@Ignore public class CreateNoLimitTest
extends EnvConfiguredScenarioTestBase {

	private static Thread RUNNER;
	private static String ITEM_OUTPUT_PATH;
	private static String STD_OUTPUT;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		EXCLUDE_PARAMS.clear();
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_TYPE, Arrays.asList("s3", "swift"));
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_CONCURRENCY, Arrays.asList(100, 1000));
		STEP_ID = CreateNoLimitTest.class.getSimpleName();
		ThreadContext.put(KEY_TEST_STEP_ID, STEP_ID);
		EnvConfiguredScenarioTestBase.setUpClass();
		if(SKIP_FLAG) {
			return;
		}
		
		switch(STORAGE_DRIVER_TYPE) {
			case STORAGE_TYPE_FS:
				ITEM_OUTPUT_PATH = Paths.get(
					Paths.get(PathUtil.getBaseDir()).getParent().toString(), STEP_ID
				).toString();
				CONFIG.getItemConfig().getOutputConfig().setPath(ITEM_OUTPUT_PATH);
				break;
			case STORAGE_TYPE_SWIFT:
				CONFIG.getStorageConfig().getNetConfig().getHttpConfig().setNamespace("ns1");
				break;
		}
		SCENARIO = new JsonScenario(CONFIG, DEFAULT_SCENARIO_PATH.toFile());

		RUNNER = new Thread(
			() -> {
				try {
					SCENARIO.run();
				} catch(final Throwable t) {
					LogUtil.exception(Level.ERROR, t, "Failed to run the scenario");
				}
			}
		);
		STD_OUT_STREAM.startRecording();
		RUNNER.start();
		TimeUnit.SECONDS.sleep(25);
		STD_OUTPUT = STD_OUT_STREAM.stopRecordingAndGet();
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		if(!SKIP_FLAG) {
			if(RUNNER != null) {
				RUNNER.interrupt();
			}
			if(STORAGE_TYPE_FS.equals(STORAGE_DRIVER_TYPE)) {
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
	public final void testActualConcurrencyCount()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		final int expectedConcurrency = STORAGE_DRIVERS_COUNT * CONCURRENCY;
		if(STORAGE_TYPE_FS.equals(STORAGE_DRIVER_TYPE)) {
			final int actualConcurrency = OpenFilesCounter.getOpenFilesCount(ITEM_OUTPUT_PATH);
			assertTrue(
				"Expected concurrency <= " + actualConcurrency + ", actual: " + actualConcurrency,
				actualConcurrency <= expectedConcurrency
			);
		} else {
			int actualConcurrency = 0;
			final int startPort = CONFIG.getStorageConfig().getNetConfig().getNodeConfig().getPort();
			for(int j = 0; j < HTTP_STORAGE_NODE_COUNT; j ++) {
				actualConcurrency += PortTools
					.getConnectionCount("127.0.0.1:" + (startPort + j));
			}
			assertEquals(
				"Expected concurrency: " + actualConcurrency + ", actual: " + actualConcurrency,
				expectedConcurrency, actualConcurrency, expectedConcurrency / 100
			);
		}
	}

	@Test
	public final void testStdOutput()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		testMetricsTableStdout(
			STD_OUTPUT, STEP_ID, STORAGE_DRIVERS_COUNT, 0,
			new HashMap<IoType, Integer>() {{ put(IoType.CREATE, CONCURRENCY); }}
		);
	}
}
