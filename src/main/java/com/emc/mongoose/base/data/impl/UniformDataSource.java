package com.emc.mongoose.base.data.impl;
//
import com.emc.mongoose.base.data.DataSource;
import com.emc.mongoose.base.load.LoadExecutor;
import com.emc.mongoose.run.Main;
import com.emc.mongoose.util.conf.RunTimeConfig;
import com.emc.mongoose.util.logging.ExceptionHandler;
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.annotation.ThreadSafe;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
/**
 Created by kurila on 23.07.14.
 A uniform data source for producing uniform data items.
 Implemented as finite buffer of pseudorandom bytes.
 */
@ThreadSafe
public class UniformDataSource<T extends UniformData>
implements DataSource<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	////////////////////////////////////////////////////////////////////////////////////////////////
	private final static int A = 21, B = 35, C = 4;
	//
	private long seed;
	private ByteBuffer dataSrcDirect, dataSrcReverse;
	////////////////////////////////////////////////////////////////////////////////////////////////
	public UniformDataSource()
	throws NumberFormatException {
		this(
			Long.parseLong(Main.RUN_TIME_CONFIG.getString("data.ring.seed"), 0x10),
			(int) Main.RUN_TIME_CONFIG.getSizeBytes("data.ring.size")
		);
	}
	//
	protected UniformDataSource(final long seed, final int size) {
		this.seed = seed;
		dataSrcDirect = ByteBuffer.allocate(size);
		preProduceData();
		final byte buff[] = ArrayUtils.clone(dataSrcDirect.array());
		ArrayUtils.reverse(buff);
		dataSrcReverse= ByteBuffer.wrap(buff);
	}
	//
	public static UniformDataSource DEFAULT = null;
	static {
		try {
			DEFAULT = new UniformDataSource();
			LOG.info(Markers.MSG, "Default data source: {}", DEFAULT.toString());
			/*DATA_SRC_UPDATE = new UniformDataSource(
				Long.reverse(
					nextWord(
						Long.reverseBytes(
							DEFAULT.seed
						)
					)
				),
				DEFAULT.getSize()
			);*/
		} catch(final Exception e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to create default data source");
		}
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	private synchronized void preProduceData() {
		final LongBuffer dataSrcWordsView = dataSrcDirect.asLongBuffer();
		final int
			size = dataSrcDirect.array().length,
			countWordBytes = Long.SIZE / Byte.SIZE,
			countWords = size / countWordBytes,
			countTailBytes = size % countWordBytes;
		long word = seed;
		int i;
		double d = System.nanoTime();
		LOG.debug(Markers.MSG, "Prepare {} of ring data...", RunTimeConfig.formatSize(size));
		// 64-bit words
		for(i = 0; i < countWords; i++) {
			dataSrcWordsView.put(i, word);
			word = nextWord(word);
		}
		// tail bytes
		final ByteBuffer tailBytes = ByteBuffer.allocate(countWordBytes);
		tailBytes.asLongBuffer().put(word).rewind();
		for(i = 0; i < countTailBytes; i++) {
			dataSrcDirect.put(countWordBytes * countWords + i, tailBytes.get(i));
		}
		//
		LOG.debug(
			Markers.MSG, "Pre-generating the data done in {} seconds",
			(System.nanoTime() - d) / LoadExecutor.BILLION
		);
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// See for details: http://xorshift.di.unimi.it/murmurhash3.c //////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	public static long nextWord(long word) {
		word ^= (word << A);
		word ^= (word >>> B);
		word ^= (word << C);
		return word;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Binary serialization implementation /////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final void writeExternal(final ObjectOutput out)
	throws IOException {
		out.writeInt(dataSrcDirect.array().length);
		out.writeLong(seed);
	}
	//
	@Override
	public final void readExternal(final ObjectInput in)
		throws IOException, ClassNotFoundException {
		dataSrcDirect = ByteBuffer.allocate(in.readInt());
		seed = in.readLong();
		preProduceData();
		DEFAULT = this;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final int getSize() {
		return dataSrcDirect.array().length;
	}
	//
	public final long getSeed() {
		return seed;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Human readable "serialization" implementation ///////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final String toString() {
		return
			Long.toHexString(seed) + RunTimeConfig.LIST_SEP +
			Integer.toHexString(dataSrcDirect.array().length);
	}
	//
	@Override
	public void fromString(final String metaInfo)
		throws IllegalArgumentException, IOException {
		final String values[] = metaInfo.split(RunTimeConfig.LIST_SEP);
		if(values.length==2) {
			DEFAULT = new UniformDataSource(
				Long.parseLong(values[0], 0x10), Integer.parseInt(values[1], 0x10)
			);
		} else {
			throw new IllegalArgumentException();
		}
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	public final byte[] getBytes(final int layerNum) {
		return layerNum % 2 == 0 ? dataSrcDirect.array() : dataSrcReverse.array();
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
}
