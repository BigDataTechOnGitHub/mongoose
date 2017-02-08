package com.emc.mongoose.model.item;

import com.emc.mongoose.model.data.ContentSource;

/**
 Created by kurila on 28.03.16.
 */
public enum ItemType {
	
	DATA,
	PATH,
	TOKEN;
	
	public static ItemFactory getItemFactory(final ItemType itemType, final ContentSource contentSrc) {
		if(ItemType.DATA.equals(itemType)) {
			return new BasicDataItemFactory(contentSrc);
		} else if(ItemType.PATH.equals(itemType)) {
			return new BasicPathItemFactory();
		} else if(ItemType.TOKEN.equals(itemType)) {
			return new BasicTokenItemFactory();
		} else {
			throw new AssertionError("Item type \"" + itemType + "\" is not supported");
		}
	}
}
