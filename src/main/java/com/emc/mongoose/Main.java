package com.emc.mongoose;

import com.emc.mongoose.concurrent.ServiceTaskExecutor;
import com.emc.mongoose.config.AliasingUtil;
import com.emc.mongoose.config.CliArgUtil;
import com.emc.mongoose.config.ConfigUtil;
import com.emc.mongoose.config.IllegalArgumentNameException;
import com.emc.mongoose.env.CoreResourcesToInstall;
import com.emc.mongoose.env.Extension;
import com.emc.mongoose.exception.InterruptRunException;
import com.emc.mongoose.load.step.ScriptEngineUtil;
import com.emc.mongoose.load.step.service.LoadStepManagerServiceImpl;
import com.emc.mongoose.load.step.service.file.FileManagerServiceImpl;
import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;
import com.emc.mongoose.metrics.MetricsManager;
import com.emc.mongoose.metrics.MetricsManagerImpl;
import com.emc.mongoose.svc.Service;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.SchemaProvider;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import io.prometheus.client.exporter.MetricsServlet;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.emc.mongoose.Constants.APP_NAME;
import static com.emc.mongoose.Constants.DIR_EXAMPLE_SCENARIO;
import static com.emc.mongoose.Constants.DIR_EXT;
import static com.emc.mongoose.Constants.PATH_DEFAULTS;
import static com.emc.mongoose.config.CliArgUtil.allCliArgs;
import static com.emc.mongoose.load.step.Constants.ATTR_CONFIG;
import static javax.script.ScriptContext.ENGINE_SCOPE;

public final class Main {

	public static void main(final String... args)
	throws Exception {
		final CoreResourcesToInstall coreResources = new CoreResourcesToInstall();
		final Path appHomePath = coreResources.appHomePath();
		final String initialStepId = "none-" + LogUtil.getDateTimeStamp();
		LogUtil.init(appHomePath.toString());
		try {
			// install the core resources
			coreResources.install(appHomePath);
			// load the defaults
			final Config defaultConfig = loadDefaultConfig(appHomePath);
			// extensions
			try(
				final URLClassLoader extClsLoader = Extension.extClassLoader(
					Paths.get(appHomePath.toString(), DIR_EXT).toFile()
				)
			) {
				final List<Extension> extensions = Extension.load(extClsLoader);
				// install the extensions
				installExtensions(extensions, appHomePath);
				final Config configWithArgs;
				try {
					// apply the extensions defaults
					final Config config = collectDefaults(extensions, defaultConfig, appHomePath);
					// parse the CLI args and apply them to the config instance
					configWithArgs = applyArgsToConfig(args, config, initialStepId);
				} catch(final Exception e) {
					LogUtil.exception(Level.ERROR, e, "Failed to load the defaults");
					throw e;
				}
				// init the metrics manager
				final MetricsManager metricsMgr = new MetricsManagerImpl(ServiceTaskExecutor.INSTANCE);
				if(configWithArgs.boolVal("run-node")) {
					runNode(configWithArgs, extensions, metricsMgr);
				} else {
					final int port = configWithArgs.intVal("run-port");
					final Server server = new Server(port);
					addMetricsService(server);
					server.start();
					try {
						runScenario(configWithArgs, extensions, extClsLoader, metricsMgr, appHomePath);
					} finally {
						server.stop();
					}
				}
			}
		} catch(final InterruptedException | InterruptRunException e) {
			Loggers.MSG.debug("Interrupted", e);
		} catch(final Exception e) {
			LogUtil.exception(Level.FATAL, e, "Unexpected failure");
			e.printStackTrace();
		}
	}

	private static Config loadDefaultConfig(final Path appHomePath)
	throws Exception {
		final Map<String, Object> mainConfigSchema = SchemaProvider
			.resolve(APP_NAME, Thread.currentThread().getContextClassLoader())
			.stream()
			.findFirst()
			.orElseThrow(IllegalStateException::new);
		// load the defaults
		return ConfigUtil.loadConfig(Paths.get(appHomePath.toString(), PATH_DEFAULTS).toFile(), mainConfigSchema);
	}

	private static void installExtensions(final List<Extension> extensions, final Path appHomePath) {
		final StringBuilder availExtMsg = new StringBuilder("Available/installed extensions:\n");
		extensions.forEach(
			ext -> {
				ext.install(appHomePath);
				final String extId = ext.id();
				final String extFqcn = ext.getClass().getCanonicalName();
				availExtMsg
					.append('\t')
					.append(extId)
					.append(' ')
					.append(StringUtils.repeat("-", extId.length() < 30 ? 30 - extId.length() : 1))
					.append("> ")
					.append(extFqcn).append('\n');
			}
		);
		Loggers.MSG.info(availExtMsg);
	}

	private static Config collectDefaults(
		final List<Extension> extensions, final Config mainDefaults, final Path appHomePath
	) throws Exception {
		final List<Config> allDefaults = extensions
			.stream()
			.map(ext -> ext.defaults(appHomePath))
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		allDefaults.add(mainDefaults);
		return ConfigUtil.merge(mainDefaults.pathSep(), allDefaults);
	}

	private static Config applyArgsToConfig(
		final String[] args, final Config config, final String initialStepId
	) {
		try {
			final Map<String, String> parsedArgs = CliArgUtil.parseArgs(args);
			final List<Map<String, Object>> aliasingConfig = config.listVal("aliasing");
			final Map<String, String> aliasedArgs = AliasingUtil.apply(parsedArgs, aliasingConfig);
			aliasedArgs.forEach(config::val);
		} catch(final IllegalArgumentNameException e) {
			final String formattedAllCliArgs = allCliArgs(config.schema(), config.pathSep())
				.stream()
				.collect(Collectors.joining("\n", "\t", ""));
			Loggers.ERR.fatal(
				"Invalid argument: \"{}\"\nThe list of all possible args:\n{}", e.getMessage(),
				formattedAllCliArgs
			);
		} catch(final InvalidValuePathException e) {
			Loggers.ERR.fatal("Invalid configuration option: \"{}\"", e.path());
		} catch(final InvalidValueTypeException e) {
			Loggers.ERR.fatal(
				"Invalid configuration value type for the option \"{}\", expected: {}, " + "actual: {}",
				e.path(), e.expectedType(), e.actualType()
			);
		}
		if(null == config.val("load-step-id")) {
			config.val("load-step-id", initialStepId);
			config.val("load-step-idAutoGenerated", true);
		}
		Arrays.stream(args).forEach(Loggers.CLI::info);
		Loggers.CONFIG.info(ConfigUtil.toString(config));
		return config;
	}

	private static void addMetricsService(final Server server) {
		final ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
	}

	private static void runNode(final Config config, final List<Extension> extensions, final MetricsManager metricsMgr)
	throws InterruptRunException, InterruptedException {
		final int listenPort = config.intVal("load-step-node-port");
		try(
			final Service fileMgrSvc = new FileManagerServiceImpl(listenPort);
			final Service scenarioStepSvc = new LoadStepManagerServiceImpl(listenPort, extensions, metricsMgr)
		) {
			fileMgrSvc.start();
			scenarioStepSvc.start();
			scenarioStepSvc.await();
		} catch(final InterruptedException | InterruptRunException e) {
			throw e;
		} catch(final Throwable cause) {
			cause.printStackTrace(System.err);
		}
	}

	@SuppressWarnings("StringBufferWithoutInitialCapacity")
	private static void runScenario(
		final Config config, final List<Extension> extensions, final ClassLoader extClsLoader,
		final MetricsManager metricsMgr, final Path appHomePath
	) {
		// get the scenario file/path
		final Path scenarioPath;
		final String scenarioFile = config.stringVal("run-scenario");
		if(scenarioFile != null && ! scenarioFile.isEmpty()) {
			scenarioPath = Paths.get(scenarioFile);
		} else {
			scenarioPath = Paths.get(appHomePath.toString(), DIR_EXAMPLE_SCENARIO, "js", "default.js");
		}
		final StringBuilder strb = new StringBuilder();
		try {
			Files
				.lines(scenarioPath)
				.forEach(line -> strb.append(line).append(System.lineSeparator()));
		} catch(final IOException e) {
			LogUtil.exception(Level.FATAL, e, "Failed to read the scenario file \"{}\"", scenarioPath);
			try {
				Files
					.list(scenarioPath.getParent())
					.forEach(System.out::println);
			} catch(final IOException ee) {
				ee.printStackTrace(System.err);
			}
		}
		final String scenarioText = strb.toString();
		Loggers.SCENARIO.log(Level.INFO, scenarioText);
		final ScriptEngine scriptEngine = ScriptEngineUtil.resolve(scenarioPath, extClsLoader);
		if(scriptEngine == null) {
			Loggers.ERR.fatal("Failed to resolve the scenario engine for the file \"{}\"", scenarioPath);
		} else {
			Loggers.MSG.info("Using the \"{}\" scenario engine", scriptEngine.getFactory().getEngineName());
			// expose the environment values
			System.getenv().forEach(scriptEngine::put);
			// expose the loaded configuration
			scriptEngine.getContext().setAttribute(ATTR_CONFIG, config, ENGINE_SCOPE);
			// expose the step types
			ScriptEngineUtil.registerStepTypes(scriptEngine, extensions, config, metricsMgr);
			// go
			try {
				scriptEngine.eval(scenarioText);
			} catch(final ScriptException e) {
				e.printStackTrace();
				LogUtil.exception(
					Level.ERROR, e, "\nScenario failed @ file \"{}\", line #{}, column #{}:\n{}", scenarioPath,
					e.getLineNumber(), e.getColumnNumber(), e.getMessage()
				);
			}
		}
	}
}
