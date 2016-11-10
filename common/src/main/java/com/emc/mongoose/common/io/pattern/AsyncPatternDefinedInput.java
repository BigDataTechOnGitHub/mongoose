package com.emc.mongoose.common.io.pattern;

import com.emc.mongoose.common.exception.OmgDoesNotPerformException;
import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.common.io.AsyncValueInput;
import com.emc.mongoose.common.io.AsyncStringInputFactory;

public final class AsyncPatternDefinedInput
extends AsyncValueInput<String>
implements PatternDefinedInput {
	
	private final PatternDefinedInput wrappedGenerator;
	
	public AsyncPatternDefinedInput(final String pattern)
	throws UserShootHisFootException {
		this(new RangePatternDefinedInput(pattern, AsyncStringInputFactory.getInstance()));
	}
	
	private AsyncPatternDefinedInput(final PatternDefinedInput wrappedGenerator)
	throws OmgDoesNotPerformException {
		super(
			null,
			new InitializedCallableBase<String>() {
				private final StringBuilder result = new StringBuilder();
				@Override
				public String call()
				throws Exception {
					result.setLength(0);
					return wrappedGenerator.format(result);
				}
			}
		);
		this.wrappedGenerator = wrappedGenerator;
	}
	
	@Override
	public String getPattern() {
		return wrappedGenerator.getPattern();
	}

	@Override
	public String format(final StringBuilder result) {
		return wrappedGenerator.format(result);
	}
}
