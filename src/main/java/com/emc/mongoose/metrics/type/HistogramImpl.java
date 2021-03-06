package com.emc.mongoose.metrics.type;

import com.emc.mongoose.metrics.snapshot.HistogramSnapshot;
import com.emc.mongoose.metrics.snapshot.HistogramSnapshotImpl;
import com.emc.mongoose.metrics.util.LongReservoir;

import java.util.concurrent.atomic.LongAdder;

/**
 @author veronika K. on 01.10.18 */
public class HistogramImpl
implements LongMeter<HistogramSnapshot> {

	private final LongReservoir reservoir;
	private final LongAdder count;

	public HistogramImpl(final LongReservoir reservoir) {
		this.reservoir = reservoir;
		this.count = new LongAdder();
	}

	@Override
	public void update(final long value) {
		count.increment();
		reservoir.update(value);
	}

	@Override
	public HistogramSnapshotImpl snapshot() {
		return new HistogramSnapshotImpl(reservoir.snapshot());
	}
}
