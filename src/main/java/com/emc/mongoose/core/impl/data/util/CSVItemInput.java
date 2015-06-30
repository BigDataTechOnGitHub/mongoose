package com.emc.mongoose.core.impl.data.util;
//
import com.emc.mongoose.common.conf.Constants;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.util.DataItemInput;
//
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
/**
 The data item input using CSV file containing the human-readable data item records as the source
 */
public class CSVItemInput<T extends DataItem>
implements DataItemInput<T> {
	//
	protected final BufferedReader itemsSrc;
	protected final Constructor<T> itemConstructor;
	/**
	 @param in the input stream to read the data item records from
	 @param itemCls the particular data item implementation class used to parse the records
	 @throws IOException
	 @throws NoSuchMethodException
	 */
	public CSVItemInput(final InputStream in, final Class<T> itemCls)
	throws IOException, NoSuchMethodException {
		itemsSrc = new BufferedReader(
			new InputStreamReader(in, StandardCharsets.UTF_8), Constants.BUFF_SIZE_LO
		);
		itemConstructor = itemCls.getConstructor(String.class);
	}
	//
	@Override
	public T read()
	throws IOException {
		final String nextLine = itemsSrc.readLine();
		T nextItem = null;
		if(nextLine != null) {
			try {
				nextItem = itemConstructor.newInstance(nextLine);
			} catch(
				final InstantiationException | IllegalAccessException | InvocationTargetException e
			) {
				throw new IOException(e);
			}
		}
		return nextItem;
	}
	//
	@Override
	public int read(final List<T> buffer)
	throws IOException {
		String nextLine;
		T nextItem;
		final int n = buffer.size();
		do {
			nextLine = itemsSrc.readLine();
			if(nextLine == null) {
				break;
			}
			try {
				nextItem = itemConstructor.newInstance(nextLine);
			} catch(
				final InstantiationException | IllegalAccessException | InvocationTargetException e
			) {
				throw new IOException(e);
			}
		} while(buffer.add(nextItem));
		return buffer.size() - n;
	}
	//
	@Override
	public void reset()
	throws IOException {
		itemsSrc.reset();
	}
	//
	@Override
	public void close()
	throws IOException {
		itemsSrc.close();
	}
}
