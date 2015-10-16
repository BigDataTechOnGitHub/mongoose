package com.emc.mongoose.core.impl.data.model;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.content.ContentSource;
import com.emc.mongoose.core.api.data.model.FileDataItemDst;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
/**
 Created by kurila on 30.06.15.
 */
public class CSVFileItemDst<T extends DataItem>
extends CSVItemDst<T>
implements FileDataItemDst<T> {
	//
	protected Path itemsFilePath;
	//
	public CSVFileItemDst(
		final Path itemsFilePath, final Class<? extends T> itemCls, final ContentSource contentSrc
	) throws IOException {
		super(
			Files.newOutputStream(itemsFilePath, StandardOpenOption.WRITE),
			itemCls, contentSrc
		);
		this.itemsFilePath = itemsFilePath;
	}
	//
	public CSVFileItemDst(final Class<? extends T> itemCls, final ContentSource contentSrc)
	throws IOException {
		this(Files.createTempFile(null, ".csv"), itemCls, contentSrc);
		this.itemsFilePath.toFile().deleteOnExit();
	}
	//
	@Override
	public CSVFileItemSrc<T> getDataItemSrc()
	throws IOException {
		try {
			return new CSVFileItemSrc<>(itemsFilePath, itemCls, contentSrc);
		} catch(final NoSuchMethodException e) {
			throw new IOException(e);
		}
	}
	//
	@Override
	public String toString() {
		return "csvFileItemOutput<" + itemsFilePath.getFileName() + ">";
	}
	//
	@Override
	public final Path getFilePath() {
		return itemsFilePath;
	}
	//
	@Override
	public final void delete()
	throws IOException {
		Files.delete(itemsFilePath);
	}
}
