package com.emc.mongoose.control.run;

import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;

import org.apache.logging.log4j.Level;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

public final class RunImpl
implements Run {

	private final String comment;
	private final String scenario;
	private final ScriptEngine scriptEngine;
	private final long timestamp;

	public RunImpl(final String comment, final String scenario, final ScriptEngine scriptEngine) {
		this.comment = comment;
		this.scenario = scenario;
		this.scriptEngine = scriptEngine;
		this.timestamp = System.currentTimeMillis();
	}

	@Override
	public final long timestamp() {
		return timestamp;
	}

	@Override
	public final String comment() {
		return comment;
	}

	@Override
	public final void run() {
		Loggers.SCENARIO.log(Level.INFO, scenario);
		try {
			scriptEngine.eval(scenario);
		} catch(final ScriptException e) {
			LogUtil.trace(
				Loggers.ERR, Level.ERROR, e, "\nScenario failed, line #{}, column #{}:\n{}", e.getLineNumber(),
				e.getColumnNumber(), e.getMessage()
			);
		}
	}
}
