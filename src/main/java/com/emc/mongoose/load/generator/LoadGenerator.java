package com.emc.mongoose.load.generator;

import com.emc.mongoose.exception.InterruptRunException;
import com.emc.mongoose.item.op.Operation;
import com.emc.mongoose.item.Item;
import com.emc.mongoose.item.op.OpType;

import com.github.akurilov.fiber4j.Fiber;

import java.io.IOException;

/**
 Created on 11.07.16.
 */
public interface LoadGenerator<I extends Item, O extends Operation<I>>
extends Fiber {

	/**
	 @return true if item input has been read until its end, false otherwise
	 */
	boolean isItemInputFinished();
	
	/**
	 @return sum of the new tasks and recycled ones
	 */
	long generatedOpCount();

	/**
	 Enqueues the task for further recycling
	 @param op the task to recycle
	 */
	void recycle(final O op);

	/**
	 @return true if the internal recycle queue is empty, false otherwise
	 */
	boolean isNothingToRecycle();

	@Override
	void close()
	throws IOException, InterruptRunException;
}
