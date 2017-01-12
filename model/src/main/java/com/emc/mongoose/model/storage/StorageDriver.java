package com.emc.mongoose.model.storage;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.concurrent.Daemon;
import com.emc.mongoose.common.io.Output;
import com.emc.mongoose.common.net.ServiceUtil;
import com.emc.mongoose.model.ServiceTaskDispatcher;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.model.io.task.IoTask;
import static com.emc.mongoose.model.io.task.IoTask.IoResult;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.item.ItemFactory;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 Created on 11.07.16.
 */
public interface StorageDriver<I extends Item, O extends IoTask<I, R>, R extends IoResult>
extends Daemon, Output<O>, Remote {
	
	Map<StorageDriver, Runnable> SVC_TASKS = new ConcurrentHashMap<>();
	Thread SVC_TASKS_WORKER = new Thread(
		new ServiceTaskDispatcher(SVC_TASKS), "ioTasksDispatcher"
	) {
		{
			setDaemon(true);
			start();
		}
	};

	String HOST_ADDR = ServiceUtil.getHostAddr();
	int BUFF_SIZE_MIN = 0x1000;
	int BUFF_SIZE_MAX = 0x1000000;
	
	boolean createPath(final String path)
	throws RemoteException;

	List<I> list(
		final ItemFactory<I> itemFactory, final String path, final String prefix, final int idRadix,
		final I lastPrevItem, final int count
	) throws IOException;

	String getAuthToken()
	throws RemoteException;

	void setAuthToken(final String authToken)
	throws RemoteException;

	List<R> getResults()
	throws IOException;

	int getActiveTaskCount()
	throws RemoteException;
	
	long getScheduledTaskCount()
	throws RemoteException;
	
	long getCompletedTaskCount()
	throws RemoteException;

	long getRecycledTaskCount()
	throws RemoteException;

	boolean isIdle()
	throws RemoteException;

	boolean isFullThrottleEntered()
	throws RemoteException;

	boolean isFullThrottleExited()
	throws RemoteException;
	
	void adjustIoBuffers(final SizeInBytes avgDataItemSize, final IoType ioType)
	throws RemoteException;
}
