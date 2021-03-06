package com.emc.mongoose.storage.driver.coop.netty.http.s3;

import com.emc.mongoose.data.DataInput;
import com.emc.mongoose.env.ExtensionBase;
import com.emc.mongoose.exception.OmgShootMyFootException;
import com.emc.mongoose.item.Item;
import com.emc.mongoose.item.op.Operation;
import com.emc.mongoose.storage.driver.StorageDriverFactory;
import static com.emc.mongoose.Constants.APP_NAME;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.SchemaProvider;
import com.github.akurilov.confuse.io.json.JsonSchemaProviderBase;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AmzS3StorageDriverExtension<
	I extends Item, O extends Operation<I>, T extends AmzS3StorageDriver<I,O>
>
extends ExtensionBase
implements StorageDriverFactory<I, O, T> {

	private static final String NAME = "s3";
	private static final String DEFAULTS_FILE_NAME = "defaults-storage-s3.json";
	private static final List<String> RES_INSTALL_FILES = Collections.unmodifiableList(
		Arrays.asList(
			"config/" + DEFAULTS_FILE_NAME
		)
	);

	@Override
	public String id() {
		return NAME;
	}

	@Override
	public T create(
		final String stepId, final DataInput dataInput, final Config storageConfig, final boolean verifyFlag,
		final int batchSize
	) throws OmgShootMyFootException, InterruptedException {
		return (T) new AmzS3StorageDriver<>(stepId, dataInput, storageConfig, verifyFlag, batchSize);
	}

	@Override
	public final SchemaProvider schemaProvider() {
		return new JsonSchemaProviderBase() {
			@Override
			protected final InputStream schemaInputStream() {
				return getClass().getResourceAsStream("/config-schema-storage-s3.json");
			}

			@Override
			public final String id() {
				return APP_NAME;
			}
		};
	}

	@Override
	protected final String defaultsFileName() {
		return DEFAULTS_FILE_NAME;
	}

	@Override
	protected final List<String> resourceFilesToInstall() {
		return RES_INSTALL_FILES;
	}
}
