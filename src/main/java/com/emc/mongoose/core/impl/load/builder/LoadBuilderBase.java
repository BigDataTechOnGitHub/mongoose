package com.emc.mongoose.core.impl.load.builder;
// mongoose-common.jar
import com.emc.mongoose.common.concurrent.ThreadUtil;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.log.LogUtil;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.Item;
import com.emc.mongoose.core.api.data.model.ItemSrc;
import com.emc.mongoose.core.api.io.conf.IOConfig;
import com.emc.mongoose.core.api.io.conf.RequestConfig;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.load.builder.LoadBuilder;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
//
import org.apache.commons.configuration.ConversionException;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 20.10.14.
 */
public abstract class LoadBuilderBase<T extends Item, U extends LoadExecutor<T>>
implements LoadBuilder<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected volatile RunTimeConfig rtConfig;
	protected long maxCount = 0;
	protected volatile IOConfig<?, ?> ioConfig = getDefaultIOConfig();
	protected float rateLimit;
	protected int manualTaskSleepMicroSecs;
	protected ItemSrc itemSrc;
	protected String storageNodeAddrs[];
	protected final Map<IOTask.Type, Integer>
		loadTypeWorkerCount = new HashMap<>(),
		loadTypeConnPerNode = new HashMap<>();
	protected boolean flagUseNewItemSrc, flagUseNoneItemSrc;
	//
	protected abstract IOConfig<?, ?> getDefaultIOConfig();
	//
	public LoadBuilderBase()
	throws RemoteException {
		this(RunTimeConfig.getContext());
	}
	//
	public LoadBuilderBase(final RunTimeConfig runTimeConfig)
	throws RemoteException {
		resetItemSrc();
		setRunTimeConfig(runTimeConfig);
	}
	//
	protected void resetItemSrc() {
		flagUseNewItemSrc = true;
		flagUseNoneItemSrc = false;
		itemSrc = null;
	}
	//
	@Override
	public LoadBuilder<T, U> setRunTimeConfig(final RunTimeConfig rtConfig)
	throws IllegalStateException, RemoteException {
		this.rtConfig = rtConfig;
		RunTimeConfig.setContext(rtConfig);
		if(ioConfig != null) {
			ioConfig.setRunTimeConfig(rtConfig);
		} else {
			throw new IllegalStateException("Shared request config is not initialized");
		}
		//
		String paramName;
		for(final IOTask.Type loadType: IOTask.Type.values()) {
			paramName = RunTimeConfig.getConnCountPerNodeParamName(loadType.name().toLowerCase());
			try {
				setConnPerNodeFor(
					rtConfig.getConnCountPerNodeFor(loadType.name().toLowerCase()), loadType
				);
			} catch(final NoSuchElementException e) {
				LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
			} catch(final IllegalArgumentException e) {
				LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
			}
		}
		//
		for(final IOTask.Type loadType: IOTask.Type.values()) {
			paramName = RunTimeConfig.getLoadWorkersParamName(loadType.name().toLowerCase());
			try {
				setWorkerCountFor(
					rtConfig.getWorkerCountFor(loadType.name().toLowerCase()), loadType
				);
			} catch(final NoSuchElementException e) {
				LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
			} catch(final IllegalArgumentException e) {
				LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
			}
		}
		//
		paramName = RunTimeConfig.KEY_DATA_ITEM_COUNT;
		try {
			setMaxCount(rtConfig.getLoadLimitCount());
		} catch(final NoSuchElementException e) {
			LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
		} catch(final IllegalArgumentException e) {
			LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
		}
		//
		paramName = RunTimeConfig.KEY_LOAD_LIMIT_REQSLEEP_MILLISEC;
		try {
			setManualTaskSleepMicroSecs(
				(int) TimeUnit.MILLISECONDS.toMicros(
					rtConfig.getLoadLimitReqSleepMilliSec()
				)
			);
		} catch(final NoSuchElementException e) {
			LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
		} catch(final IllegalArgumentException e) {
			LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
		}
		//
		paramName = RunTimeConfig.KEY_LOAD_LIMIT_RATE;
		try {
			setRateLimit(rtConfig.getLoadLimitRate());
		} catch(final NoSuchElementException e) {
			LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
		} catch(final IllegalArgumentException e) {
			LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
		}
		//
		if(ioConfig instanceof RequestConfig) {
			final RequestConfig reqConfig = (RequestConfig) ioConfig;
			paramName = RunTimeConfig.KEY_STORAGE_ADDRS;
			try {
				setDataNodeAddrs(rtConfig.getStorageAddrsWithPorts());
			} catch(final NoSuchElementException | ConversionException e) {
				LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
			} catch(final IllegalArgumentException e) {
				LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
			}
			//
			paramName = RunTimeConfig.getApiPortParamName(reqConfig.getAPI().toLowerCase());
			try {
				reqConfig.setPort(rtConfig.getApiTypePort(reqConfig.getAPI().toLowerCase()));
			} catch(final NoSuchElementException e) {
				LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
			}
		}
		//
		return this;
	}
	//
	protected boolean itemsFileExists(final String filePathStr) {
		if(filePathStr != null && !filePathStr.isEmpty()) {
			final Path listFilePath = Paths.get(filePathStr);
			if(!Files.exists(listFilePath)) {
				throw new IllegalArgumentException(
					String.format("Specified input file \"%s\" doesn't exists", listFilePath)
				);
			} else if(!Files.isReadable(listFilePath)) {
				throw new IllegalArgumentException(
					String.format("Specified input file \"%s\" isn't readable", listFilePath)
				);
			} else if(Files.isDirectory(listFilePath)) {
				throw new IllegalArgumentException(
					String.format("Specified input file \"%s\" is a directory", listFilePath)
				);
			} else {
				return true;
			}
		}
		return false;
	}
	//
	@Override
	public final IOConfig<?, ?> getIOConfig() {
		return ioConfig;
	}
	//
	@Override
	public final LoadBuilder<T, U> setIOConfig(final IOConfig<?, ?> ioConfig)
	throws ClassCastException, RemoteException {
		if(this.ioConfig.equals(ioConfig)) {
			return this;
		}
		LOG.debug(Markers.MSG, "Set request builder: {}", ioConfig.toString());
		try {
			this.ioConfig.close(); // see jira ticket #437
		} catch(final IOException e) {
			LogUtil.exception(
				LOG, Level.WARN, e, "Failed to close the replacing conf config instance #{}",
				hashCode()
			);
		}
		this.ioConfig = ioConfig;
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setLoadType(final IOTask.Type loadType)
	throws IllegalStateException, RemoteException {
		LOG.debug(Markers.MSG, "Set load type: {}", loadType);
		if(ioConfig == null) {
			throw new IllegalStateException(
				"Request builder should be specified before setting an I/O loadType"
			);
		} else {
			ioConfig.setLoadType(loadType);
		}
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setMaxCount(final long maxCount)
	throws IllegalArgumentException, RemoteException {
		LOG.debug(Markers.MSG, "Set max data item count: {}", maxCount);
		if(maxCount < 0) {
			throw new IllegalArgumentException("Count should be >= 0");
		}
		this.maxCount = maxCount;
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setRateLimit(final float rateLimit)
	throws IllegalArgumentException, RemoteException {
		LOG.debug(Markers.MSG, "Set rate limit to: {}", rateLimit);
		if(rateLimit < 0) {
			throw new IllegalArgumentException("Rate limit should not be negative");
		} else {
			LOG.debug(Markers.MSG, "Using load rate limit: {}", rateLimit);
		}
		this.rateLimit = rateLimit;
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setManualTaskSleepMicroSecs(final int manualTaskSleepMicroSecs)
	throws IllegalArgumentException, RemoteException {
		LOG.debug(Markers.MSG, "Set manual I/O tasks sleep to: {}[us]", manualTaskSleepMicroSecs);
		if(rateLimit < 0) {
			throw new IllegalArgumentException("Tasks sleep time shouldn't be negative");
		} else {
			LOG.debug(Markers.MSG, "Using tasks sleep time: {}[us]", manualTaskSleepMicroSecs);
		}
		this.manualTaskSleepMicroSecs = manualTaskSleepMicroSecs;
		return this;
	}
	//

	//
	@Override
	public LoadBuilder<T, U> setWorkerCountDefault(final int workersPerNode)
	throws RemoteException {
		for(final IOTask.Type loadType: IOTask.Type.values()) {
			setWorkerCountFor(workersPerNode, loadType);
		}
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setWorkerCountFor(
		final int workersPerNode, final IOTask.Type loadType
	) throws RemoteException {
		if(workersPerNode > 0) {
			loadTypeWorkerCount.put(loadType, workersPerNode);
		} else {
			loadTypeWorkerCount.put(loadType, ThreadUtil.getWorkerCount());
		}
		LOG.debug(
			Markers.MSG, "Set worker count per node {} for load type \"{}\"",
			workersPerNode, loadType
		);
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setConnPerNodeDefault(final int connPerNode)
	throws IllegalArgumentException, RemoteException {
		LOG.debug(Markers.MSG, "Set default connection count per node: {}", connPerNode);
		for(final IOTask.Type loadType : IOTask.Type.values()) {
			setConnPerNodeFor(connPerNode, loadType);
		}
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setConnPerNodeFor(
		final int connPerNode, final IOTask.Type loadType
	) throws IllegalArgumentException, RemoteException {
		if(connPerNode < 1) {
			throw new IllegalArgumentException("Concurrency level should not be less than 1");
		}
		LOG.debug(
			Markers.MSG, "Set connection count per node {} for load type \"{}\"",
			connPerNode, loadType
		);
		loadTypeConnPerNode.put(loadType, connPerNode);
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setDataNodeAddrs(
		final String[] dataNodeAddrs
	) throws IllegalArgumentException, RemoteException {
		LOG.debug(Markers.MSG, "Set storage nodes: {}", Arrays.toString(dataNodeAddrs));
		if(dataNodeAddrs == null || dataNodeAddrs.length == 0) {
			throw new IllegalArgumentException("Data node address list should not be empty");
		}
		this.storageNodeAddrs = dataNodeAddrs;
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setItemSrc(final ItemSrc<T> itemSrc)
	throws RemoteException {
		LOG.debug(Markers.MSG, "Set data items source: {}", itemSrc);
		this.itemSrc = itemSrc;
		return this;
	}
	//
	@Override @SuppressWarnings("unchecked")
	public LoadBuilderBase<T, U> clone()
	throws CloneNotSupportedException {
		final LoadBuilderBase<T, U> lb = (LoadBuilderBase<T, U>) super.clone();
		lb.rtConfig = (RunTimeConfig) rtConfig.clone();
		LOG.debug(Markers.MSG, "Cloning request config for {}", ioConfig.toString());
		lb.ioConfig = ioConfig.clone();
		lb.maxCount = maxCount;
		for(final IOTask.Type loadType : loadTypeWorkerCount.keySet()) {
			lb.loadTypeWorkerCount.put(loadType, loadTypeWorkerCount.get(loadType));
		}
		for(final IOTask.Type loadType : loadTypeConnPerNode.keySet()) {
			lb.loadTypeConnPerNode.put(loadType, loadTypeConnPerNode.get(loadType));
		}
		lb.storageNodeAddrs = storageNodeAddrs;
		lb.itemSrc = itemSrc;
		lb.rateLimit = rateLimit;
		lb.manualTaskSleepMicroSecs = manualTaskSleepMicroSecs;
		lb.flagUseNewItemSrc = flagUseNewItemSrc;
		lb.flagUseNoneItemSrc = flagUseNoneItemSrc;
		return lb;
	}
	//
	protected abstract ItemSrc<T> getDefaultItemSource();
	//
	@Override
	public LoadBuilderBase<T, U> useNewItemSrc()
	throws RemoteException {
		flagUseNewItemSrc = true;
		return this;
	}
	//
	@Override
	public LoadBuilderBase<T, U> useNoneItemSrc()
	throws RemoteException {
		flagUseNoneItemSrc = true;
		return this;
	}
	//
	@Override
	public final U build()
	throws RemoteException {
		try {
			invokePreConditions();
		} catch(final RemoteException | IllegalStateException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Preconditions failure");
		}
		try {
			return buildActually();
		} finally {
			resetItemSrc();
		}
	}
	//
	protected final int getMinIOThreadCount(
		final int threadCount, final int nodeCount, final int connCountPerNode
	) {
		return Math.min(Math.max(threadCount, nodeCount), nodeCount * connCountPerNode);
	}
	//
	protected abstract U buildActually()
	throws RemoteException;
	//
	@Override
	public String toString() {
		return ioConfig.toString() + "." +
			loadTypeConnPerNode.get(loadTypeConnPerNode.keySet().iterator().next());
	}
	//
	@Override
	public void close()
	throws IOException {
		ioConfig.close();
	}
}
