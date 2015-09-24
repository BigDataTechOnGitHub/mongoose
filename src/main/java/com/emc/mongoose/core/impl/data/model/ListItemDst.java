package com.emc.mongoose.core.impl.data.model;
//
import com.emc.mongoose.core.api.data.DataItem;
//
import com.emc.mongoose.core.api.data.model.DataItemDst;
//
import java.io.IOException;
import java.util.List;
/**
 Created by kurila on 18.06.15.
 Writable collection of the data items.
 */
public class ListItemDst<T extends DataItem>
implements DataItemDst<T> {
	//
	protected final List<T> items;
	//
	public ListItemDst(final List<T> items) {
		this.items = items;
	}

	/**
	 @param dataItem the data item to put
	 @throws IOException if the destination collection fails to add the data item
	 (due to capacity reasons for example)
	 */
	@Override
	public void put(final T dataItem)
	throws IOException {
		if(!items.add(dataItem)) {
			throw new IOException("Failed to add the data item to the destination collection");
		}
	}
	/**
	 Bulk put of the data items from the specified buffer
	 @param buffer the buffer containing the data items to put
	 @return the count of the data items which have been written successfully
	 @throws IOException doesn't throw
	 */
	@Override
	public int put(final List<T> buffer, final int from, final int to)
	throws IOException {
		items.addAll(buffer.subList(from, to));
		return to - from;
	}

	//
	@Override
	public final int put(final List<T> items)
	throws IOException {
		return put(items, 0, items.size());
	}

	/**
	 @return the corresponding input
	 @throws IOException doesn't throw
	 */
	@Override
	public ListItemSrc<T> getDataItemSrc()
	throws IOException {
		return new ListItemSrc<>(items);
	}

	/**
	 does nothing
	 @throws IOException doesn't throw
	 */
	@Override
	public void close()
	throws IOException {
	}

	//
	@Override
	public String toString() {
		return "listItemOutput<" + items.hashCode() + ">";
	}

	//
	@Override
	protected void finalize()
	throws Throwable {
		items.clear();
		super.finalize();
	}
}
