package com.emc.mongoose.api.model.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 Created by andrey on 24.07.17.
 */
public final class ExternalDataInput
extends CachedDataInput {

	public ExternalDataInput() {
		super();
	}

	public ExternalDataInput(
		final ReadableByteChannel initialLayerInputChannel, final int layerSize,
		final int layersCacheCountLimit
	) throws IOException {
		super((MappedByteBuffer) ByteBuffer.allocateDirect(layerSize), layersCacheCountLimit);
		// read the data from the channel to the inputBuff which is already initialized with the
		// parent constructor invocation
		int n = 0, m;
		do {
			m = initialLayerInputChannel.read(inputBuff);
			if(m < 0) {
				break;
			} else {
				n += m;
			}
		} while(n < layerSize);
		inputBuff.flip();
	}
}
