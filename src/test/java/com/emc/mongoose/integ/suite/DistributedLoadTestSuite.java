package com.emc.mongoose.integ.suite;
/**
 Created by kurila on 15.07.15.
 */
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
//
@RunWith(Suite.class)
@Suite.SuiteClasses({
	/*com.emc.mongoose.integ.distributed.single.WriteByCountTest.class,
	com.emc.mongoose.integ.distributed.single.WriteByTimeTest.class,
	com.emc.mongoose.integ.distributed.single.WriteLoggingTest.class,
	com.emc.mongoose.integ.distributed.single.ReadLoggingTest.class,
	com.emc.mongoose.integ.distributed.single.DeleteLoggingTest.class,
	com.emc.mongoose.integ.distributed.single.UpdateLoggingTest.class,
	com.emc.mongoose.integ.distributed.chain.SequentialLoadTest.class,
	com.emc.mongoose.integ.distributed.chain.SimultaneousLoadTest.class,*/
	com.emc.mongoose.integ.distributed.rampup.RampupTest.class,
})
public class DistributedLoadTestSuite {}
