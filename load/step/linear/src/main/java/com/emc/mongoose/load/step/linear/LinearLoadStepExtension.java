package com.emc.mongoose.load.step.linear;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.env.ExtensionBase;
import com.emc.mongoose.load.step.LoadStepFactory;
import com.emc.mongoose.metrics.MetricsManager;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.SchemaProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LinearLoadStepExtension<T extends LinearLoadStepLocal, U extends LinearLoadStepClient>
extends ExtensionBase
implements LoadStepFactory<T, U> {

	public static final String TYPE = "Load";

	private static final List<String> RES_INSTALL_FILES = Collections.unmodifiableList(
		Arrays.asList(
		)
	);

	@Override
	public final String id() {
		return TYPE;
	}

	@Override @SuppressWarnings("unchecked")
	public final T createLocal(
		final Config baseConfig, final List<Extension> extensions, final List<Config> contextConfigs,
		final MetricsManager metricsManager
	) {
		return (T) new LinearLoadStepLocal(baseConfig, extensions, contextConfigs, metricsManager);
	}

	@Override @SuppressWarnings("unchecked")
	public final U createClient(
		final Config baseConfig, final List<Extension> extensions, final MetricsManager metricsManager
	) {
		return (U) new LinearLoadStepClient(baseConfig, extensions, null, metricsManager);
	}

	@Override
	public final SchemaProvider schemaProvider() {
		return null;
	}

	@Override
	protected final String defaultsFileName() {
		return null;
	}

	@Override
	protected final List<String> resourceFilesToInstall() {
		return RES_INSTALL_FILES;
	}
}
