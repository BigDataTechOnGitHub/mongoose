package com.emc.mongoose.load.step.client;

import com.emc.mongoose.exception.InterruptRunException;
import com.emc.mongoose.item.Item;
import static com.emc.mongoose.load.step.client.LoadStepClient.OUTPUT_PROGRESS_PERIOD_MILLIS;
import com.emc.mongoose.load.step.file.FileManager;
import com.emc.mongoose.load.step.service.file.FileManagerService;
import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;

import com.github.akurilov.commons.io.Input;

import com.github.akurilov.confuse.Config;

import org.apache.logging.log4j.Level;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ItemInputFileSlicer
implements AutoCloseable {

	private static final int APPROX_LINE_LENGTH = 0x40;

	private final String loadStepId;
	private final Map<FileManager, String> itemInputFileSlices;
	private final List<FileManager> fileMgrs;

	public <I extends Item> ItemInputFileSlicer(
		final String loadStepId, final List<FileManager> fileMgrs, final List<Config> configSlices,
		final Input<I> itemInput, final int batchSize
	) throws InterruptRunException {
		this.loadStepId = loadStepId;
		final int sliceCount = configSlices.size();
		itemInputFileSlices = new HashMap<>(sliceCount);
		this.fileMgrs = fileMgrs;
		for(int i = 0; i < sliceCount; i ++) {
			try {
				final FileManager fileMgr = fileMgrs.get(i);
				final String itemInputFileName = fileMgr.newTmpFileName();
				itemInputFileSlices.put(fileMgr, itemInputFileName);
				final Config configSlice = configSlices.get(i);
				configSlice.val("item-input-file", itemInputFileName);
			} catch(final Exception e) {
				LogUtil.exception(Level.ERROR, e, "Failed to get the item input file name for the step slice #" + i);
			}
		}

		try {
			Loggers.MSG.info("{}: scatter the items from the input \"{}\"...", loadStepId, itemInput);
			scatterItems(itemInput, batchSize);
		} catch(final IOException e) {
			LogUtil.exception(Level.WARN, e, "{}: failed to use the item input", loadStepId);
		} catch(final InterruptRunException e) {
			throw e;
		} catch(final Throwable cause) {
			LogUtil.exception(Level.ERROR, cause, "{}: unexpected failure", loadStepId);
		}
	}

	@Override
	public final void close() {
		itemInputFileSlices
			.entrySet()
			.parallelStream()
			.forEach(
				entry -> {
					final FileManager fileMgr = entry.getKey();
					final String itemInputFileName = entry.getValue();
					try {
						fileMgr.deleteFile(itemInputFileName);
					} catch(final Exception e) {
						LogUtil.exception(
							Level.WARN, e, "{}: failed to delete the file \"{}\" @ file manager \"{}\"", loadStepId,
							itemInputFileName, fileMgr
						);
					}
				}
			);
		itemInputFileSlices.clear();
	}

	private <I extends Item> void scatterItems(final Input<I> itemInput, final int batchSize)
	throws InterruptRunException, IOException {

		Loggers.MSG.info("{}: slice the item input \"{}\"...", loadStepId, itemInput);

		final Map<FileManager, ByteArrayOutputStream> itemsOutByteBuffs = fileMgrs
			.stream()
			.collect(
				Collectors.toMap(
					Function.identity(), fileMgr -> new ByteArrayOutputStream(batchSize * APPROX_LINE_LENGTH)
				)
			);

		final Map<FileManager, ObjectOutputStream> itemsOutputs = itemsOutByteBuffs
			.entrySet()
			.stream()
			.collect(
				Collectors.toMap(
					Map.Entry::getKey,
					entry -> {
						try {
							return new ObjectOutputStream(entry.getValue());
						} catch(final IOException ignored) {
						}
						return null;
					}
				)
			);

		transferData(itemInput, itemsOutByteBuffs, itemsOutputs, batchSize);

		itemsOutputs
			.values()
			.parallelStream()
			.filter(Objects::nonNull)
			.forEach(
				outStream -> {
					try {
						outStream.close();
					} catch(final IOException ignored) {
					}
				}
			);
	}

	private <I extends Item> void transferData(
		final Input<I> itemInput, final Map<FileManager, ByteArrayOutputStream> itemsOutByteBuffs,
		final Map<FileManager, ObjectOutputStream> itemsOutputs, final int batchSize
	) throws InterruptRunException, IOException {

		final int sliceCount = itemsOutByteBuffs.size();
		final List<I> itemsBuff = new ArrayList<>(batchSize);

		int n;
		long count = 0;
		long lastProgressOutputTimeMillis = System.currentTimeMillis();

		Loggers.MSG.info(
			"Items input \"{}\": starting to distribute the items among the {} load step slices", itemInput, sliceCount
		);

		while(true) {

			// get the next batch of items
			try {
				n = itemInput.get(itemsBuff, batchSize);
			} catch(final EOFException e) {
				break;
			}

			if(n > 0) {

				// distribute the items using the round robin
				for(int i = 0; i < n; i ++) {
					itemsOutputs
						.get(fileMgrs.get(i % sliceCount))
						.writeUnshared(itemsBuff.get(i));
				}

				itemsBuff.clear();

				// write the text items data to the remote input files
				fileMgrs
					.parallelStream()
					.forEach(
						fileMgr -> {
							final ByteArrayOutputStream buff = itemsOutByteBuffs.get(fileMgr);
							final String itemInputFileName = itemInputFileSlices.get(fileMgr);
							try {
								final byte[] data = buff.toByteArray();
								fileMgr.writeToFile(itemInputFileName, data);
								buff.reset();
							} catch(final IOException e) {
								LogUtil.exception(
									Level.WARN, e, "Failed to write the items input data to the {} file \"{}\"",
									itemInputFileName, (fileMgr instanceof FileManagerService ? "remote" : "local")
								);
							}
						}
					);

				count += n;

				if(System.currentTimeMillis() - lastProgressOutputTimeMillis > OUTPUT_PROGRESS_PERIOD_MILLIS) {
					Loggers.MSG.info("Transferred {} items from the input \"{}\"...", count, itemInput);
					lastProgressOutputTimeMillis = System.currentTimeMillis();
				}

			} else {
				break;
			}
		}

		Loggers.MSG.info(
			"Items input \"{}\": {} items was distributed among the {} load step slices", itemInput, count, sliceCount
		);
	}
}
