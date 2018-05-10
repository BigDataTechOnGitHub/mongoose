package com.emc.mongoose.model.io.task.composite.data;

import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.model.io.task.data.BasicDataIoTask;
import com.emc.mongoose.model.io.task.partial.data.BasicPartialDataIoTask;
import com.emc.mongoose.model.io.task.partial.data.PartialDataIoTask;
import com.emc.mongoose.model.item.DataItem;
import com.emc.mongoose.model.storage.Credential;
import com.github.akurilov.commons.collection.Range;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 Created by andrey on 25.11.16.
 */
public class BasicCompositeDataIoTask<I extends DataItem>
extends BasicDataIoTask<I>
implements CompositeDataIoTask<I> {

	private long sizeThreshold;
	private AtomicInteger pendingSubTasksCount = new AtomicInteger(-1);

	private transient final Map<String, String> contextData = new HashMap<>();
	private transient final List<PartialDataIoTask<I>> subTasks = new ArrayList<>();

	public BasicCompositeDataIoTask() {
		super();
	}

	public BasicCompositeDataIoTask(
		final int originIndex, final IoType ioType, final I item, final String srcPath,
		final String dstPath, final Credential credential, final List<Range> fixedRanges,
		final int randomRangesCount, final long sizeThreshold
	) {
		super(
			originIndex, ioType, item, srcPath, dstPath, credential, fixedRanges, randomRangesCount
		);
		this.sizeThreshold = sizeThreshold;
	}

	protected BasicCompositeDataIoTask(final BasicCompositeDataIoTask<I> other) {
		super(other);
		this.sizeThreshold = other.sizeThreshold;
		this.pendingSubTasksCount.set(other.pendingSubTasksCount.get());
	}

	@Override
	public final String get(final String key) {
		return contextData.get(key);
	}

	@Override
	public final void put(final String key, final String value) {
		contextData.put(key, value);
	}

	@Override
	public final List<? extends PartialDataIoTask<I>> subTasks() {

		if(!subTasks.isEmpty()) {
			return subTasks;
		}

		final int equalPartsCount = sizeThreshold > 0 ? (int) (contentSize / sizeThreshold) : 0;
		final long tailPartSize = contentSize % sizeThreshold;
		I nextPart;
		PartialDataIoTask<I> nextSubTask;
		for(int i = 0; i < equalPartsCount; i ++) {
			nextPart = item.slice(i * sizeThreshold, sizeThreshold);
			nextSubTask = new BasicPartialDataIoTask<>(
				originIndex, ioType, nextPart, srcPath, dstPath, credential, i, this
			);
			nextSubTask.srcPath(srcPath);
			subTasks.add(nextSubTask);
		}
		if(tailPartSize > 0) {
			nextPart = item.slice(equalPartsCount * sizeThreshold , tailPartSize);
			nextSubTask = new BasicPartialDataIoTask<>(
				originIndex, ioType, nextPart, srcPath, dstPath, credential, equalPartsCount, this
			);
			nextSubTask.srcPath(srcPath);
			subTasks.add(nextSubTask);
		}

		pendingSubTasksCount.set(subTasks.size());

		return subTasks;
	}

	@Override
	public final void markSubTaskCompleted() {
		pendingSubTasksCount.decrementAndGet();
	}

	@Override
	public final boolean allSubTasksDone() {
		return pendingSubTasksCount.get() == 0;
	}

	@Override @SuppressWarnings("unchecked")
	public final BasicCompositeDataIoTask<I> result() {
		buildItemPath(item, dstPath == null ? srcPath : dstPath);
		return new BasicCompositeDataIoTask<>(this);
	}

	@Override
	public void writeExternal(final ObjectOutput out)
	throws IOException {
		super.writeExternal(out);
		out.writeLong(sizeThreshold);
		out.writeInt(pendingSubTasksCount.get());
	}

	@Override
	public void readExternal(final ObjectInput in)
	throws IOException, ClassNotFoundException {
		super.readExternal(in);
		sizeThreshold = in.readLong();
		pendingSubTasksCount.set(in.readInt());
	}
}