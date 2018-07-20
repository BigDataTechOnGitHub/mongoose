package com.emc.mongoose.load.step.pipeline;

import com.emc.mongoose.config.TimeUtil;
import com.emc.mongoose.env.Extension;
import com.emc.mongoose.exception.OmgShootMyFootException;
import com.emc.mongoose.data.DataInput;
import com.emc.mongoose.item.op.OpType;
import com.emc.mongoose.item.op.Operation;
import com.emc.mongoose.item.io.DelayedTransferConvertBuffer;
import com.emc.mongoose.item.Item;
import com.emc.mongoose.item.ItemFactory;
import com.emc.mongoose.item.io.ItemInfoFileOutput;
import com.emc.mongoose.item.ItemType;
import com.emc.mongoose.item.TransferConvertBuffer;
import com.emc.mongoose.load.step.local.context.LoadStepContext;
import com.emc.mongoose.load.generator.LoadGenerator;
import com.emc.mongoose.load.step.local.LoadStepLocalBase;
import com.emc.mongoose.storage.driver.StorageDriver;
import com.emc.mongoose.load.step.local.context.LoadStepContextImpl;
import com.emc.mongoose.load.generator.LoadGeneratorBuilderImpl;
import com.emc.mongoose.load.generator.LoadGeneratorBuilder;
import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;

import com.github.akurilov.commons.io.Output;
import com.github.akurilov.commons.reflection.TypeUtil;
import com.github.akurilov.commons.system.SizeInBytes;
import com.github.akurilov.commons.concurrent.throttle.RateThrottle;
import static com.github.akurilov.commons.collection.TreeUtil.reduceForest;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import com.github.akurilov.confuse.impl.BasicConfig;
import static com.github.akurilov.confuse.Config.deepToMap;

import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public class PipelineLoadStepLocal
extends LoadStepLocalBase {

	public PipelineLoadStepLocal(
		final Config baseConfig, final List<Extension> extensions, final List<Config> contextConfigs
	) {
		super(baseConfig, extensions, contextConfigs);
	}

	@Override
	protected void init() {

		final String autoStepId = "pipeline_" + LogUtil.getDateTimeStamp();
		final Config stepConfig = config.configVal("load-step");
		if(stepConfig.boolVal("idAutoGenerated")) {
			stepConfig.val("id", autoStepId);
		}
		final int subStepCount = ctxConfigs.size();
		TransferConvertBuffer<? extends Item, ? extends Operation<? extends Item>> nextItemBuff = null;

		for(int originIndex = 0; originIndex < subStepCount; originIndex ++) {

			final Map<String, Object> mergedConfigTree = reduceForest(
				Arrays.asList(deepToMap(config), deepToMap(ctxConfigs.get(originIndex)))
			);
			final Config subConfig;
			try {
				subConfig = new BasicConfig(config.pathSep(), config.schema(), mergedConfigTree);
			} catch(final InvalidValueTypeException | InvalidValuePathException e) {
				LogUtil.exception(Level.FATAL, e, "Scenario syntax error");
				throw new CancellationException();
			}
			final Config loadConfig = subConfig.configVal("load");
			final Config opConfig = loadConfig.configVal("op");
			final OpType opType = OpType.valueOf(opConfig.stringVal("type").toUpperCase());
			final Config storageConfig = subConfig.configVal("storage");
			final int concurrency = storageConfig.intVal("driver-limit-concurrency");
			final Config outputConfig = subConfig.configVal("output");
			final Config metricsConfig = outputConfig.configVal("metrics");
			final SizeInBytes itemDataSize;
			final Object itemDataSizeRaw = subConfig.val("item-data-size");
			if(itemDataSizeRaw instanceof String) {
				itemDataSize = new SizeInBytes((String) itemDataSizeRaw);
			} else {
				itemDataSize = new SizeInBytes(TypeUtil.typeConvert(itemDataSizeRaw, long.class));
			}
			final boolean outputColorFlag = outputConfig.boolVal("color");
			initMetrics(originIndex, opType, concurrency, metricsConfig, itemDataSize, outputColorFlag);

			final Config itemConfig = subConfig.configVal("item");
			final Config dataConfig = itemConfig.configVal("data");
			final Config dataInputConfig = dataConfig.configVal("input");
			final Config limitConfig = stepConfig.configVal("limit");
			final Config dataLayerConfig = dataInputConfig.configVal("layer");

			final String testStepId = stepConfig.stringVal("id");

			try {

				final Object dataLayerSizeRaw = dataLayerConfig.val("size");
				final SizeInBytes dataLayerSize;
				if(dataLayerSizeRaw instanceof String) {
					dataLayerSize = new SizeInBytes((String) dataLayerSizeRaw);
				} else {
					dataLayerSize = new SizeInBytes(TypeUtil.typeConvert(dataLayerSizeRaw, int.class));
				}

				final DataInput dataInput = DataInput.instance(
					dataInputConfig.stringVal("file"), dataInputConfig.stringVal("seed"), dataLayerSize,
					dataLayerConfig.intVal("cache")
				);

				final int batchSize = loadConfig.intVal("batch-size");

				try {

					final StorageDriver driver = StorageDriver.instance(
						extensions, storageConfig, dataInput, dataConfig.boolVal("verify"), batchSize, testStepId
					);

					final ItemType itemType = ItemType.valueOf(itemConfig.stringVal("type").toUpperCase());
					final ItemFactory<? extends Item> itemFactory = ItemType.getItemFactory(itemType);
					final double rateLimit = opConfig.doubleVal("limit-rate");

					try {
						final LoadGeneratorBuilder generatorBuilder = new LoadGeneratorBuilderImpl<>()
							.itemConfig(itemConfig)
							.loadConfig(loadConfig)
							.limitConfig(limitConfig)
							.itemType(itemType)
							.itemFactory((ItemFactory) itemFactory)
							.storageDriver(driver)
							.authConfig(storageConfig.configVal("auth"))
							.originIndex(originIndex);
						if(rateLimit > 0) {
							generatorBuilder.addThrottle(new RateThrottle(rateLimit));
						}
						if(nextItemBuff != null) {
							generatorBuilder.itemInput(nextItemBuff);
						}
						final LoadGenerator generator = generatorBuilder.build();

						final LoadStepContext stepCtx = new LoadStepContextImpl<>(
							testStepId, generator, driver, metricsContexts.get(originIndex), limitConfig,
							outputConfig.boolVal("metrics-trace-persist"), batchSize, opConfig.intVal("limit-recycle"),
							opConfig.boolVal("recycle"), opConfig.boolVal("retry")
						);
						stepContexts.add(stepCtx);

						if(originIndex < subStepCount - 1) {
							final long itemOutputDelay;
							final Object itemOutputDelayRaw = itemConfig.val("output-delay");
							if(itemOutputDelayRaw instanceof String) {
								itemOutputDelay = TimeUtil.getTimeInSeconds((String) itemOutputDelayRaw);
							} else {
								itemOutputDelay = TypeUtil.typeConvert(itemOutputDelayRaw, long.class);
							}
							nextItemBuff = new DelayedTransferConvertBuffer<>(
								storageConfig.intVal("driver-limit-queue-output"), itemOutputDelay, TimeUnit.SECONDS
							);
							stepCtx.operationsResultsOutput(nextItemBuff);
						} else {
							final String itemOutputFile = itemConfig.stringVal("output-file");
							if(itemOutputFile != null && itemOutputFile.length() > 0) {
								final Path itemOutputPath = Paths.get(itemOutputFile);
								if(Files.exists(itemOutputPath)) {
									Loggers.ERR.warn("Items output file \"{}\" already exists", itemOutputPath);
								}
								try {
									final Output<? extends Item> itemOutput = new ItemInfoFileOutput<>(itemOutputPath);
									stepCtx.operationsResultsOutput(itemOutput);
								} catch(final IOException e) {
									LogUtil.exception(
										Level.ERROR, e,
										"Failed to initialize the item output, the processed items info won't be "
											+ "persisted"
									);
								}
							}
						}

					} catch(final OmgShootMyFootException e) {
						throw new IllegalStateException("Failed to initialize the load generator", e);
					}
				} catch(final OmgShootMyFootException e) {
					throw new IllegalStateException("Failed to initialize the storage driver", e);
				} catch(final InterruptedException e) {
					throw new CancellationException();
				}
			} catch(final IOException e) {
				throw new IllegalStateException("Failed to initialize the data input", e);
			}
		}
	}

	@Override
	public String getTypeName() {
		return PipelineLoadStepExtension.TYPE;
	}
}
