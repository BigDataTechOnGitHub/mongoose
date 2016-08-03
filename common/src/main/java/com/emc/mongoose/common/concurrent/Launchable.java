package com.emc.mongoose.common.concurrent;

import com.emc.mongoose.common.exception.UserShootHisFootException;

import java.util.concurrent.TimeUnit;

/**
 Created on 21.07.16.
 */
public interface Launchable {

	void start()
	throws UserShootHisFootException;

	boolean isStarted();

	boolean await()
	throws InterruptedException;

	boolean await(final long timeout, final TimeUnit timeUnit)
	throws InterruptedException;

}