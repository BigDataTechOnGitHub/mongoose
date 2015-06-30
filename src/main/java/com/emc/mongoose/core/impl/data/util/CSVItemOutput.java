package com.emc.mongoose.core.impl.data.util;
//
import com.emc.mongoose.core.api.data.DataItem;
//
import com.emc.mongoose.core.api.data.util.DataItemOutput;
//
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
/**
 The data item output writing into the specified file human-readable data item records using the CSV
 format
 */
public abstract class CSVItemOutput<T extends DataItem>
implements DataItemOutput<T> {
	//
	protected final Class<T> itemCls;
	protected final BufferedWriter itemsDst;
	//
	protected CSVItemOutput(final OutputStream out, final Class<T> itemCls)
	throws IOException {
		itemsDst = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
		this.itemCls = itemCls;
	}
	//
	@Override
	public void write(final T dataItem)
	throws IOException {
		itemsDst.write(dataItem.toString());
		itemsDst.newLine();
	}
	//
	@Override
	public int write(final List<T> buffer)
	throws IOException {
		for(final T nextItem : buffer) {
			write(nextItem);
		}
		return buffer.size();
	}
	//
	@Override
	public void close()
	throws IOException {
		itemsDst.close();
	}
}
