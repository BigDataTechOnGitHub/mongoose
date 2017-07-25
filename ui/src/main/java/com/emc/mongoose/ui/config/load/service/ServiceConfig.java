package com.emc.mongoose.ui.config.load.service;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 Created by andrey on 26.07.17.
 */
public final class ServiceConfig
implements Serializable {

	public static final String KEY_THREADS = "threads";

	public final void setThreads(final int count) {
		this.threads = count;
	}

	@JsonProperty(KEY_THREADS) private int threads;

	public ServiceConfig() {
	}

	public ServiceConfig(final ServiceConfig other) {
		this.threads = other.getThreads();
	}

	public final int getThreads() {
		return threads;
	}
}