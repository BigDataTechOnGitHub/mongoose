package com.emc.mongoose.core.impl.load.executor;
// mongoose-common.jar
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.DataItemInput;
import com.emc.mongoose.core.api.io.req.RequestConfig;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 04.05.15.
 The extension of load executor which is able to sustain the rate (throughput, item/sec) not higher
 than the specified limit.
 */
public abstract class LimitedRateLoadExecutorBase<T extends DataItem>
extends LoadExecutorBase<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final float rateLimit;
	private final int tgtDurMicroSec;
	//
	protected LimitedRateLoadExecutorBase(
		final Class<T> dataCls,
		final RunTimeConfig runTimeConfig, final RequestConfig<T> reqConfig, final String[] addrs,
		final int connCountPerNode, final int threadCount,
		final DataItemInput<T> itemSrc, final long maxCount,
		final float rateLimit
	) throws ClassCastException {
		super(
			dataCls, runTimeConfig, reqConfig, addrs, connCountPerNode, threadCount,
			itemSrc, maxCount
		);
		//
		if(rateLimit < 0) {
			throw new IllegalArgumentException("Frequency rate limit shouldn't be a negative value");
		}
		this.rateLimit = rateLimit;
		if(rateLimit > 0) {
			tgtDurMicroSec = (int) (1000000 * totalConnCount / rateLimit);
			LOG.debug(
				Markers.MSG, "{}: target I/O task durations is {}[us]", getName(), tgtDurMicroSec
			);
		} else {
			tgtDurMicroSec = 0;
		}
	}
	//
	private void invokeDelayToMatchRate(final int itemCountToFeed)
	throws InterruptedException {
		if(rateLimit > 0 && lastStats.getSuccRateLast() > rateLimit && itemCountToFeed > 0) {
			final int microDelay = itemCountToFeed * (int) (
				tgtDurMicroSec - lastStats.getDurationSum() / lastStats.getSuccRateMean()
			);
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(Markers.MSG, "Next delay: {}[us]", microDelay);
			}
			TimeUnit.MICROSECONDS.sleep(microDelay);
		}
	}
	/**
	 Adds the optional delay calculated from last successful I/O task duration and the target
	 duration
	 */
	@Override
	public void feed(final T dataItem)
	throws InterruptedException, RemoteException, RejectedExecutionException {
		invokeDelayToMatchRate(1);
		super.feed(dataItem);
	}
	/**
	 Adds the optional delay calculated from last successful I/O task duration and the target
	 duration
	 */
	@Override
	public void feedBatch(final List<T> dataItems)
	throws InterruptedException, RemoteException, RejectedExecutionException {
		invokeDelayToMatchRate(dataItems.size());
		super.feedBatch(dataItems);
	}
}
