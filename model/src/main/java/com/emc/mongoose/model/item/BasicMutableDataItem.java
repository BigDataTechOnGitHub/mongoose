package com.emc.mongoose.model.item;

import com.emc.mongoose.model.data.ContentSource;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.BitSet;

import static com.emc.mongoose.model.item.MutableDataItem.getRangeOffset;
import static java.lang.Math.min;

public class BasicMutableDataItem
extends BasicDataItem
implements MutableDataItem {
	//
	private static final char LAYER_MASK_SEP = '/';
	//
	protected static final String
		FMT_MSG_MASK = "Ranges mask is not correct hexadecimal value: %s",
		STR_EMPTY_MASK = "0";
	////////////////////////////////////////////////////////////////////////////////////////////////
	public final BitSet maskRangesRead = new BitSet(Long.SIZE);
	////////////////////////////////////////////////////////////////////////////////////////////////
	public BasicMutableDataItem() {
		super();
	}
	//
	public BasicMutableDataItem(final ContentSource contentSrc) {
		super(contentSrc); // ranges remain uninitialized
	}
	//
	public BasicMutableDataItem(final String value, final ContentSource contentSrc) {
		super(value.substring(0, value.lastIndexOf(",")), contentSrc);
		//
		final String rangesInfo = value.substring(value.lastIndexOf(",") + 1, value.length());
		final int sepPos = rangesInfo.indexOf(LAYER_MASK_SEP, 0);
		try {
			// extract hexadecimal layer number
			layerNum = Integer.parseInt(rangesInfo.substring(0, sepPos), 0x10);
			// extract hexadecimal mask, convert into bit set and add to the existing mask
			final String rangesMask = rangesInfo.substring(sepPos + 1, rangesInfo.length());
			final char rangesMaskChars[];
			if(rangesMask.length() == 0) {
				rangesMaskChars = ("00" + rangesMask).toCharArray();
			} else if(rangesMask.length() % 2 == 1) {
				rangesMaskChars = ("0" + rangesMask).toCharArray();
			} else {
				rangesMaskChars = rangesMask.toCharArray();
			}
			// method "or" to merge w/ the existing mask
			maskRangesRead.or(BitSet.valueOf(Hex.decodeHex(rangesMaskChars)));
		} catch(final DecoderException | NumberFormatException e) {
			throw new IllegalArgumentException(String.format(FMT_MSG_MASK, rangesInfo));
		}
	}
	//
	public BasicMutableDataItem(
		final long offset, final long size, final ContentSource contentSrc
	) {
		super(offset, size, contentSrc);
	}
	//
	public BasicMutableDataItem(
		final String name, final long offset, final long size, final ContentSource contentSrc
	) {
		super(name, offset, size, 0, contentSrc);
	}
	//
	public BasicMutableDataItem(
		final String name, final long offset, final long size, final int layerNum,
		final ContentSource contentSrc
	) {
		super(name, offset, size, layerNum, contentSrc);
	}
	
	@Override
	public BasicMutableDataItem slice(final long from, final long partSize) {
		if(from < 0) {
			throw new IllegalArgumentException();
		}
		if(partSize < 1) {
			throw new IllegalArgumentException();
		}
		if(from + partSize > size) {
			throw new IllegalArgumentException();
		}
		return new BasicMutableDataItem(name, offset + from, partSize, layerNum, getContentSrc());
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Human readable "serialization" implementation ///////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	private static final ThreadLocal<StringBuilder> STRB = new ThreadLocal<StringBuilder>() {
		@Override
		protected final StringBuilder initialValue() {
			return new StringBuilder();
		}
	};

	@Override
	public String toString() {
		final StringBuilder strb = STRB.get();
		strb.setLength(0); // reset
		return strb
			.append(super.toString()).append(',')
			.append(Integer.toHexString(layerNum)).append('/')
			.append(
				maskRangesRead.isEmpty() ?
					STR_EMPTY_MASK : Hex.encodeHexString(maskRangesRead.toByteArray())
			).toString();
	}

	@Override
	public String toString(final String itemPath) {
		final StringBuilder strb = STRB.get();
		strb.setLength(0); // reset
		return strb
			.append(super.toString(itemPath)).append(',')
			.append(Integer.toHexString(layerNum)).append('/')
			.append(
				maskRangesRead.isEmpty() ?
				STR_EMPTY_MASK : Hex.encodeHexString(maskRangesRead.toByteArray())
			).toString();
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public boolean equals(final Object o) {
		if(o == this) {
			return true;
		}
		if(!(o instanceof BasicMutableDataItem) || !super.equals(o)) {
			return false;
		} else {
			final BasicMutableDataItem other = (BasicMutableDataItem) o;
			return maskRangesRead.equals(other.maskRangesRead);
		}
	}
	//
	@Override
	public int hashCode() {
		return super.hashCode() ^ maskRangesRead.hashCode();
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	/*public static int log2(long value) {
		int result = 0;
		if((value &  0xffffffff00000000L ) != 0	) { value >>>= 32;	result += 32; }
		if( value >= 0x10000					) { value >>>= 16;	result += 16; }
		if( value >= 0x1000						) { value >>>= 12;	result += 12; }
		if( value >= 0x100						) { value >>>= 8;	result += 8; }
		if( value >= 0x10						) { value >>>= 4;	result += 4; }
		if( value >= 0x4						) { value >>>= 2;	result += 2; }
		return result + (int) (value >>> 1);
	}*/
	//
	@Override
	public final long getRangeSize(final int i) {
		return min(getRangeOffset(i + 1), size) - getRangeOffset(i);
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////
	// UPDATE //////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public final boolean isUpdated() {
		return !maskRangesRead.isEmpty();
	}
	
	@Override
	public final void commitUpdatedRanges(final BitSet[] updatingRangesMaskPair) {
		if(updatingRangesMaskPair[1].isEmpty()) {
			maskRangesRead.or(updatingRangesMaskPair[0]);
		} else {
			maskRangesRead.clear();
			maskRangesRead.or(updatingRangesMaskPair[1]);
			layerNum ++;
		}
	}

	@Override
	public final boolean isRangeUpdated(final int rangeIdx) {
		return maskRangesRead.get(rangeIdx);
	}

	@Override
	public final int getUpdatedRangesCount() {
		return maskRangesRead.cardinality();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// UPDATE //////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public void writeExternal(final ObjectOutput out)
	throws IOException {
		super.writeExternal(out);
		final byte buff[] = maskRangesRead.toByteArray();
		out.writeInt(buff.length);
		out.write(buff);
	}
	
	@Override
	public void readExternal(final ObjectInput in)
	throws IOException, ClassNotFoundException {
		super.readExternal(in);
		final int len = in.readInt();
		final byte buff[] = new byte[len];
		in.readFully(buff);
		maskRangesRead.or(BitSet.valueOf(buff));
	}
}
