package com.emc.mongoose.web.ui;
/**
 * Created by gusakk on 02/10/14.
 */
public enum RunModes {
	//
	VALUE_RUN_MODE_STANDALONE("standalone"),
	VALUE_RUN_MODE_WSMOCK("wsmock"),
	VALUE_RUN_MODE_SERVER("server"),
	VALUE_RUN_MODE_CLIENT("client");
	//
	private final String value;
	//
	RunModes(final String value) {
		this.value = value;
	}
	//
	public final String getValue() {
		return value;
	}
}
