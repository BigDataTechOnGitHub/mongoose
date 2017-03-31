package com.emc.mongoose.model;

import com.emc.mongoose.common.concurrent.Daemon;

import static com.emc.mongoose.common.concurrent.Daemon.State.CLOSED;
import static com.emc.mongoose.common.concurrent.Daemon.State.INITIAL;
import static com.emc.mongoose.common.concurrent.Daemon.State.INTERRUPTED;
import static com.emc.mongoose.common.concurrent.Daemon.State.SHUTDOWN;
import static com.emc.mongoose.common.concurrent.Daemon.State.STARTED;
import static com.emc.mongoose.common.concurrent.ThreadUtil.getHardwareConcurrencyLevel;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static java.util.Map.Entry;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 Created on 12.07.16.
 */
public abstract class DaemonBase
implements Daemon {

	private static final Map<Daemon, BlockingQueue<Runnable>> SVC_TASKS = new ConcurrentHashMap<>();
	
	private static final ExecutorService SVC_TASKS_EXECUTOR = Executors.newFixedThreadPool(
		getHardwareConcurrencyLevel(), new NamingThreadFactory("svcTasksWorker", true)
	);
	
	static {
		for(int i = 0; i < getHardwareConcurrencyLevel(); i ++) {
			SVC_TASKS_EXECUTOR.submit(
				() -> {
					Set<Entry<Daemon, BlockingQueue<Runnable>>> svcTaskEntries;
					BlockingQueue<Runnable> nextSvcTasksQueue;
					final List<Runnable> nextSvcTasks = new ArrayList<>(MAX_DAEMON_SVC_TASKS);
					while(true) {
						svcTaskEntries = SVC_TASKS.entrySet();
						if(svcTaskEntries.size() == 0) {
							Thread.sleep(1);
						} else {
							for(final Entry<Daemon, BlockingQueue<Runnable>> entry : svcTaskEntries) {
								nextSvcTasksQueue = entry.getValue();
								nextSvcTasksQueue.drainTo(nextSvcTasks, MAX_DAEMON_SVC_TASKS);
								for(final Runnable nextSvcTask : nextSvcTasks) {
									try {
										nextSvcTask.run();
									} catch(final Throwable t) {
										System.err.println(
											entry.getKey().toString() + ": service task \"" +
												nextSvcTask + "\"  failed:"
										);
										t.printStackTrace(System.err);
									}
								}
								nextSvcTasks.clear();
							}
						}
					}
				}
			);
		}
	}
	
	protected final BlockingQueue<Runnable> svcTasks = new ArrayBlockingQueue<>(
		MAX_DAEMON_SVC_TASKS
	);
	
	private AtomicReference<State> stateRef = new AtomicReference<>(INITIAL);
	protected final Object state = new Object();
	
	protected void doStart()
	throws IllegalStateException {
		SVC_TASKS.put(this, svcTasks);
	}

	protected abstract void doShutdown()
	throws IllegalStateException;

	protected abstract void doInterrupt()
	throws IllegalStateException;
	
	protected void doClose()
	throws IOException, IllegalStateException {
		SVC_TASKS.remove(this);
		svcTasks.clear();
	}

	@Override
	public final void start()
	throws IllegalStateException {
		if(stateRef.compareAndSet(INITIAL, STARTED)) {
			synchronized(state) {
				state.notifyAll();
			}
			doStart();
		} else {
			throw new IllegalStateException("start failed: state is " + stateRef.get());
		}
	}

	@Override
	public final boolean isStarted() {
		return stateRef.get().equals(STARTED);
	}

	@Override
	public final void shutdown()
	throws IllegalStateException {
		if(stateRef.compareAndSet(INITIAL, SHUTDOWN) || stateRef.compareAndSet(STARTED, SHUTDOWN)) {
			synchronized(state) {
				state.notifyAll();
			}
			doShutdown();
		} else {
			throw new IllegalStateException("shutdown failed: state is " + stateRef.get());
		}
	}

	@Override
	public final boolean isShutdown() {
		return stateRef.get().equals(SHUTDOWN);
	}
	
	@Override
	public final void await()
	throws InterruptedException, RemoteException {
		await(Long.MAX_VALUE, TimeUnit.SECONDS);
	}
	
	@Override
	public final void interrupt()
	throws IllegalStateException {
		try {
			shutdown();
		} catch(final IllegalStateException ignored) {
		}
		if(stateRef.compareAndSet(SHUTDOWN, INTERRUPTED)) {
			synchronized(state) {
				state.notifyAll();
			}
			doInterrupt();
		} else {
			throw new IllegalStateException("interrupt failed: state is " + stateRef.get());
		}
	}

	@Override
	public final boolean isInterrupted() {
		return stateRef.get().equals(INTERRUPTED);
	}
	
	@Override
	public void close()
	throws IOException, IllegalStateException {
		try {
			interrupt();
		} catch(final IllegalStateException ignored) {
		}
		if(stateRef.compareAndSet(INTERRUPTED, CLOSED)) {
			synchronized(state) {
				state.notifyAll();
			}
			doClose();
			// may be closed by another thread right after the interruption
		} else if(!CLOSED.equals(stateRef.get())) {
			throw new IllegalStateException("close failed: state is " + stateRef.get());
		}
	}
	
	@Override
	public final boolean isClosed() {
		return stateRef.get().equals(CLOSED);
	}
}