package com.emc.mongoose.scenario.step;

import com.emc.mongoose.model.svc.Service;

public interface LoadStepService
extends Service, LoadStep {

	String SVC_NAME_PREFIX = "scenario/step/";
}
