package com.emc.mongoose.core.impl.load.model;
//mongoose-common.jar
import com.emc.mongoose.common.concurrent.NamingWorkerFactory;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.logging.LogUtil;
//mongoose-core-api.jar
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
import com.emc.mongoose.core.api.load.model.Consumer;
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.load.model.Producer;
//mongoose-core-impl.jar
import com.emc.mongoose.core.impl.load.model.reader.RandomFileReader;
import com.emc.mongoose.core.impl.load.tasks.SubmitTask;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 12.05.14.
 A data item producer which constructs data items while reading the special input file.
 */
public class FileProducer<T extends DataItem>
extends Thread
implements Producer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static short
		MAX_COUNT_TO_ESTIMATE_SIZES = 100,
		MAX_WAIT_TO_ESTIMATE_MILLIS = 10000;
	//
	private final Path fPath;
	private final Constructor<T> dataItemConstructor;
	private final long maxCount;
	private long approxDataItemsSize = LoadExecutor.BUFF_SIZE_LO;
	private final ExecutorService producerExecSvc;
	//
	private Consumer<T> consumer = null;
	//
	@SuppressWarnings("unchecked")
	public FileProducer(final long maxCount, final String fPathStr, final Class<T> dataItemsImplCls)
	throws NoSuchMethodException, IOException {
		this(maxCount, fPathStr, dataItemsImplCls, false);
	}
	//
	private FileProducer(
		final long maxCount, final String fPathStr, final Class<T> dataItemsImplCls,
		final boolean nested
	) throws IOException, NoSuchMethodException {
		super(fPathStr);
		producerExecSvc = Executors.newFixedThreadPool(
			Producer.WORKER_COUNT, new NamingWorkerFactory(fPathStr)
		);
		fPath = FileSystems.getDefault().getPath(fPathStr);
		if(!Files.exists(fPath)) {
			throw new IOException("File \""+fPathStr+"\" doesn't exist");
		}
		if(!Files.isReadable(fPath)) {
			throw new IOException("File \""+fPathStr+"\" is not readable");
		}
		dataItemConstructor = dataItemsImplCls.getConstructor(String.class);
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		//
		if(!nested) {
			// try to read 1st max 100 data items to determine the
			new FileProducer<T>(MAX_COUNT_TO_ESTIMATE_SIZES, fPathStr, dataItemsImplCls, true) {
				{
					setConsumer(
						new Consumer<T>() {
							//
							private long sizeSum = 0, count = 0;
							//
							@Override
							public final void submit(final T data) {
								sizeSum += data.getSize();
								count ++;
								approxDataItemsSize = sizeSum / count;
							}
							//
							@Override
							public final void shutdown() {
							}
							//
							@Override
							public final long getMaxCount() {
								return MAX_COUNT_TO_ESTIMATE_SIZES;
							}
							//
							@Override
							public final void close() {
							}
						}
					);
					//
					start();
					try {
						join(MAX_WAIT_TO_ESTIMATE_MILLIS);
					} catch(final InterruptedException e) {
						LogUtil.failure(LOG, Level.WARN, e, "Interrupted");
					} finally {
						interrupt();
					}
				}
			};
			//
			// TODO randomize the lines if necessary
		}
	}
	//
	public final String getPath() {
		return fPath.toString();
	}
	//
	public final long getApproxDataItemsSize() {
		return approxDataItemsSize;
	}
	//
	@Override
	public final void run() {
		long dataItemsCount = 0;
		BufferedReader fReader = null;
		try {
			String nextLine;
			T nextData;
			LOG.debug(
				LogUtil.MSG, "Going to produce up to {} data items for consumer \"{}\"",
				consumer.getMaxCount(), consumer.toString()
			);
			//
			if(RunTimeConfig.getContext().isEnabledDataRandom()) {
				final long batchSize = RunTimeConfig.getContext().getDataRandomBatchSize();
				final Charset charset =  StandardCharsets.UTF_8;
				final CharsetDecoder decoder = charset.newDecoder();
				final InputStreamReader reader = new InputStreamReader(
					Files.newInputStream(fPath), decoder);
				//
				fReader = new RandomFileReader(reader, batchSize, maxCount);
			} else {
				fReader = Files.newBufferedReader(fPath, StandardCharsets.UTF_8);
			}
			//
			do {
				//
				nextLine = fReader.readLine();
				LOG.trace(LogUtil.MSG, "Got next line #{}: \"{}\"", dataItemsCount, nextLine);
				//
				if(nextLine == null || nextLine.isEmpty()) {
					LOG.debug(LogUtil.MSG, "No next line, exiting");
					break;
				} else {
					nextData = dataItemConstructor.newInstance(nextLine);
					try {
						producerExecSvc.submit(
							SubmitTask.getInstance(consumer, nextData)
						);
						dataItemsCount ++;
					} catch(final Exception e) {
						if(
							consumer.getMaxCount() > dataItemsCount &&
							!RejectedExecutionException.class.isInstance(e)
						) {
							LogUtil.failure(LOG, Level.WARN, e, "Failed to submit data item");
							break;
						} else {
							LogUtil.failure(LOG, Level.DEBUG, e, "Failed to submit data item");
						}
					}
				}
			} while(!isInterrupted() && dataItemsCount < maxCount);
		} catch(final IOException e) {
			LogUtil.failure(LOG, Level.ERROR, e, "Failed to read line from the file");
		} catch(final Exception e) {
			//e.printStackTrace(System.err);
			LogUtil.failure(LOG, Level.ERROR, e, "Unexpected failure");
		} finally {
			LOG.debug(LogUtil.MSG, "Produced {} data items", dataItemsCount);
			try {
				LOG.debug(LogUtil.MSG, "Feeding poison to consumer \"{}\"", consumer.toString());
				if(isInterrupted()) {
					producerExecSvc.shutdownNow();
				} else {
					producerExecSvc.shutdown();
					producerExecSvc.awaitTermination(
						RunTimeConfig.getContext().getRunReqTimeOutMilliSec(),
						TimeUnit.MILLISECONDS
					);
				}
				consumer.shutdown();
				if (fReader != null) {
					fReader.close();
				}
			} catch(final IOException e) {
				LogUtil.failure(LOG, Level.WARN, e, "Failed to close file reader.");
			}catch(final Exception e) {
				LogUtil.failure(LOG, Level.WARN, e, "Failed to shut down the consumer");
			}
			LOG.debug(LogUtil.MSG, "Exiting");
		}
	}
	//
	@Override
	public final void setConsumer(final Consumer<T> consumer) {
		LOG.debug(LogUtil.MSG, "Set consumer to \"{}\"", consumer.toString());
		this.consumer = consumer;
	}
	//
	@Override
	public final Consumer<T> getConsumer()
	throws RemoteException {
		return consumer;
	}
	//
	@Override
	public final void interrupt() {
		producerExecSvc.shutdownNow();
	}
}
