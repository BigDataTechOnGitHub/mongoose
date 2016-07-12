package com.emc.mongoose.common.concurrent;

import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 23.09.15.
 */
public interface LifeCycle {

	void start()
	throws IllegalStateException;

	void shutdown()
	throws IllegalStateException;

	boolean await()
	throws InterruptedException;

	boolean await(final long timeout, final TimeUnit timeUnit)
	throws InterruptedException;

	void interrupt()
	throws IllegalStateException;
}