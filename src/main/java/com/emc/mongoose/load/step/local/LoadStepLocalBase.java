package com.emc.mongoose.load.step.local;

import com.emc.mongoose.config.TimeUtil;
import com.emc.mongoose.env.Extension;
import com.emc.mongoose.exception.InterruptRunException;
import com.emc.mongoose.item.op.OpType;
import com.emc.mongoose.load.step.LoadStepBase;
import com.emc.mongoose.load.step.local.context.LoadStepContext;
import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;
import com.emc.mongoose.metrics.context.MetricsContext;
import com.emc.mongoose.metrics.context.MetricsContextImpl;
import com.emc.mongoose.metrics.MetricsManager;
import static com.emc.mongoose.Constants.KEY_CLASS_NAME;
import static com.emc.mongoose.Constants.KEY_STEP_ID;

import com.github.akurilov.commons.concurrent.AsyncRunnable;
import com.github.akurilov.commons.reflection.TypeUtil;
import com.github.akurilov.commons.system.SizeInBytes;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.fiber4j.Fiber;
import org.apache.logging.log4j.Level;
import static org.apache.logging.log4j.CloseableThreadContext.Instance;
import static org.apache.logging.log4j.CloseableThreadContext.put;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public abstract class LoadStepLocalBase
extends LoadStepBase {

	protected final List<LoadStepContext> stepContexts = new ArrayList<>();

	protected LoadStepLocalBase(
		final Config baseConfig, final List<Extension> extensions, final List<Config> contextConfigs,
		final MetricsManager metricsManager
	) {
		super(baseConfig, extensions, contextConfigs, metricsManager);
	}

	@Override
	protected void doStartWrapped() {
		stepContexts.forEach(
			stepCtx -> {
				try {
					stepCtx.start();
				} catch(final RemoteException ignored) {
				} catch(final IllegalStateException e) {
					LogUtil.exception(Level.WARN, e, "{}: failed to start the load step context \"{}\"", id(), stepCtx);
				}
			}
		);
	}

	@Override
	protected final void initMetrics(
		final int originIndex, final OpType opType, final int concurrency, final Config metricsConfig,
		final SizeInBytes itemDataSize, final boolean outputColorFlag
	) {
		final int index = metricsContexts.size();
		final MetricsContext metricsCtx = MetricsContextImpl
			.builder()
			.id(id())
			.opType(opType)
			.actualConcurrencyGauge(() -> stepContexts.get(index).activeOpCount())
			.concurrencyLimit(concurrency)
			.concurrencyThreshold((int) (concurrency * metricsConfig.doubleVal("threshold")))
			.itemDataSize(itemDataSize)
			.outputPeriodSec(avgPeriod(metricsConfig))
			.stdOutColorFlag(outputColorFlag)
			.comment(config.stringVal("run-comment"))
			.build();
		metricsContexts.add(metricsCtx);
	}

	@Override
	protected final void doShutdown() {
		stepContexts.forEach(
			stepCtx -> {
				try(
					final Instance ctx = put(KEY_STEP_ID, id())
						.put(KEY_CLASS_NAME, getClass().getSimpleName())
				) {
					stepCtx.shutdown();
					Loggers.MSG.debug("{}: load step context shutdown", id());
				} catch(final RemoteException ignored) {
				}
			}
		);
	}

	@Override
	public final boolean await(final long timeout, final TimeUnit timeUnit)
	throws InterruptRunException, IllegalStateException {

		final long timeoutMillis = timeout > 0 ? timeUnit.toMillis(timeout) : Long.MAX_VALUE;
		final long startTimeMillis = System.currentTimeMillis();
		final int stepCtxCount = stepContexts.size();
		final LoadStepContext[] stepContextsCopy = stepContexts.toArray(new LoadStepContext[stepCtxCount]);
		int countDown = stepCtxCount;
		LoadStepContext stepCtx;
		boolean timeIsOut = false;

		while(countDown > 0 && !timeIsOut) {
			for(int i = 0; i < stepCtxCount; i ++) {
				if(timeoutMillis <= System.currentTimeMillis() - startTimeMillis) {
					timeIsOut = true;
					break;
				}
				stepCtx = stepContextsCopy[i];
				if(stepCtx != null) {
					try {
						if(stepCtx.isDone() || stepCtx.await(Fiber.SOFT_DURATION_LIMIT_NANOS, TimeUnit.NANOSECONDS)) {
							stepContextsCopy[i] = null; // exclude
							countDown --;
							break;
						}
					} catch(final InterruptedException e) {
						throw new InterruptRunException(e);
					} catch(final RemoteException ignored) {
					}
				}
			}
		}

		return 0 == countDown;
	}

	@Override
	protected final void doStop()
	throws InterruptRunException {
		stepContexts.forEach(LoadStepContext::stop);
		super.doStop();
	}

	protected final void doClose()
	throws InterruptRunException, IOException {
		super.doClose();
		stepContexts
			.parallelStream()
			.filter(Objects::nonNull)
			.forEach(
				stepCtx -> {
					try {
						stepCtx.close();
					} catch(final IOException e) {
						LogUtil.exception(
							Level.ERROR, e, "Failed to close the load step context \"{}\"", stepCtx.toString()
						);
					}
				}
			);
		stepContexts.clear();
	}
}
