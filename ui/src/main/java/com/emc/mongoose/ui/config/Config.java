package com.emc.mongoose.ui.config;

import com.emc.mongoose.common.env.PathUtil;
import com.emc.mongoose.common.exception.OmgDoesNotPerformException;
import com.emc.mongoose.common.exception.OmgLookAtMyConsoleException;
import com.emc.mongoose.common.reflection.TypeUtil;
import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.api.TimeUtil;

import static com.emc.mongoose.common.Constants.DIR_CONFIG;
import static com.emc.mongoose.common.Constants.FNAME_CONFIG;
import static com.emc.mongoose.ui.cli.CliArgParser.ARG_PREFIX;
import com.emc.mongoose.ui.config.item.ItemConfig;
import com.emc.mongoose.ui.config.load.LoadConfig;
import com.emc.mongoose.ui.config.output.OutputConfig;
import com.emc.mongoose.ui.config.storage.StorageConfig;
import com.emc.mongoose.ui.config.test.TestConfig;
import com.emc.mongoose.ui.config.test.step.StepConfig;
import com.emc.mongoose.ui.log.LogUtil;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.apache.commons.lang.WordUtils.capitalize;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 Created on 11.07.16.
 */
public final class Config
implements Serializable {

	public static final String NAME = "name";
	public static final String DEPRECATED = "deprecated";
	public static final String TARGET = "target";
	public static final String PATH_SEP = "-";
	public static final String KEY_VERSION = "version";
	public static final String KEY_ITEM = "item";
	public static final String KEY_LOAD = "load";
	public static final String KEY_OUTPUT = "output";
	public static final String KEY_STORAGE = "storage";
	public static final String KEY_TEST = "test";
	public static final String KEY_ALIASING = "aliasing";
	
	@JsonProperty(KEY_ITEM) private ItemConfig itemConfig;
	@JsonProperty(KEY_LOAD) private LoadConfig loadConfig;
	@JsonProperty(KEY_OUTPUT) private OutputConfig outputConfig;
	@JsonProperty(KEY_STORAGE) private StorageConfig storageConfig;
	@JsonProperty(KEY_TEST) private TestConfig testConfig;
	@JsonProperty(KEY_VERSION) private String version;
	@JsonProperty(KEY_ALIASING) private List<Map<String, Object>> aliasingConfig;

	public Config() {}

	public Config(final Config config) {
		this.version = config.getVersion();
		this.itemConfig = new ItemConfig(config.getItemConfig());
		this.loadConfig = new LoadConfig(config.getLoadConfig());
		this.outputConfig = new OutputConfig(config.getOutputConfig());
		this.storageConfig = new StorageConfig(config.getStorageConfig());
		this.testConfig = new TestConfig(config.getTestConfig());
		final List<Map<String, Object>> ac = config.getAliasingConfig();
		this.aliasingConfig = ac == null ? null : new ArrayList<>(config.getAliasingConfig());
	}

	public final String getVersion() {
		return version;
	}

	public final OutputConfig getOutputConfig() {
		return outputConfig;
	}

	public final StorageConfig getStorageConfig() {
		return storageConfig;
	}

	public final TestConfig getTestConfig() {
		return testConfig;
	}

	public final LoadConfig getLoadConfig() {
		return loadConfig;
	}

	public final ItemConfig getItemConfig() {
		return itemConfig;
	}

	public final List<Map<String, Object>> getAliasingConfig() {
		return aliasingConfig;
	}
	
	public final void setVersion(final String version) {
		this.version = version;
	}

	public final void setOutputConfig(final OutputConfig outputConfig) {
		this.outputConfig = outputConfig;
	}
	
	public final void setStorageConfig(final StorageConfig storageConfig) {
		this.storageConfig = storageConfig;
	}

	public final void setTestConfig(final TestConfig testConfig) {
		this.testConfig = testConfig;
	}
	
	public final void setLoadConfig(final LoadConfig loadConfig) {
		this.loadConfig = loadConfig;
	}
	
	public final void setItemConfig(final ItemConfig itemConfig) {
		this.itemConfig = itemConfig;
	}
	
	public final void setAliasingConfig(final List<Map<String, Object>> aliasingConfig) {
		this.aliasingConfig = aliasingConfig;
	}

	public void apply(final Map<String, Object> tree)
	throws IllegalArgumentException {
		final StepConfig testStepConfg = getTestConfig().getStepConfig();
		final String oldTestStepId = testStepConfg.getId();
		applyAliasing(tree, getAliasingConfig());
		try {
			applyRecursively(this, tree);
		} catch(final IllegalArgumentNameException e) {
			throw new IllegalArgumentNameException(ARG_PREFIX + e.getMessage());
		} catch(final InvocationTargetException | IllegalAccessException e) {
			e.printStackTrace(System.err);
		} finally {
			final String newTestStepId = testStepConfg.getId();
			if(oldTestStepId == null) {
				if(newTestStepId == null) {
					testStepConfg.setId(LogUtil.getDateTimeStamp());
				}
			}
		}
	}

	private static void applyAliasing(
		final Map<String, Object> tree, final List<Map<String, Object>> rawAliases
	) {
		String aliasName, aliasTarget, aliasNamePath[], aliasNamePart;
		Map<String, Object> subTree;
		Object t;

		for(final Map<String, Object> nextAliasNode : rawAliases) {

			aliasName = (String) nextAliasNode.get(NAME);
			aliasTarget = (String) nextAliasNode.get(TARGET);
			if(aliasName.equals(aliasTarget)) {
				throw new IllegalAliasNameException(aliasName);
			}
			aliasNamePath = aliasName.split(PATH_SEP);
			subTree = tree;

			for(int i = 0; i < aliasNamePath.length; i ++) {

				aliasNamePart = aliasNamePath[i];
				t = subTree.get(aliasNamePart);

				if(t != null) {
					if(t instanceof Map) {
						subTree = (Map<String, Object>) t;
					} else if(i == aliasNamePath.length - 1) {
						if(aliasTarget == null) {
							System.err.println(
								"ERROR: configuration value @ \"" + aliasName + "\" is deprecated"
							);
						} else if(nextAliasNode.containsKey(DEPRECATED)) {
							if((boolean) nextAliasNode.get(DEPRECATED)) {
								System.err.println(
									"WARNING: configuration value @ \"" + aliasName +
										"\" is deprecated, please use \"" + aliasTarget +
										"\" instead"
								);
							}
						}
						setNewPath(tree, aliasTarget, t);
						subTree.remove(aliasNamePart);
					} else {
						throw new IllegalAliasNameException(aliasName);
					}
				} else {
					break;
				}
			}
		}

		cleanEmptyPaths(tree);
	}

	private static void setNewPath(
		final Map<String, Object> tree, final String rawPath, final Object value
	) {
		final String newPath[] = rawPath.split(PATH_SEP);
		Map<String, Object> subTree = tree;
		Object t;
		String newPathPart;

		for(int i = 0; i < newPath.length; i ++) {
			newPathPart = newPath[i];
			t = subTree.get(newPathPart);

			if(t != null) {
				if(t instanceof Map) {
					subTree = (Map<String, Object>) t;
					if(i == newPath.length - 1) {
						subTree.put(newPathPart, value);
					}
				} else {
					throw new IllegalAliasTargetException(rawPath);
				}
			} else {
				if(i == newPath.length - 1) {
					subTree.put(newPathPart, value);
				} else {
					t = new HashMap<String, Object>();
					subTree.put(newPathPart, t);
					subTree = (Map<String, Object>) t;
				}
			}
		}
	}

	private static void cleanEmptyPaths(final Map<String, Object> tree) {

		boolean emptyBranchFound = true; // assume
		Object t;
		Iterator<Map.Entry<String, Object>> i;
		Map.Entry<String, Object> nextEntry;

		while(emptyBranchFound && !tree.isEmpty()) {
			i = tree.entrySet().iterator();
			while(i.hasNext()) {
				nextEntry = i.next();
				emptyBranchFound = false;
				t = nextEntry.getValue();
				if(t instanceof Map) {
					if(((Map) t).isEmpty()) {
						i.remove();
						emptyBranchFound = true;
					} else {
						cleanEmptyPaths((Map<String, Object>) t);
					}
				}
			}
		}
	}

	private static void applyRecursively(final Object config, final Map<String, Object> branch)
	throws InvocationTargetException, IllegalAccessException {
		final Class configCls = config.getClass();
		for(final String key : branch.keySet()) {
			final Object node = branch.get(key);
			if(node instanceof Map) {
				final Map<String, Object> childBranch = (Map<String, Object>) node;
				try {
					final Method subConfigGetter = configCls.getMethod(
						"get" + capitalize(key) + "Config"
					);
					final Object subConfig = subConfigGetter.invoke(config);
					try {
						applyRecursively(subConfig, childBranch);
					} catch(final IllegalArgumentNameException e) {
						throw new IllegalArgumentNameException(key + PATH_SEP + e.getMessage());
					}
				} catch(final NoSuchMethodException e) {
					throw new IllegalArgumentNameException(key);
				}
			} else if(config instanceof Map) {
				((Map<String, Object>) config).put(key, node);
			} else {
				applyField(config, key, node);
			}
		}
	}

	private static void applyField(final Object config, final String key, final Object value)
	throws InvocationTargetException, IllegalAccessException {
		final Class configCls = config.getClass();
		try {
			final Method fieldGetter = configCls.getMethod("get" + capitalize(key));
			final Class fieldType = fieldGetter.getReturnType();
			if(value == null) {
				configCls
					.getMethod("set" + capitalize(key), fieldType)
					.invoke(config, value);
			} else {
				final Class valueType = value.getClass();
				if(TypeUtil.typeEquals(fieldType, valueType)) {
					configCls.getMethod("set" + capitalize(key), fieldType).invoke(config, value);
				} else if(value instanceof List && TypeUtil.typeEquals(fieldType, List.class)) {
					configCls.getMethod("set" + capitalize(key), fieldType).invoke(config, value);
				} else if(value instanceof String) { // CLI arguments case
					if(fieldType.equals(List.class)) {
						final List<String> listValue = Arrays.asList(((String) value).split(","));
						configCls
							.getMethod("set" + capitalize(key), List.class)
							.invoke(config, listValue);
					} else if(fieldType.equals(Map.class)) {
						final Map<String, String>
							field = (Map<String, String>) fieldGetter.invoke(config);
						final String keyValuePair[] = ((String) value).split(":", 2);
						if(keyValuePair.length == 1) {
							field.remove(keyValuePair[0]);
						} else if(keyValuePair.length == 2) {
							field.put(keyValuePair[0], keyValuePair[1]);
						}
					} else if(fieldType.equals(Integer.TYPE) || fieldType.equals(Integer.class)) {
						final int intValue = Integer.parseInt((String) value);
						configCls
							.getMethod("set" + capitalize(key), Integer.TYPE)
							.invoke(config, intValue);
					} else if(fieldType.equals(Long.TYPE) || fieldType.equals(Long.class)) {
						try {
							final long longValue = Long.parseLong((String) value);
							configCls
								.getMethod("set" + capitalize(key), Long.TYPE)
								.invoke(config, longValue);
						} catch(final NumberFormatException e) {
							final long timeValue = TimeUtil.getTimeInSeconds((String) value);
							configCls
								.getMethod("set" + capitalize(key), Long.TYPE)
								.invoke(config, timeValue);
						}
					} else if(fieldType.equals(Float.TYPE) || fieldType.equals(Float.class)) {
						final float floatValue = Float.parseFloat((String) value);
						configCls
							.getMethod("set" + capitalize(key), Float.TYPE)
							.invoke(config, floatValue);
					} else if(fieldType.equals(Double.TYPE) || fieldType.equals(Double.class)) {
						final double doubleValue = Double.parseDouble((String) value);
						configCls
							.getMethod("set" + capitalize(key), Double.TYPE)
							.invoke(config, doubleValue);
					} else if(fieldType.equals(Boolean.TYPE) || fieldType.equals(Boolean.class)) {
						final boolean boolValue = Boolean.parseBoolean((String) value);
						configCls
							.getMethod("set" + capitalize(key), Boolean.TYPE)
							.invoke(config, boolValue);
					} else if(fieldType.equals(SizeInBytes.class)) {
						final SizeInBytes sizeValue = new SizeInBytes((String) value);
						configCls
							.getMethod("set" + capitalize(key), SizeInBytes.class)
							.invoke(config, sizeValue);
					} else {
						throw new IllegalStateException(
							"Field type is \"" + fieldType.getName() + "\" for key: " + key
						);
					}
				} else {
					if(Integer.TYPE.equals(valueType) || Integer.class.equals(valueType)) {
						final int intValue = (int) value;
						if(SizeInBytes.class.equals(fieldType)) {
							configCls
								.getMethod("set" + capitalize(key), SizeInBytes.class)
								.invoke(config, new SizeInBytes(intValue));
						} else if(Long.class.equals(fieldType) || Long.TYPE.equals(fieldType)) {
							configCls
								.getMethod("set" + capitalize(key), Long.TYPE)
								.invoke(config, intValue);
						} else if(Double.class.equals(fieldType) || Double.TYPE.equals(fieldType)) {
							configCls
								.getMethod("set" + capitalize(key), Double.TYPE)
								.invoke(config, intValue);
						} else {
							throw new IllegalStateException(
								"Field type is \"" + fieldType.getName() +
								"\" but value type is \"" + valueType.getName() + "\""
							);
						}
					} else {
						throw new IllegalStateException(
							"Field type is \"" + fieldType.getName() +
							"\" but value type is \"" + valueType.getName() + "\""
						);
					}
				}
			}
		} catch(final NoSuchMethodException e) {
			throw new IllegalArgumentNameException(key);
		}
	}

	public static Config loadDefaults()
	throws IOException {
		final String defaultConfigPath = PathUtil.getBaseDir() + DIR_CONFIG + File.separator +
			FNAME_CONFIG;
		final ObjectMapper mapper = new ObjectMapper()
			.configure(JsonParser.Feature.ALLOW_COMMENTS, true)
			.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
		return mapper.readValue(new File(defaultConfigPath), Config.class);
	}

	public final Config replace(
		final String replacePattern, final Object newValue
	) throws OmgLookAtMyConsoleException, OmgDoesNotPerformException, IOException {
		final ObjectMapper mapper = new ObjectMapper()
			.configure(JsonParser.Feature.ALLOW_COMMENTS, true)
			.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
		try {
			final String configText = mapper.writeValueAsString(this);
			final String newConfigText;
			final String rp = Pattern.quote(replacePattern);
			if(newValue == null) {
				newConfigText = configText.replaceAll("\"" + rp + "\"", "null");
			} else if(newValue instanceof Boolean || newValue instanceof Number) {
				newConfigText = configText
					.replaceAll("\"" + rp + "\"", newValue.toString())
					.replaceAll(rp, newValue.toString());
			} else if(newValue instanceof List) {
				final List<Object> newValues = (List<Object>) newValue;
				final List<String> newStrValues = new ArrayList<>();
				// replace the values in the source list with their textual representations
				Object nextValue;
				for(int i = 0; i < newValues.size(); i ++) {
					nextValue = newValues.get(i);
					if(nextValue == null) {
						newStrValues.add("null");
					} else if(nextValue instanceof Boolean || nextValue instanceof Number) {
						newStrValues.add(nextValue.toString());
					} else if(nextValue instanceof String) {
						newStrValues.add("\"" + nextValue + "\"");
					} else {
						throw new OmgLookAtMyConsoleException(
							"Unexpected replacement value type: " + nextValue.getClass().getName()
						);
					}
				}
				final String newValueStr = "[" + String.join(",", newStrValues) + "]";
				newConfigText = configText.replace(rp, newValueStr);
			} else if(newValue instanceof String) {
				newConfigText = configText.replaceAll(rp, (String) newValue);
			} else {
				throw new OmgLookAtMyConsoleException(
					"Unexpected replacement value type: " + newValue.getClass().getName()
				);
			}
			return mapper.readValue(newConfigText, Config.class);
		} catch(final JsonProcessingException e) {
			throw new OmgDoesNotPerformException(e);
		}
	}

	public static Map<String, Object> replace(
		final Map<String, Object> config, final String replacePattern, final Object newValue
	) throws OmgLookAtMyConsoleException {
		final Map<String, Object> newConfig = new HashMap<>();
		final String rp = Pattern.quote(replacePattern);
		Object v;
		String valueStr;
		for(final String k : config.keySet()) {
			v = config.get(k);
			if(v instanceof String) {
				valueStr = (String) v;
				if(valueStr.equals("\"" + replacePattern + "\"")) {
					v = newValue;
				} else {
					if(newValue == null) {
						v = valueStr.replaceAll(rp, "");
					} else if(
						newValue instanceof Boolean || newValue instanceof Number ||
							newValue instanceof String
						) {
						v = valueStr.replaceAll(rp, newValue.toString());
					} else if(newValue instanceof List) {
						final StringJoiner sj = new StringJoiner(",");
						for(final Object newValueElement : (List) newValue) {
							sj.add(newValueElement == null ? "" : newValueElement.toString());
						}
						v = valueStr.replaceAll(rp, sj.toString());
					} else {
						throw new OmgLookAtMyConsoleException(
							"Unexpected replacement value type: " + newValue.getClass().getName()
						);
					}
				}
			} else if(v instanceof Map) {
				v = replace((Map<String, Object>) v, replacePattern, newValue);
			}
			newConfig.put(k, v);
		}
		return newConfig;
	}

	/**
	 @return The JSON pretty-printed representation of this configuration.
	 */
	@Override
	public final String toString() {
		final ObjectMapper mapper = new ObjectMapper()
			.configure(SerializationFeature.INDENT_OUTPUT, true);
		final DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter(
			"\t", DefaultIndenter.SYS_LF
		);
		final DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
		printer.withObjectIndenter(indenter);
		printer.withArrayIndenter(indenter);
		try {
			return mapper.writer(printer).writeValueAsString(this);
		} catch(final JsonProcessingException e) {
			throw new AssertionError(e);
		}
	}
}
