package com.emc.mongoose.item.io;

import com.emc.mongoose.Constants;
import com.emc.mongoose.item.Item;
import com.emc.mongoose.item.ItemFactory;

import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;
import com.github.akurilov.commons.io.Input;
import org.apache.logging.log4j.Level;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 The data item input using CSV file containing the human-readable data item records as the source
 */
public class CsvItemInput<I extends Item>
implements Input<I> {
	
	protected BufferedReader itemsSrc;
	protected final ItemFactory<I> itemFactory;
	
	/**
	 @param in the input stream to get the data item records from
	 @param itemFactory the concrete item factory used to parse the records
	 @throws IOException
	 @throws NoSuchMethodException
	 */
	public CsvItemInput(final InputStream in, final ItemFactory<I> itemFactory)
	throws IOException, NoSuchMethodException {
		this(
			new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), Constants.MIB),
			itemFactory
		);
	}
	
	protected CsvItemInput(final BufferedReader itemsSrc, final ItemFactory<I> itemFactory) {
		setItemsSrc(itemsSrc);
		this.itemFactory = itemFactory;
	}
	
	protected final void setItemsSrc(final BufferedReader itemsSrc) {
		this.itemsSrc = itemsSrc;
	}
	
	@Override
	public long skip(final long itemsCount)
	throws IOException {
		long i = 0;
		while(i < itemsCount && null != itemsSrc.readLine()) {
			i ++;
		}
		return i;
	}
	
	@Override
	public I get()
	throws IOException {
		final String nextLine = itemsSrc.readLine();
		try {
			return nextLine == null ? null : itemFactory.getItem(nextLine);
		} catch(final IllegalArgumentException e) {
			LogUtil.trace(Loggers.ERR, Level.WARN, e, "Failed to build the item from the string \"{}\"", nextLine);
			return null;
		}
	}
	
	@Override
	public int get(final List<I> buffer, final int limit)
	throws IOException {
		int i = 0;
		String nextLine = null;
		try {
			while(i < limit) {
				nextLine = itemsSrc.readLine();
				if(nextLine == null) {
					if(i == 0) {
						throw new EOFException();
					} else {
						break;
					}
				}
				buffer.add(itemFactory.getItem(nextLine));
				i ++;
			}
		} catch(final IllegalArgumentException e) {
			LogUtil.trace(Loggers.ERR, Level.WARN, e, "Failed to build the item from the string \"{}\"", nextLine);
		}
		return i;
	}
	
	/**
	 Most probably will cause an IOException due to missing mark
	 @throws IOException
	 */
	@Override
	public void reset()
	throws IOException {
		itemsSrc.reset();
	}
	
	@Override
	public void close()
	throws IOException {
		itemsSrc.close();
	}
	
	@Override
	public String toString() {
		return "csvItemInput<" + itemsSrc + ">";
	}
}
