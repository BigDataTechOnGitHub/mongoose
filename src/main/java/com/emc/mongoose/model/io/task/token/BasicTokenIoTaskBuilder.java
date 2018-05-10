package com.emc.mongoose.model.io.task.token;

import com.emc.mongoose.model.io.task.BasicIoTaskBuilder;
import com.emc.mongoose.model.item.TokenItem;
import com.emc.mongoose.model.storage.Credential;

import java.io.IOException;
import java.util.List;

/**
 Created by kurila on 14.07.16.
 */
public class BasicTokenIoTaskBuilder<I extends TokenItem, O extends TokenIoTask<I>>
extends BasicIoTaskBuilder<I, O>
implements TokenIoTaskBuilder<I, O> {

	public BasicTokenIoTaskBuilder(final int originIndex) {
		super(originIndex);
	}

	@Override @SuppressWarnings("unchecked")
	public O getInstance(final I item)
	throws IOException {
		final String uid;
		return (O) new BasicTokenIoTask<>(
			originIndex, ioType, item, Credential.getInstance(uid = getNextUid(), getNextSecret(uid))
		);
	}

	@Override @SuppressWarnings("unchecked")
	public void getInstances(final List<I> items, final List<O> buff)
	throws IOException {
		String uid;
		for(final I item : items) {
			buff.add(
				(O) new BasicTokenIoTask<>(
					originIndex, ioType, item,
					Credential.getInstance(uid = getNextUid(), getNextSecret(uid))
				)
			);
		}
	}
}