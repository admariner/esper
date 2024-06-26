/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.regressionlib.suite.multithread;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.Locking;
import com.espertech.esper.regressionlib.framework.RegressionExecutionPreConfigured;
import com.espertech.esper.regressionlib.support.client.SupportCompileDeployUtil;
import com.espertech.esper.regressionlib.support.util.SupportMTUpdateListener;
import com.espertech.esper.runtime.client.EPDeployment;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.espertech.esper.regressionlib.support.client.SupportCompileDeployUtil.threadJoin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultithreadStmtNamedWindowUniqueTwoWJoinConsumer implements RegressionExecutionPreConfigured {
    private final Configuration configuration;

    private int count;

    public MultithreadStmtNamedWindowUniqueTwoWJoinConsumer(Configuration configuration) {
        this.configuration = configuration;
    }

    public void run() {
        configuration.getCommon().addEventType(EventOne.class);
        configuration.getCommon().addEventType(EventTwo.class);

        runAssertion(1, true, null, null, configuration);
        runAssertion(2, false, true, Locking.SPIN, configuration);
        runAssertion(3, false, true, Locking.SUSPEND, configuration);
        runAssertion(4, false, false, null, configuration);
    }

    private void runAssertion(int runtimeNum, boolean useDefault, Boolean preserve, Locking locking, Configuration config) {
        if (!useDefault) {
            config.getRuntime().getThreading().setNamedWindowConsumerDispatchPreserveOrder(preserve);
            config.getRuntime().getThreading().setNamedWindowConsumerDispatchLocking(locking);
            config.getRuntime().getThreading().setNamedWindowConsumerDispatchTimeout(100000);

            if (!preserve) {
                // In this setting there is no guarantee:
                // (1) A thread T1 may process the first event to create a pair {E1, null}
                // (2) A thread T2 may process the second event to create a pair {E2, E1}
                // (3) Thread T2 pair may process first against consumer index
                // (4) Thread T1 pair processes against consumer index and since its a unique index it fails
                config.getRuntime().getExceptionHandling().getHandlerFactories().clear();
            }
        }

        EPRuntime runtime = EPRuntimeProvider.getRuntime(MultithreadStmtNamedWindowUniqueTwoWJoinConsumer.class.getSimpleName() + "_" + runtimeNum + "_" + (count++), config);
        runtime.initialize();

        String epl = "create window EventOneWindow#unique(key) as EventOne;\n" +
            "insert into EventOneWindow select * from EventOne;\n" +
            "create window EventTwoWindow#unique(key) as EventTwo;\n" +
            "insert into EventTwoWindow select * from EventTwo;\n" +
            "@name('out') select * from EventOneWindow as e1, EventTwoWindow as e2 where e1.key = e2.key";
        EPDeployment deployed = SupportCompileDeployUtil.compileDeploy(epl, runtime, config);

        SupportMTUpdateListener listener = new SupportMTUpdateListener();
        runtime.getDeploymentService().getStatement(deployed.getDeploymentId(), "out").addListener(listener);

        Runnable runnableOne = new Runnable() {
            public void run() {
                for (int i = 0; i < 33; i++) {
                    EventOne eventOne = new EventOne("TEST");
                    runtime.getEventService().sendEventBean(eventOne, eventOne.getClass().getSimpleName());
                    EventTwo eventTwo = new EventTwo("TEST");
                    runtime.getEventService().sendEventBean(eventTwo, eventTwo.getClass().getSimpleName());
                }
            }
        };
        Runnable runnableTwo = new Runnable() {
            public void run() {
                for (int i = 0; i < 33; i++) {
                    EventTwo eventTwo = new EventTwo("TEST");
                    runtime.getEventService().sendEventBean(eventTwo, eventTwo.getClass().getSimpleName());
                    EventOne eventOne = new EventOne("TEST");
                    runtime.getEventService().sendEventBean(eventOne, eventOne.getClass().getSimpleName());
                }
            }
        };
        Runnable runnableThree = new Runnable() {
            public void run() {
                for (int i = 0; i < 34; i++) {
                    EventTwo eventTwo = new EventTwo("TEST");
                    runtime.getEventService().sendEventBean(eventTwo, eventTwo.getClass().getSimpleName());
                    EventOne eventOne = new EventOne("TEST");
                    runtime.getEventService().sendEventBean(eventOne, eventOne.getClass().getSimpleName());
                }
            }
        };

        Thread t1 = new Thread(runnableOne, MultithreadStmtNamedWindowUniqueTwoWJoinConsumer.class.getSimpleName() + "-one");
        Thread t2 = new Thread(runnableTwo, MultithreadStmtNamedWindowUniqueTwoWJoinConsumer.class.getSimpleName() + "-two");
        Thread t3 = new Thread(runnableThree, MultithreadStmtNamedWindowUniqueTwoWJoinConsumer.class.getSimpleName() + "-three");
        t1.start();
        t2.start();
        t3.start();
        SupportCompileDeployUtil.threadSleep(1000);

        threadJoin(t1);
        threadJoin(t2);
        threadJoin(t3);
        SupportCompileDeployUtil.threadSleep(200);

        List<EventBean[]> delivered = listener.getNewDataList();

        // count deliveries of multiple rows
        int countMultiDeliveries = 0;
        for (EventBean[] events : delivered) {
            countMultiDeliveries += events.length > 1 ? 1 : 0;
        }

        // count deliveries where instance doesn't monotonically increase from previous row for one column
        int countNotMonotone = 0;
        Long previousIdE1 = null;
        Long previousIdE2 = null;
        for (EventBean[] events : delivered) {
            long idE1 = (Long) events[0].get("e1.instance");
            long idE2 = (Long) events[0].get("e2.instance");
            // comment-in when needed: System.out.println("Received " + idE1 + " " + idE2);

            if (previousIdE1 != null) {
                boolean incorrect = idE1 != previousIdE1 && idE2 != previousIdE2;
                if (!incorrect) {
                    incorrect = idE1 == previousIdE1 && idE2 != (previousIdE2 + 1) ||
                        (idE2 == previousIdE2 && idE1 != (previousIdE1 + 1));
                }
                if (incorrect) {
                    // comment-in when needed: System.out.println("Non-Monotone increase (this is still correct but noteworthy)");
                    countNotMonotone++;
                }
            }

            previousIdE1 = idE1;
            previousIdE2 = idE2;
        }

        if (useDefault || preserve) {
            assertEquals("multiple row deliveries: " + countMultiDeliveries, 0, countMultiDeliveries);
            // the number of non-monotone delivers should be small but not zero
            // this is because when the event get generated and when the event actually gets processed may not be in the same order
            assertTrue("count not monotone: " + countNotMonotone, countNotMonotone < 100);
            assertTrue(delivered.size() >= 197); // its possible to not have 199 since there may not be events on one side of the join
        } else {
            assertTrue("count not monotone: " + countNotMonotone, countNotMonotone > 2);
        }

        runtime.destroy();
    }

    public static class EventOne {

        private static final AtomicLong ATOMIC_LONG = new AtomicLong(1);
        private final long instance;
        private final String key;

        private EventOne(String key) {
            instance = ATOMIC_LONG.getAndIncrement();
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public long getInstance() {
            return instance;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventOne)) return false;

            EventOne eventOne = (EventOne) o;

            return key.equals(eventOne.key);
        }

        public int hashCode() {
            return key.hashCode();
        }
    }

    public static class EventTwo {

        private static final AtomicLong ATOMIC_LONG = new AtomicLong(1);

        private final long instance;
        private final String key;

        public EventTwo(String key) {
            instance = ATOMIC_LONG.getAndIncrement();
            this.key = key;
        }

        public long getInstance() {
            return instance;
        }

        public String getKey() {
            return key;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventTwo)) return false;

            EventTwo eventTwo = (EventTwo) o;

            return key.equals(eventTwo.key);

        }

        public int hashCode() {
            return key.hashCode();
        }
    }
}