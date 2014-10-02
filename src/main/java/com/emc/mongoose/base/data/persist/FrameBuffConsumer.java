package com.emc.mongoose.base.data.persist;
//
import com.emc.mongoose.base.data.DataItem;
import com.emc.mongoose.util.remote.RecordFrameBuffer;
//
import org.apache.http.annotation.ThreadSafe;
//
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
/**
 Created by kurila on 25.06.14.
 A logging consumer which accumulates the data items until the accumulated data is externally taken.
 */
@ThreadSafe
public final class FrameBuffConsumer<T extends DataItem>
extends LogConsumer<T>
implements RecordFrameBuffer<T> {
	//
	private final ConcurrentLinkedQueue<T> buff = new ConcurrentLinkedQueue<>();
	private final Lock submLock = new ReentrantLock();
	//
	@Override
	public final void submit(final T data) {
		if(data!=null) {
			submLock.lock();
			buff.add(data);
			submLock.unlock();
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final ArrayList<T> takeFrame() {
		submLock.lock();
		final ArrayList<T> frame = new ArrayList<>(buff.size());
		frame.addAll(buff);
		buff.clear();
		submLock.unlock();
		return frame;
	}
	//
}
