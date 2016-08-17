package com.emc.mongoose.model.impl.io.task;

import com.emc.mongoose.model.api.io.task.IoTaskFactory;
import com.emc.mongoose.model.api.item.DataItem;
import com.emc.mongoose.model.util.LoadType;

import java.io.IOException;

/**
 Created by kurila on 14.07.16.
 */
public final class BasicDataIoTaskFactory<D extends DataItem>
implements IoTaskFactory<D, BasicDataIoTask<D>> {

	@Override
	public final BasicDataIoTask<D> getInstance(
		final LoadType ioType, final D dataItem, final String dstPath
	) {
		try {
			return new BasicDataIoTask<>(ioType, dataItem, dstPath);
		} catch(final IOException ignored) {
			return null;
		}
	}
}
