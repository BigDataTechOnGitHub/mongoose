package com.emc.mongoose.integ.feature.filesystem;

import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.log.appenders.RunIdFileManager;
import com.emc.mongoose.integ.base.DistributedLoadBuilderTestBase;
import com.emc.mongoose.integ.tools.StdOutUtil;
import com.emc.mongoose.integ.tools.BufferingOutputStream;
import com.emc.mongoose.integ.tools.LogValidator;
import com.emc.mongoose.integ.tools.TestConstants;
import com.emc.mongoose.run.scenario.runner.ScenarioRunner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Created by olga on 22.07.15.
 * HLUC: 1.4.4.1, 1.5.3.3, 1.5.4.9, 1.5.6.5
 */
public class DistributedFileRampupTest
extends DistributedLoadBuilderTestBase {
	//
	private static BufferingOutputStream STD_OUTPUT_STREAM;
	//
	private static String RUN_ID = FileRampupTest.class.getCanonicalName();
	private static final String
		LIMIT_TIME = "10s",
		RAMPUP_SIZES = "0,1,2,5",
		RAMPUP_CONN_COUNTS = "1,2,5,10",
		RAMPUP_LOAD_CHAIN = "create,read,delete";
	private static final int COUNT_STEPS = 3*3;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		System.setProperty(RunTimeConfig.KEY_RUN_ID, RUN_ID);
		DistributedLoadBuilderTestBase.setUpClass();
		//
		final AppConfig appConfig = BasicConfig.THREAD_CONTEXT.get();
		appConfig.set(RunTimeConfig.KEY_ITEM_CLASS, "file");
		appConfig.set(RunTimeConfig.KEY_ITEM_PREFIX, "/tmp/" + RUN_ID);
		appConfig.set(RunTimeConfig.KEY_LOAD_LIMIT_TIME, LIMIT_TIME);
		appConfig.set(RunTimeConfig.KEY_SCENARIO_NAME, TestConstants.SCENARIO_RAMPUP);
		appConfig.set(RunTimeConfig.KEY_SCENARIO_RAMPUP_SIZES, RAMPUP_SIZES);
		appConfig.set(RunTimeConfig.KEY_SCENARIO_RAMPUP_CONN_COUNTS, RAMPUP_CONN_COUNTS);
		appConfig.set(RunTimeConfig.KEY_SCENARIO_CHAIN_LOAD, RAMPUP_LOAD_CHAIN);
		appConfig.set(RunTimeConfig.KEY_API_S3_BUCKET, TestConstants.BUCKET_NAME);
		RunTimeConfig.setContext(appConfig);
		//
		final Logger logger = LogManager.getLogger();
		logger.info(Markers.MSG, BasicConfig.THREAD_CONTEXT.get().toString());
		//
		try (final BufferingOutputStream
			     stdOutStream =	StdOutUtil.getStdOutBufferingStream()
		) {
			//  Run mongoose default scenario in standalone mode
			new ScenarioRunner().run();
			//  Wait for "Scenario end" message
			TimeUnit.SECONDS.sleep(5);
			STD_OUTPUT_STREAM = stdOutStream;
		}
		//
		RunIdFileManager.flushAll();
	}

	@AfterClass
	public  static void tearDownClass()
	throws Exception {
		System.setProperty(RunTimeConfig.KEY_ITEM_CLASS, "data");
		System.setProperty(RunTimeConfig.KEY_ITEM_PREFIX, "");
		DistributedLoadBuilderTestBase.tearDownClass();
		final File tgtDir = Paths.get("/tmp/" + RUN_ID).toFile();
		for(final File f : tgtDir.listFiles()) {
			f.delete();
		}
		tgtDir.delete();
	}

	@Test
	public void shouldReportSummaryToConsole()
	throws Exception {
		Assert.assertTrue(
			"Should report information about end of scenario run from console",
			STD_OUTPUT_STREAM.toString().contains(TestConstants.SCENARIO_END_INDICATOR)
		);
	}

	@Test
	public void shouldCreateLogFiles()
	throws Exception {
		Path expectedFile = LogValidator.getMessageFile(RUN_ID).toPath();
		Assert.assertTrue("messages.log file must be contained", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfSumFile(RUN_ID).toPath();
		Assert.assertTrue("perf.sum.csv file must be contained", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfTraceFile(RUN_ID).toPath();
		Assert.assertTrue("perf.trace.csv file must be contained", Files.exists(expectedFile));

		expectedFile = LogValidator.getItemsListFile(RUN_ID).toPath();
		Assert.assertTrue("data.items.csv file must be contained", Files.exists(expectedFile));
	}

	@Test
	public void shouldCustomValuesDisplayedCorrectlyInConfigurationTable()
	throws Exception {
		final String[] runtimeConfCustomParam = BasicConfig.THREAD_CONTEXT.get().toString().split("\n");
		for (final String confParam : runtimeConfCustomParam) {
			if (confParam.contains(RunTimeConfig.KEY_LOAD_LIMIT_TIME)) {
				Assert.assertTrue(
					"Information about time limit must be correct in configuration table",
					confParam.contains(LIMIT_TIME)
				);
			}
			if (confParam.contains(RunTimeConfig.KEY_STORAGE_ADDRS)) {
				Assert.assertTrue(
					"Information about storage address must be correct in configuration table",
					confParam.contains("127.0.0.1")
				);
			}
			if (confParam.contains(RunTimeConfig.KEY_RUN_MODE)) {
				Assert.assertTrue(
					"Information about run mode must be correct in configuration table",
					confParam.contains(Constants.RUN_MODE_STANDALONE)
				);
			}
			if (confParam.contains(RunTimeConfig.KEY_RUN_ID)) {
				if (RUN_ID.length() >= 64) {
					Assert.assertTrue(confParam.contains(RUN_ID.substring(0, 63).trim()));
				} else {
					Assert.assertTrue(confParam.contains(RUN_ID));
				}
			}
			if (confParam.contains(RunTimeConfig.KEY_SCENARIO_NAME)) {
				Assert.assertTrue(
					"Information about scenario name must be correct in configuration table",
					confParam.contains(TestConstants.SCENARIO_RAMPUP)
				);
			}
		}
	}

	@Test
	public void shouldReportScenarioEndToMessageLogFile()
	throws Exception {
		//Read message file and search "Scenario End"
		final File messageFile = LogValidator.getMessageFile(RUN_ID);
		Assert.assertTrue("message.log file must be exist", messageFile.exists());
		//
		try (final BufferedReader
			     bufferedReader = new BufferedReader(new FileReader(messageFile))
		) {
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains(TestConstants.SCENARIO_END_INDICATOR)) {
					break;
				}
			}
			Assert.assertNotNull(
				"message.log file doesn't contain information about scenario end", line
			);
			//Check the message file contain report about scenario end. If not line = null.
			Assert.assertTrue(
				"message.log file doesn't contain information about scenario end",
				line.contains(TestConstants.SCENARIO_END_INDICATOR)
			);
		}
	}

	@Test
	public void shouldCreateCorrectPerfSumFile()
	throws Exception {
		// Get perf.sum.csv file
		final File perfSumFile = LogValidator.getPerfSumFile(RUN_ID);
		Assert.assertTrue("perf.sum.csv file must be exist", perfSumFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(perfSumFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectPerfSumCSV(in);
		}
	}

	@Test
	public void shouldCreateCorrectDataItemsFile()
	throws Exception {
		// Get data.items.csv file
		final File dataItemFile = LogValidator.getItemsListFile(RUN_ID);
		Assert.assertTrue("data.items.csv file must be exist", dataItemFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(dataItemFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectDataItemsCSV(in);
		}
	}

	@Test
	public void shouldCreateCorrectPerfTraceFile()
	throws Exception {
		// Get perf.trace.csv file
		final File perfTraceFile = LogValidator.getPerfTraceFile(RUN_ID);
		Assert.assertTrue("perf.trace.csv file doesn't exist",perfTraceFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(perfTraceFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectPerfTraceCSV(in);
		}
	}

	@Test
	public void shouldContainInformationAboutAllLoadsByStep()
	throws Exception {
		final File perfSumFile = LogValidator.getPerfSumFile(RUN_ID);
		Assert.assertTrue("perf.sum.csv file must be exist", perfSumFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(perfSumFile.toPath(), StandardCharsets.UTF_8)
		) {
			boolean firstRow = true;
			int loadJobCount = 0, stepsCount = 0;
			final String loadChain[] = RAMPUP_LOAD_CHAIN.split(",");
			final Iterable<CSVRecord> recIter = CSVFormat.RFC4180.parse(in);
			String loadTypeExpected, loadTypeActual;
			for(final CSVRecord nextRec : recIter) {
				if(firstRow) {
					firstRow = false;
				} else if(nextRec.size() == 25) {
					loadTypeExpected = loadChain[loadJobCount % loadChain.length];
					loadTypeActual = nextRec.get(3);
					Assert.assertTrue(
						"Load type is \"" + loadTypeActual + "\" but expected " + loadTypeExpected,
						loadTypeActual.equalsIgnoreCase(loadTypeExpected)
					);
					loadJobCount ++;
				} else {
					stepsCount ++;
				}
			}
			Assert.assertEquals(
				"Steps counts must be equal to " + COUNT_STEPS, COUNT_STEPS, stepsCount
			);
		}
	}
}
