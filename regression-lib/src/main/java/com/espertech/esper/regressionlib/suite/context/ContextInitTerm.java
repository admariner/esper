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
package com.espertech.esper.regressionlib.suite.context;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.context.*;
import com.espertech.esper.common.client.scopetest.EPAssertionUtil;
import com.espertech.esper.common.client.util.DateTime;
import com.espertech.esper.common.client.util.StatementProperty;
import com.espertech.esper.common.client.util.StatementType;
import com.espertech.esper.common.internal.support.SupportBean;
import com.espertech.esper.common.internal.support.SupportBean_S0;
import com.espertech.esper.common.internal.support.SupportBean_S1;
import com.espertech.esper.common.internal.support.SupportBean_S2;
import com.espertech.esper.common.internal.util.CollectionUtil;
import com.espertech.esper.regressionlib.framework.RegressionEnvironment;
import com.espertech.esper.regressionlib.framework.RegressionExecution;
import com.espertech.esper.regressionlib.framework.RegressionFlag;
import com.espertech.esper.regressionlib.framework.RegressionPath;
import com.espertech.esper.regressionlib.support.bean.SupportBean_S3;
import com.espertech.esper.regressionlib.support.context.AgentInstanceAssertionUtil;
import com.espertech.esper.regressionlib.support.context.SupportContextPropUtil;
import com.espertech.esper.regressionlib.support.context.SupportSelectorById;
import com.espertech.esper.regressionlib.support.context.SupportSelectorFilteredInitTerm;
import com.espertech.esper.regressionlib.support.filter.SupportFilterServiceHelper;
import com.espertech.esper.regressionlib.support.util.SupportScheduleHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ContextInitTerm {

    public static Collection<RegressionExecution> executions() {
        ArrayList<RegressionExecution> execs = new ArrayList<>();
        execs.add(new ContextInitTermNoTerminationCondition());
        execs.add(new ContextStartEndNoTerminationCondition());
        execs.add(new ContextStartEndAfterZeroInitiatedNow());
        execs.add(new ContextStartEndEndSameEventAsAnalyzed());
        execs.add(new ContextInitTermContextPartitionSelection());
        execs.add(new ContextInitTermFilterInitiatedFilterAllTerminated());
        execs.add(new ContextInitTermFilterInitiatedFilterTerminatedCorrelatedOutputSnapshot());
        execs.add(new ContextInitTermFilterAndAfter1Min());
        execs.add(new ContextInitTermFilterAndPattern());
        execs.add(new ContextInitTermPatternAndAfter1Min());
        execs.add(new ContextInitTermScheduleFilterResources());
        execs.add(new ContextInitTermPatternIntervalZeroInitiatedNow());
        execs.add(new ContextInitTermPatternInclusion());
        execs.add(new ContextInitTermPatternInitiatedStraightSelect());
        execs.add(new ContextInitTermFilterInitiatedStraightEquals());
        execs.add(new ContextInitTermFilterAllOperators());
        execs.add(new ContextInitTermFilterBooleanOperator());
        execs.add(new ContextInitTermTerminateTwoContextSameTime());
        execs.add(new ContextInitTermOutputSnapshotWhenTerminated());
        execs.add(new ContextInitTermOutputAllEvery2AndTerminated());
        execs.add(new ContextInitTermOutputWhenExprWhenTerminatedCondition());
        execs.add(new ContextInitTermOutputOnlyWhenTerminatedCondition());
        execs.add(new ContextInitTermOutputOnlyWhenSetAndWhenTerminatedSet());
        execs.add(new ContextInitTermOutputOnlyWhenTerminatedThenSet());
        execs.add(new ContextInitTermCrontab());
        execs.add(new ContextStartEndStartNowCalMonthScoped());
        execs.add(new ContextInitTermAggregationGrouped());
        execs.add(new ContextInitTermPrevPrior());
        execs.add(new ContextStartEndPatternCorrelated());
        execs.add(new ContextInitTermPatternCorrelated());
        execs.add(new ContextStartEndFilterWithPatternCorrelatedWithAsName(false));
        execs.add(new ContextStartEndFilterWithPatternCorrelatedWithAsName(true));
        execs.add(new ContextStartEndPatternWithPatternCorrelatedWithAsName());
        execs.add(new ContextStartEndPatternWithFilterCorrelatedWithAsName());
        execs.add(new ContextInitTermWithTermEvent(false));
        execs.add(new ContextInitTermWithTermEvent(true));
        execs.add(new ContextInitTermSubqueryInFilter());
        return execs;
    }

    private static class ContextInitTermSubqueryInFilter implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "create context MyContext initiated by SupportBean_S0 AS criteria;\n" +
                "context MyContext create window MyWindow#time(86400 seconds) (eventId int);\n" +
                "context MyContext insert into MyWindow select id as eventId from SupportBean_S2;\n" +
                "context MyContext " +
                "  insert into NotFound " +
                "  select id from SupportBean_S1(not exists (select * from MyWindow where event.id = eventId)) AS event;\n" +
                "@name('s0') select * from NotFound;\n";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean_S0(100));
            env.milestone(0);

            env.sendEventBean(new SupportBean_S1(100));
            env.assertEqualsNew("s0", "id", 100);

            env.sendEventBean(new SupportBean_S2(100));
            env.milestone(1);

            env.sendEventBean(new SupportBean_S1(100));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextInitTermWithTermEvent implements RegressionExecution {
        private final boolean overlapping;

        public ContextInitTermWithTermEvent(boolean overlapping) {
            this.overlapping = overlapping;
        }

        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema UserEvent(userId string, alert string);\n" +
                "create context UserSessionContext " + (overlapping ? "initiated" : "start") + " UserEvent(alert = 'A')\n" +
                "  " + (overlapping ? "terminated" : "end") + " UserEvent(alert = 'B') as termEvent;\n" +
                "@name('s0') context UserSessionContext select *, context.termEvent as term from UserEvent#firstevent\n" +
                "  output snapshot when terminated;";
            env.compileDeploy(epl).addListener("s0");

            sendUser(env, "U1", "A");
            sendUser(env, "U1", null);
            sendUser(env, "U1", null);
            env.assertListenerNotInvoked("s0");
            env.milestone(0);

            Map<String, Object> term = sendUser(env, "U1", "B");
            env.assertEventNew("s0", event -> assertEquals(term, event.get("term")));

            env.undeployAll();
        }

        private Map<String, Object> sendUser(RegressionEnvironment env, String user, String alert) {
            Map<String, Object> data = CollectionUtil.buildMap("userId", user, "alert", alert);
            env.sendEventMap(data, "UserEvent");
            return data;
        }

        public String name() {
            return this.getClass().getSimpleName() + "{" +
                "overlapping=" + overlapping +
                '}';
        }

        public EnumSet<RegressionFlag> flags() {
            return EnumSet.of(RegressionFlag.SERDEREQUIRED);
        }
    }

    private static class ContextStartEndPatternWithFilterCorrelatedWithAsName implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "create context MyContext as start pattern[s0=SupportBean_S0] as starter\n" +
                "end SupportBean_S1(id=starter.s0.id) as ender;\n" +
                "@name('s0') context MyContext select * from SupportBean";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean_S0(10));

            env.sendEventBean(new SupportBean("E1", 0));
            env.assertEventNew("s0", event -> {
            });

            env.sendEventBean(new SupportBean_S1(10));
            env.sendEventBean(new SupportBean("E2", 0));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextStartEndPatternWithPatternCorrelatedWithAsName implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "create context MyContext as start pattern[s0=SupportBean_S0] as starter\n" +
                "end pattern [s1=SupportBean_S1(id=starter.s0.id)] as ender;\n" +
                "@name('s0') context MyContext select context.starter as starter, context.starter.s0 as starterS0, context.starter.s0.id as starterS0id from SupportBean";
            env.compileDeploy(epl).addListener("s0");

            SupportBean_S0 starter = new SupportBean_S0(10);
            env.sendEventBean(starter);

            env.milestone(0);

            env.sendEventBean(new SupportBean("E1", 0));
            env.assertEventNew("s0", event -> {
                Map<String, Object> starterMap = (Map<String, Object>) event.get("starter");
                assertEquals(starter, ((EventBean) starterMap.get("s0")).getUnderlying());
                assertEquals(1, starterMap.size());
                assertEquals(starter, event.get("starterS0"));
                assertEquals(10, event.get("starterS0id"));
            });

            env.sendEventBean(new SupportBean_S1(10));
            env.sendEventBean(new SupportBean("E2", 0));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextStartEndFilterWithPatternCorrelatedWithAsName implements RegressionExecution {
        private final boolean soda;

        public ContextStartEndFilterWithPatternCorrelatedWithAsName(boolean soda) {
            this.soda = soda;
        }

        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            env.advanceTime(0);

            String eplContext = "@public create context MyContext as start SupportBean_S0 as starter " +
                "end pattern [s1=SupportBean_S1(id=starter.id) or timer:interval(30)] as ender";
            env.compileDeploy(soda, eplContext, path);

            String eplSelect = "@name('s0') context MyContext select context.starter as starter, context.ender as ender, context.ender.s1 as enderS1, context.ender.s1.id as enderS1id from SupportBean_S0 output when terminated";
            env.compileDeploy(eplSelect, path).addListener("s0");

            SupportBean_S0 starterOne = new SupportBean_S0(10);
            env.sendEventBean(starterOne);

            env.milestone(0);

            SupportBean_S1 enderOne = new SupportBean_S1(10);
            env.sendEventBean(enderOne);
            env.assertEventNew("s0", event -> {
                assertEquals(starterOne, event.get("starter"));
                Map<String, Object> enderMapOne = (Map<String, Object>) event.get("ender");
                assertEquals(enderOne, ((EventBean) enderMapOne.get("s1")).getUnderlying());
                assertNull(enderMapOne.get("starter"));
                assertEquals(enderOne, event.get("enderS1"));
                assertEquals(10, event.get("enderS1id"));
            });

            env.advanceTime(10000);
            SupportBean_S0 starterTwo = new SupportBean_S0(20);
            env.sendEventBean(starterTwo);

            env.milestone(1);

            env.advanceTime(40000);

            env.assertEventNew("s0", event -> {
                assertEquals(starterTwo, event.get("starter"));
                Map<String, Object> enderMapTwo = (Map<String, Object>) event.get("ender");
                assertNull(enderMapTwo.get("s1"));
                assertNull(enderMapTwo.get("starter"));
                assertNull(event.get("enderS1"));
                assertNull(event.get("enderS1id"));
            });

            env.undeployAll();

            env.tryInvalidCompile(path, "context MyContext select context.ender.starter from SupportBean_S0",
                "Failed to validate select-clause expression 'context.ender.starter': Context property 'ender.starter' is not a known property, known properties are [name, id, startTime, endTime, starter, ender]");

            String eplInvalidTagProvidedByFilter = "create context MyContext as start SupportBean_S1(id=0) as starter " +
                "end pattern [starter=SupportBean_S1(id=1) or timer:interval(30)] as ender";
            env.tryInvalidCompile(eplInvalidTagProvidedByFilter, "Tag 'starter' for event 'SupportBean_S1' is already assigned");

            String eplInvalidTagProvidedByPatternUnnamed = "create context MyContext as start pattern[starter=SupportBean_S1(id=0)] " +
                "end pattern [starter=SupportBean_S1(id=1) or timer:interval(30)] as ender";
            env.tryInvalidCompile(eplInvalidTagProvidedByPatternUnnamed, "Tag 'starter' for event 'SupportBean_S1' is already assigned");

            String eplInvalidTagProvidedByPatternNamed = "create context MyContext as start pattern[s1=SupportBean_S1(id=0)] as starter " +
                "end pattern [starter=SupportBean_S1(id=1) or timer:interval(30)] as ender";
            env.tryInvalidCompile(eplInvalidTagProvidedByPatternNamed, "Tag 'starter' for event 'SupportBean_S1' is already assigned");
        }

        public String name() {
            return this.getClass().getSimpleName() + "{" +
                "soda=" + soda +
                '}';
        }

        public EnumSet<RegressionFlag> flags() {
            return EnumSet.of(RegressionFlag.SERDEREQUIRED);
        }
    }

    private static class ContextStartEndPatternCorrelated implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "create context MyContext\n" +
                "start pattern [a=SupportBean_S0 or b=SupportBean_S1]\n" +
                "end pattern [SupportBean_S2(id=a.id) or SupportBean_S3(id=b.id)];\n" +
                "@name('s0') context MyContext select * from SupportBean";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean_S1(100));
            sendAssertSB(env, true);
            env.sendEventBean(new SupportBean_S2(100));
            sendAssertSB(env, true);
            env.sendEventBean(new SupportBean_S3(101));
            sendAssertSB(env, true);
            env.sendEventBean(new SupportBean_S3(100));
            sendAssertSB(env, false);

            env.sendEventBean(new SupportBean_S0(200));
            sendAssertSB(env, true);
            env.sendEventBean(new SupportBean_S2(201));
            env.sendEventBean(new SupportBean_S3(200));
            sendAssertSB(env, true);
            env.sendEventBean(new SupportBean_S2(200));
            sendAssertSB(env, false);

            env.undeployAll();
        }

        private void sendAssertSB(RegressionEnvironment env, boolean received) {
            env.sendEventBean(new SupportBean());
            env.assertListenerInvokedFlag("s0", received);
        }
    }

    private static class ContextInitTermPatternCorrelated implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            // Sample alternative epl without pattern is:
            //   create context ACtx
            //   initiated by SupportBean(intPrimitive = 0) as a
            //   terminated by SupportBean(theString=a.theString, intPrimitive = 1);
            //   @name('s0') context ACtx select * from SupportBean_S0(p00=context.a.theString);

            String epl = "create context ACtx\n" +
                "initiated by pattern[every a=SupportBean(intPrimitive = 0)]\n" +
                "terminated by pattern[SupportBean(theString=a.theString, intPrimitive = 1)];\n" +
                "@name('s0') context ACtx select * from SupportBean_S0(p00=context.a.theString);\n";

            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("G1", 0));
            sendAssertS0(env, "G1", true);
            sendAssertS0(env, "X", false);

            env.milestone(0);

            sendAssertS0(env, "G1", true);
            sendAssertS0(env, "X", false);
            env.sendEventBean(new SupportBean("G1", 1));
            sendAssertS0(env, "G1", false);

            env.milestone(1);

            env.sendEventBean(new SupportBean("G2", 0));
            sendAssertS0(env, "G1", false);
            sendAssertS0(env, "G2", true);

            env.milestone(2);

            sendAssertS0(env, "G2", true);
            sendAssertS0(env, "X", false);
            env.sendEventBean(new SupportBean("G2", 1));
            sendAssertS0(env, "G1", false);
            sendAssertS0(env, "G2", false);

            env.undeployAll();
        }

        private void sendAssertS0(RegressionEnvironment env, String p00, boolean received) {
            env.sendEventBean(new SupportBean_S0(1, p00));
            env.assertListenerInvokedFlag("s0", received);
        }
    }

    private static class ContextInitTermNoTerminationCondition implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            AtomicInteger milestone = new AtomicInteger();
            env.advanceTime(5);
            tryAssertionNoTerminationConditionOverlapping(env, milestone, false);
            tryAssertionNoTerminationConditionOverlapping(env, milestone, true);
        }
    }

    private static class ContextStartEndNoTerminationCondition implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            AtomicInteger milestone = new AtomicInteger();
            env.advanceTime(5);
            tryAssertionNoTerminationConditionNonoverlapping(env, milestone, false);
            tryAssertionNoTerminationConditionNonoverlapping(env, milestone, true);
        }
    }

    private static class ContextStartEndAfterZeroInitiatedNow implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fieldsOne = "c0,c1".split(",");
            RegressionPath path = new RegressionPath();
            env.advanceTime(0);

            // test start-after with immediate start
            String contextExpr = "@public create context CtxPerId start after 0 sec end after 60 sec";
            env.compileDeploy(contextExpr, path);
            env.compileDeploy("@name('s0') context CtxPerId select theString as c0, intPrimitive as c1 from SupportBean", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("s0", fieldsOne, new Object[]{"E1", 1});

            env.milestone(0);

            env.advanceTime(59999);
            env.sendEventBean(new SupportBean("E2", 2));
            env.assertPropsNew("s0", fieldsOne, new Object[]{"E2", 2});

            env.milestone(1);

            env.advanceTime(60000);
            env.sendEventBean(new SupportBean("E3", 3));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextInitTermPatternIntervalZeroInitiatedNow implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fieldsOne = "c0,c1".split(",");

            // test initiated-by pattern with immediate start
            env.advanceTime(120000);
            String epl = "create context CtxPerId initiated by pattern [timer:interval(0) or every timer:interval(1 min)] terminated after 60 sec;\n" +
                "@name('s0') context CtxPerId select theString as c0, sum(intPrimitive) as c1 from SupportBean;\n";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 10));
            env.assertPropsNew("s0", fieldsOne, new Object[]{"E1", 10});

            env.milestone(0);

            env.advanceTime(120000 + 59999);
            env.sendEventBean(new SupportBean("E2", 20));
            env.assertPropsNew("s0", fieldsOne, new Object[]{"E2", 30});

            env.milestone(1);

            env.advanceTime(120000 + 60000);
            env.sendEventBean(new SupportBean("E3", 4));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
            env.assertThat(() -> {
                assertEquals(0, SupportScheduleHelper.scheduleCountOverall(env.runtime()));
                assertEquals(0, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
            });
        }
    }

    private static class ContextInitTermPatternInclusion implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = "theString,intPrimitive".split(",");
            RegressionPath path = new RegressionPath();
            env.advanceTime(0);

            String contextExpr = "@public create context CtxPerId initiated by pattern [every-distinct (a.theString, 10 sec) a=SupportBean]@Inclusive terminated after 10 sec ";
            env.compileDeploy(contextExpr, path);
            String streamExpr = "@name('s0') context CtxPerId select * from SupportBean(theString = context.a.theString) output last when terminated";
            env.compileDeploy(streamExpr, path).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));

            env.milestone(0);

            env.advanceTime(1000);
            env.sendEventBean(new SupportBean("E2", 2));

            env.milestone(1);

            env.advanceTime(8000);
            env.sendEventBean(new SupportBean("E1", 3));

            env.milestone(2);

            env.advanceTime(9999);
            env.assertListenerNotInvoked("s0");
            env.advanceTime(10000);
            env.assertPropsNew("s0", fields, new Object[]{"E1", 3});

            env.milestone(3);

            env.advanceTime(10100);
            env.sendEventBean(new SupportBean("E2", 4));
            env.sendEventBean(new SupportBean("E1", 5));
            env.assertListenerNotInvoked("s0");

            env.milestone(4);

            env.advanceTime(11000);
            env.assertPropsNew("s0", fields, new Object[]{"E2", 4});

            env.milestone(5);

            env.advanceTime(16100);
            env.sendEventBean(new SupportBean("E2", 6));

            env.advanceTime(20099);
            env.assertListenerNotInvoked("s0");

            env.milestone(6);

            env.advanceTime(20100);
            env.assertPropsNew("s0", fields, new Object[]{"E1", 5});

            env.milestone(7);

            env.advanceTime(26100 - 1);
            env.assertListenerNotInvoked("s0");
            env.advanceTime(26100);
            env.assertPropsNew("s0", fields, new Object[]{"E2", 6});

            env.undeployAll();
            path.clear();

            // test multiple pattern with multiple events
            String contextExprMulti = "@public create context CtxPerId initiated by pattern [every a=SupportBean_S0 -> b=SupportBean_S1]@Inclusive terminated after 10 sec ";
            env.compileDeploy(contextExprMulti, path);
            String streamExprMulti = "@name('s0') context CtxPerId select * from pattern [every a=SupportBean_S0 -> b=SupportBean_S1]";
            env.compileDeploy(streamExprMulti, path).addListener("s0");

            env.milestone(8);

            env.sendEventBean(new SupportBean_S0(10, "S0_1"));

            env.milestone(9);

            env.sendEventBean(new SupportBean_S1(20, "S1_1"));
            env.assertListenerInvoked("s0");

            env.undeployAll();
        }
    }

    public static class ContextStartEndEndSameEventAsAnalyzed implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            RegressionPath path = new RegressionPath();
            String[] fields;
            String epl;

            // same event terminates - not included
            fields = "c1,c2,c3,c4".split(",");
            env.compileDeploy("@public create context MyCtx as " +
                "start SupportBean " +
                "end SupportBean(intPrimitive=11)", path);
            env.compileDeploy("@name('s0') context MyCtx " +
                "select min(intPrimitive) as c1, max(intPrimitive) as c2, sum(intPrimitive) as c3, avg(intPrimitive) as c4 from SupportBean " +
                "output snapshot when terminated", path);
            env.addListener("s0");

            env.milestone(0);

            env.sendEventBean(new SupportBean("E1", 10));
            env.assertListenerNotInvoked("s0");

            env.milestone(1);

            env.sendEventBean(new SupportBean("E2", 11));
            env.assertPropsNew("s0", fields, new Object[]{10, 10, 10, 10d});

            env.milestone(2);

            env.undeployAll();
            path.clear();

            env.milestone(3);

            // same event terminates - included
            fields = "c1,c2,c3,c4".split(",");
            epl = "create schema MyCtxTerminate(theString string);\n" +
                "create context MyCtx as start SupportBean end MyCtxTerminate;\n" +
                "@name('s0') context MyCtx " +
                "select min(intPrimitive) as c1, max(intPrimitive) as c2, sum(intPrimitive) as c3, avg(intPrimitive) as c4 from SupportBean " +
                "output snapshot when terminated;\n" +
                "insert into MyCtxTerminate select theString from SupportBean(intPrimitive=11);\n";
            env.compileDeploy(epl).addListener("s0");

            env.milestone(4);

            env.sendEventBean(new SupportBean("E1", 10));
            env.assertListenerNotInvoked("s0");

            env.milestone(5);

            env.sendEventBean(new SupportBean("E2", 11));
            env.assertPropsNew("s0", fields, new Object[]{10, 11, 21, 10.5d});

            env.undeployAll();

            // test with audit
            epl = "@Audit @public create context AdBreakCtx as initiated by SupportBean(intPrimitive > 0) as ad " +
                " terminated by SupportBean(theString=ad.theString, intPrimitive < 0) as endAd";
            env.compileDeploy(epl, path);
            env.compileDeploy("context AdBreakCtx select count(*) from SupportBean", path);

            env.sendEventBean(new SupportBean("E1", 10));
            env.sendEventBean(new SupportBean("E1", -10));

            env.undeployAll();
        }
    }

    private static class ContextInitTermContextPartitionSelection implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = "c0,c1,c2,c3".split(",");
            env.advanceTime(0);
            RegressionPath path = new RegressionPath();
            AtomicInteger milestone = new AtomicInteger();

            env.compileDeploy("@name('ctx') @public create context MyCtx as initiated by SupportBean_S0 s0 terminated by SupportBean_S1(id=s0.id)", path);
            env.compileDeploy("@name('s0') context MyCtx select context.id as c0, context.s0.p00 as c1, theString as c2, sum(intPrimitive) as c3 from SupportBean#keepall group by theString", path);

            env.advanceTime(1000);
            SupportBean_S0 initOne = new SupportBean_S0(1, "S0_1");
            env.sendEventBean(initOne);

            env.milestoneInc(milestone);

            env.sendEventBean(new SupportBean("E1", 1));

            env.milestoneInc(milestone);

            env.sendEventBean(new SupportBean("E2", 10));

            env.milestoneInc(milestone);

            env.sendEventBean(new SupportBean("E1", 2));

            env.advanceTime(2000);
            SupportBean_S0 initTwo = new SupportBean_S0(2, "S0_2");
            env.sendEventBean(initTwo);

            env.milestoneInc(milestone);

            env.sendEventBean(new SupportBean("E3", 100));
            env.sendEventBean(new SupportBean("E3", 101));

            env.milestoneInc(milestone);

            env.sendEventBean(new SupportBean("E1", 3));
            env.assertPropsPerRowIteratorAnyOrder("s0", fields, new Object[][]{{0, "S0_1", "E1", 6}, {0, "S0_1", "E2", 10}, {0, "S0_1", "E3", 201}, {1, "S0_2", "E1", 3}, {1, "S0_2", "E3", 201}});
            SupportContextPropUtil.assertContextProps(env, "ctx", "MyCtx", new int[]{0, 1}, "startTime,endTime,s0", new Object[][]{{1000L, null, initOne}, {2000L, null, initTwo}});

            env.milestoneInc(milestone);

            env.assertStatement("s0", statement -> {
                // test iterator targeted by context partition id
                SupportSelectorById selectorById = new SupportSelectorById(Collections.singleton(1));
                EPAssertionUtil.assertPropsPerRowAnyOrder(statement.iterator(selectorById), statement.safeIterator(selectorById), fields, new Object[][]{{1, "S0_2", "E1", 3}, {1, "S0_2", "E3", 201}});

                // test iterator targeted by property on triggering event
                SupportSelectorFilteredInitTerm filtered = new SupportSelectorFilteredInitTerm("S0_2");
                EPAssertionUtil.assertPropsPerRowAnyOrder(statement.iterator(filtered), statement.safeIterator(filtered), fields, new Object[][]{{1, "S0_2", "E1", 3}, {1, "S0_2", "E3", 201}});

                // test always-false filter - compare context partition info
                filtered = new SupportSelectorFilteredInitTerm(null);
                assertFalse(statement.iterator(filtered).hasNext());
                EPAssertionUtil.assertEqualsAnyOrder(new Object[]{1000L, 2000L}, filtered.getContextsStartTimes());
                EPAssertionUtil.assertEqualsAnyOrder(new Object[]{"S0_1", "S0_2"}, filtered.getP00PropertyValues());

                try {
                    statement.iterator(new ContextPartitionSelectorSegmented() {
                        public List<Object[]> getPartitionKeys() {
                            return null;
                        }
                    });
                    fail();
                } catch (InvalidContextPartitionSelector ex) {
                    assertTrue("message: " + ex.getMessage(), ex.getMessage().startsWith("Invalid context partition selector, expected an implementation class of any of [ContextPartitionSelectorAll, ContextPartitionSelectorFiltered, ContextPartitionSelectorById] interfaces but received com."));
                }
            });

            env.undeployAll();
        }
    }

    private static class ContextInitTermFilterInitiatedFilterAllTerminated implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            String[] fields = "c1".split(",");
            String epl = "create context MyContext as " +
                "initiated by SupportBean_S0 " +
                "terminated by SupportBean_S1;\n" +
                "@name('s0') context MyContext select sum(intPrimitive) as c1 from SupportBean;\n";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertListenerNotInvoked("s0");

            env.milestone(0);

            env.sendEventBean(new SupportBean_S0(10, "S0_1")); // initiate one

            env.milestone(1);

            env.sendEventBean(new SupportBean("E2", 2));
            env.assertPropsNew("s0", fields, new Object[]{2});

            env.sendEventBean(new SupportBean_S0(11, "S0_2"));  // initiate another

            env.milestone(2);

            env.sendEventBean(new SupportBean("E3", 3));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{5}, {3}});

            env.milestone(3);

            env.sendEventBean(new SupportBean("E4", 4));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{9}, {7}});

            env.sendEventBean(new SupportBean_S1(1, "S1_1"));  // terminate all

            env.milestone(4);

            env.sendEventBean(new SupportBean("E4", 4));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextInitTermFilterInitiatedFilterTerminatedCorrelatedOutputSnapshot implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@public create context EveryNowAndThen as " +
                "initiated by SupportBean_S0 as s0 " +
                "terminated by SupportBean_S1(p10 = s0.p00)", path);

            String[] fields = "c1,c2".split(",");
            env.compileDeploy("@name('s0') context EveryNowAndThen select context.s0.p00 as c1, sum(intPrimitive) as c2 " +
                "from SupportBean#keepall output snapshot when terminated", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.sendEventBean(new SupportBean_S0(100, "G1"));    // starts it

            env.milestone(0);

            env.sendEventBean(new SupportBean("E2", 2));

            env.milestone(1);

            env.sendEventBean(new SupportBean("E3", 3));

            env.milestone(2);

            env.sendEventBean(new SupportBean_S1(200, "GX"));
            env.assertListenerNotInvoked("s0");

            env.milestone(3);

            env.sendEventBean(new SupportBean_S1(200, "G1"));  // terminate
            env.assertPropsNew("s0", fields, new Object[]{"G1", 5});

            env.sendEventBean(new SupportBean_S0(101, "G2"));    // starts new one
            env.sendEventBean(new SupportBean("E4", 4));

            env.milestone(4);

            env.sendEventBean(new SupportBean_S0(102, "G3"));    // also starts new one

            env.sendEventBean(new SupportBean("E5", 5));

            env.milestone(5);

            env.sendEventBean(new SupportBean("E6", 6));

            env.milestone(6);

            env.sendEventBean(new SupportBean_S1(0, "G2"));  // terminate G2
            env.assertPropsNew("s0", fields, new Object[]{"G2", 15});

            env.milestone(7);

            env.sendEventBean(new SupportBean_S1(0, "G3"));  // terminate G3
            env.assertPropsNew("s0", fields, new Object[]{"G3", 11});

            env.undeployAll();
        }
    }

    public static class ContextInitTermFilterAndAfter1Min implements RegressionExecution {

        public void run(RegressionEnvironment env) {

            sendTimeEvent(env, "2002-05-1T8:00:00.000");
            String eplContext = "@Name('CTX') create context CtxInitiated " +
                "initiated by SupportBean_S0 as sb0 " +
                "terminated after 1 minute;\n";
            String eplGrouped = "@Name('S1') context CtxInitiated select theString as c1, sum(intPrimitive) as c2, context.sb0.p00 as c3 from SupportBean;\n";
            env.compileDeploy(eplContext + eplGrouped).addListener("S1");
            String[] fields = "c1,c2,c3".split(",");

            env.sendEventBean(new SupportBean("G1", 1));
            env.assertListenerNotInvoked("S1");

            env.sendEventBean(new SupportBean_S0(1, "SB01"));

            env.sendEventBean(new SupportBean("G2", 2));
            env.assertPropsNew("S1", fields, new Object[]{"G2", 2, "SB01"});

            env.sendEventBean(new SupportBean("G3", 3));
            env.assertPropsNew("S1", fields, new Object[]{"G3", 5, "SB01"});

            env.sendEventBean(new SupportBean_S0(2, "SB02"));

            env.milestone(0);

            env.sendEventBean(new SupportBean("G4", 4));
            env.assertPropsPerRowLastNew("S1", fields, new Object[][]{{"G4", 9, "SB01"}, {"G4", 4, "SB02"}});

            env.milestone(1);

            env.sendEventBean(new SupportBean("G5", 5));
            env.assertPropsPerRowLastNew("S1", fields, new Object[][]{{"G5", 14, "SB01"}, {"G5", 9, "SB02"}});

            sendTimeEvent(env, "2002-05-1T8:01:00.000");

            env.sendEventBean(new SupportBean("G6", 6));
            env.assertListenerNotInvoked("S1");

            // clean up
            env.undeployAll();
        }
    }

    public static class ContextInitTermFilterAndPattern implements RegressionExecution {

        public void run(RegressionEnvironment env) {

            sendTimeEvent(env, "2002-05-1T8:00:00.000");
            String[] fields = "id".split(",");

            String eplContext = "@Name('CTX') create context CtxInitiated " +
                "initiated by SupportBean sb " +
                "terminated by pattern [SupportBean_S0(p00=sb.theString) -> SupportBean_S1(p10=sb.theString)];\n";
            String eplSelect = "@Name('S1') context CtxInitiated " +
                "select id from SupportBean_S2(p20 = context.sb.theString)";
            env.compileDeploy(eplContext + eplSelect).addListener("S1");

            // start context for G1
            env.sendEventBean(new SupportBean("G1", 0));

            env.milestone(0);

            env.sendEventBean(new SupportBean_S2(100, "G1"));
            env.assertPropsNew("S1", fields, new Object[]{100});

            // start context for G2
            env.sendEventBean(new SupportBean("G2", 0));

            env.sendEventBean(new SupportBean_S2(101, "G1"));
            env.assertPropsNew("S1", fields, new Object[]{101});
            env.sendEventBean(new SupportBean_S2(102, "G2"));
            env.sendEventBean(new SupportBean_S2(103, "G3"));
            env.assertPropsNew("S1", fields, new Object[]{102});

            env.milestone(1);

            // end context for G1
            env.sendEventBean(new SupportBean_S0(1, "G1"));

            env.milestone(2);

            env.sendEventBean(new SupportBean_S1(1, "G1"));

            env.milestone(3);

            env.sendEventBean(new SupportBean_S2(201, "G1"));
            env.sendEventBean(new SupportBean_S2(202, "G2"));
            env.sendEventBean(new SupportBean_S2(203, "G3"));
            env.assertPropsNew("S1", fields, new Object[]{202});

            // end context for G2
            env.sendEventBean(new SupportBean_S0(2, "G2"));

            env.milestone(4);

            env.sendEventBean(new SupportBean_S1(2, "G2"));

            env.sendEventBean(new SupportBean_S2(301, "G1"));
            env.sendEventBean(new SupportBean_S2(302, "G2"));
            env.sendEventBean(new SupportBean_S2(303, "G3"));
            env.assertListenerNotInvoked("S1");

            env.undeployAll();
        }
    }

    public static class ContextInitTermPatternAndAfter1Min implements RegressionExecution {

        public void run(RegressionEnvironment env) {
            sendTimeEvent(env, "2002-05-1T8:00:00.000");
            RegressionPath path = new RegressionPath();

            String eplContext = "@Name('CTX') @public create context CtxInitiated " +
                "initiated by pattern [every s0=SupportBean_S0 -> s1=SupportBean_S1(id = s0.id)]" +
                "terminated after 1 minute";
            env.compileDeploy(eplContext, path);

            String[] fields = "c1,c2,c3,c4".split(",");
            String eplGrouped = "@Name('S1') context CtxInitiated " +
                "select theString as c1, sum(intPrimitive) as c2, context.s0.p00 as c3, context.s1.p10 as c4 from SupportBean";
            env.compileDeploy(eplGrouped, path).addListener("S1");

            env.sendEventBean(new SupportBean_S0(10, "S0_1"));
            env.sendEventBean(new SupportBean_S1(20, "S1_1"));
            env.assertListenerNotInvoked("S1");

            env.milestone(0);

            env.sendEventBean(new SupportBean_S1(10, "S1_2"));

            env.milestone(1);

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("S1", fields, new Object[]{"E1", 1, "S0_1", "S1_2"});

            env.sendEventBean(new SupportBean_S0(11, "S0_2"));
            env.sendEventBean(new SupportBean_S1(11, "S1_2"));

            env.milestone(2);

            env.sendEventBean(new SupportBean("E2", 2));
            env.assertPropsPerRowLastNew("S1", fields, new Object[][]{{"E2", 3, "S0_1", "S1_2"}, {"E2", 2, "S0_2", "S1_2"}});

            env.milestone(3);

            env.sendEventBean(new SupportBean("E3", 3));
            env.assertPropsPerRowLastNew("S1", fields, new Object[][]{{"E3", 6, "S0_1", "S1_2"}, {"E3", 5, "S0_2", "S1_2"}});

            env.undeployModuleContaining("S1");
            env.undeployModuleContaining("CTX");
        }
    }

    private static class ContextInitTermScheduleFilterResources implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            // test no-context statement
            env.compileDeploy("@name('s0') select * from SupportBean#time(30)");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertThat(() -> assertEquals(1, SupportScheduleHelper.scheduleCountOverall(env.runtime())));

            env.undeployModuleContaining("s0");
            env.assertThat(() -> assertEquals(0, SupportScheduleHelper.scheduleCountOverall(env.runtime())));

            // test initiated
            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            RegressionPath path = new RegressionPath();
            String eplCtx = "@name('ctx') @public create context EverySupportBean as " +
                "initiated by SupportBean as sb " +
                "terminated after 1 minutes";
            env.compileDeploy(eplCtx, path);

            env.compileDeploy("context EverySupportBean select * from SupportBean_S0#time(2 min) sb0", path);
            env.assertThat(() -> {
                assertEquals(0, SupportScheduleHelper.scheduleCountOverall(env.runtime()));
                assertEquals(1, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
            });

            env.milestone(0);

            env.sendEventBean(new SupportBean("E1", 0));
            env.assertThat(() -> {
                assertEquals(1, SupportScheduleHelper.scheduleCountOverall(env.runtime()));
                assertEquals(2, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
            });

            env.milestone(1);

            env.sendEventBean(new SupportBean_S0(0, "S0_1"));
            env.assertThat(() -> {
                assertEquals(2, SupportScheduleHelper.scheduleCountOverall(env.runtime()));
                assertEquals(2, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
            });

            env.milestone(2);

            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.assertThat(() -> {
                assertEquals(0, SupportScheduleHelper.scheduleCountOverall(env.runtime()));
                assertEquals(1, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
            });

            env.undeployAll();
            env.assertThat(() -> {
                assertEquals(0, SupportScheduleHelper.scheduleCountOverall(env.runtime()));
                assertEquals(0, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
            });
        }
    }

    private static class ContextInitTermPatternInitiatedStraightSelect implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            sendTimeEvent(env, "2002-05-1T08:00:00.000");

            String eplCtx = "@name('ctx') @public create context EverySupportBean as " +
                "initiated by pattern [every (a=SupportBean_S0 or b=SupportBean_S1)] " +
                "terminated after 1 minutes";
            env.compileDeploy(eplCtx, path);

            String[] fields = "c1,c2,c3".split(",");
            env.compileDeploy("@name('s0') context EverySupportBean " +
                "select context.a.id as c1, context.b.id as c2, theString as c3 from SupportBean", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean_S1(2));

            env.milestone(0);

            env.sendEventBean(new SupportBean("E1", 0));
            env.assertPropsNew("s0", fields, new Object[]{null, 2, "E1"});

            env.milestone(1);

            env.sendEventBean(new SupportBean_S0(3));

            env.milestone(2);

            env.sendEventBean(new SupportBean("E2", 0));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{null, 2, "E2"}, {3, null, "E2"}});

            env.undeployAll();
            path.clear();

            // test SODA
            assertSODA(env, path, eplCtx);
        }
    }

    private static class ContextInitTermFilterInitiatedStraightEquals implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            String ctxEPL = "@public create context EverySupportBean as " +
                "initiated by SupportBean(theString like \"I%\") as sb " +
                "terminated after 1 minutes";
            env.compileDeploy(ctxEPL, path);

            String[] fields = "c1".split(",");
            env.compileDeploy("@name('s0') context EverySupportBean select sum(longPrimitive) as c1 from SupportBean(intPrimitive = context.sb.intPrimitive)", path);
            env.addListener("s0");

            env.sendEventBean(makeEvent("E1", -1, -2L));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(makeEvent("I1", 2, 4L)); // counts towards stuff
            env.assertPropsNew("s0", fields, new Object[]{4L});

            env.milestone(0);

            env.sendEventBean(makeEvent("E2", 2, 3L));
            env.assertPropsNew("s0", fields, new Object[]{7L});

            env.milestone(1);

            env.sendEventBean(makeEvent("I2", 3, 14L)); // counts towards stuff
            env.assertPropsNew("s0", fields, new Object[]{14L});

            env.milestone(2);

            env.sendEventBean(makeEvent("E3", 2, 2L));
            env.assertPropsNew("s0", fields, new Object[]{9L});

            env.sendEventBean(makeEvent("E4", 3, 15L));
            env.assertPropsNew("s0", fields, new Object[]{29L});

            env.milestone(3);

            sendTimeEvent(env, "2002-05-1T08:01:30.000");

            env.sendEventBean(makeEvent("E", -1, -2L));
            env.assertListenerNotInvoked("s0");

            // test SODA
            env.undeployAll();
            env.eplToModelCompileDeploy(ctxEPL).undeployAll();
        }
    }

    private static class ContextInitTermFilterAllOperators implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            AtomicInteger milestone = new AtomicInteger();

            // test plain
            env.compileDeploy("@public create context EverySupportBean as " +
                "initiated by SupportBean_S0 as sb " +
                "terminated after 10 days 5 hours 2 minutes 1 sec 11 milliseconds", path);

            tryOperator(env, path, milestone, "context.sb.id = intBoxed", new Object[][]{{10, true}, {9, false}, {null, false}});
            tryOperator(env, path, milestone, "intBoxed = context.sb.id", new Object[][]{{10, true}, {9, false}, {null, false}});

            tryOperator(env, path, milestone, "context.sb.id > intBoxed", new Object[][]{{11, false}, {10, false}, {9, true}, {8, true}});
            tryOperator(env, path, milestone, "context.sb.id >= intBoxed", new Object[][]{{11, false}, {10, true}, {9, true}, {8, true}});
            tryOperator(env, path, milestone, "context.sb.id < intBoxed", new Object[][]{{11, true}, {10, false}, {9, false}, {8, false}});
            tryOperator(env, path, milestone, "context.sb.id <= intBoxed", new Object[][]{{11, true}, {10, true}, {9, false}, {8, false}});

            tryOperator(env, path, milestone, "intBoxed < context.sb.id", new Object[][]{{11, false}, {10, false}, {9, true}, {8, true}});
            tryOperator(env, path, milestone, "intBoxed <= context.sb.id", new Object[][]{{11, false}, {10, true}, {9, true}, {8, true}});
            tryOperator(env, path, milestone, "intBoxed > context.sb.id", new Object[][]{{11, true}, {10, false}, {9, false}, {8, false}});
            tryOperator(env, path, milestone, "intBoxed >= context.sb.id", new Object[][]{{11, true}, {10, true}, {9, false}, {8, false}});

            tryOperator(env, path, milestone, "intBoxed in (context.sb.id)", new Object[][]{{11, false}, {10, true}, {9, false}, {8, false}});
            tryOperator(env, path, milestone, "intBoxed between context.sb.id and context.sb.id", new Object[][]{{11, false}, {10, true}, {9, false}, {8, false}});

            tryOperator(env, path, milestone, "context.sb.id != intBoxed", new Object[][]{{10, false}, {9, true}, {null, false}});
            tryOperator(env, path, milestone, "intBoxed != context.sb.id", new Object[][]{{10, false}, {9, true}, {null, false}});

            tryOperator(env, path, milestone, "intBoxed not in (context.sb.id)", new Object[][]{{11, true}, {10, false}, {9, true}, {8, true}});
            tryOperator(env, path, milestone, "intBoxed not between context.sb.id and context.sb.id", new Object[][]{{11, true}, {10, false}, {9, true}, {8, true}});

            tryOperator(env, path, milestone, "context.sb.id is intBoxed", new Object[][]{{10, true}, {9, false}, {null, false}});
            tryOperator(env, path, milestone, "intBoxed is context.sb.id", new Object[][]{{10, true}, {9, false}, {null, false}});

            tryOperator(env, path, milestone, "context.sb.id is not intBoxed", new Object[][]{{10, false}, {9, true}, {null, true}});
            tryOperator(env, path, milestone, "intBoxed is not context.sb.id", new Object[][]{{10, false}, {9, true}, {null, true}});

            // try coercion
            tryOperator(env, path, milestone, "context.sb.id = shortBoxed", new Object[][]{{(short) 10, true}, {(short) 9, false}, {null, false}});
            tryOperator(env, path, milestone, "shortBoxed = context.sb.id", new Object[][]{{(short) 10, true}, {(short) 9, false}, {null, false}});

            tryOperator(env, path, milestone, "context.sb.id > shortBoxed", new Object[][]{{(short) 11, false}, {(short) 10, false}, {(short) 9, true}, {(short) 8, true}});
            tryOperator(env, path, milestone, "shortBoxed < context.sb.id", new Object[][]{{(short) 11, false}, {(short) 10, false}, {(short) 9, true}, {(short) 8, true}});

            tryOperator(env, path, milestone, "shortBoxed in (context.sb.id)", new Object[][]{{(short) 11, false}, {(short) 10, true}, {(short) 9, false}, {(short) 8, false}});

            env.undeployAll();
        }

        private static void tryOperator(RegressionEnvironment env, RegressionPath path, AtomicInteger milestone, String operator, Object[][] testdata) {

            env.compileDeploy("@name('s0') context EverySupportBean " +
                "select theString as c0,intPrimitive as c1,context.sb.p00 as c2 " +
                "from SupportBean(" + operator + ")", path);
            env.addListener("s0");

            // initiate
            env.sendEventBean(new SupportBean_S0(10, "S01"));

            env.milestoneInc(milestone);

            for (int i = 0; i < testdata.length; i++) {
                SupportBean bean = new SupportBean();
                Object testValue = testdata[i][0];
                if (testValue instanceof Integer) {
                    bean.setIntBoxed((Integer) testValue);
                } else {
                    bean.setShortBoxed((Short) testValue);
                }
                boolean expected = (Boolean) testdata[i][1];

                env.sendEventBean(bean);
                env.assertListenerInvokedFlag("s0", expected, "Failed at " + i);
            }
            env.undeployModuleContaining("s0");
        }
    }

    private static class ContextInitTermFilterBooleanOperator implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@public create context EverySupportBean as " +
                "initiated by SupportBean_S0 as sb " +
                "terminated after 10 days 5 hours 2 minutes 1 sec 11 milliseconds", path);

            env.milestone(0);

            String[] fields = "c0,c1,c2".split(",");
            env.compileDeploy("@name('s0') context EverySupportBean " +
                "select theString as c0,intPrimitive as c1,context.sb.p00 as c2 " +
                "from SupportBean(intPrimitive + context.sb.id = 5)", path);
            env.addListener("s0");

            env.milestone(1);

            env.sendEventBean(new SupportBean("E1", 2));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean_S0(3, "S01"));

            env.milestone(2);

            env.sendEventBean(new SupportBean("E2", 2));
            env.assertPropsNew("s0", fields, new Object[]{"E2", 2, "S01"});

            env.sendEventBean(new SupportBean_S0(3, "S02"));

            env.sendEventBean(new SupportBean("E3", 2));
            env.assertPropsPerRowLastNewAnyOrder("s0", fields, new Object[][]{{"E3", 2, "S01"}, {"E3", 2, "S02"}});

            env.milestone(3);

            env.sendEventBean(new SupportBean_S0(4, "S03"));

            env.sendEventBean(new SupportBean("E4", 2));
            env.assertPropsPerRowLastNewAnyOrder("s0", fields, new Object[][]{{"E4", 2, "S01"}, {"E4", 2, "S02"}});

            env.sendEventBean(new SupportBean("E5", 1));
            env.assertPropsPerRowLastNewAnyOrder("s0", fields, new Object[][]{{"E5", 1, "S03"}});

            env.undeployAll();
        }
    }

    private static class ContextInitTermTerminateTwoContextSameTime implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            sendTimeEvent(env, "2002-05-1T08:00:00.000");

            String eplContext = "@Name('CTX') @public create context CtxInitiated " +
                "initiated by SupportBean_S0 as sb0 " +
                "terminated after 1 minute";
            env.compileDeploy(eplContext, path);

            String[] fields = "c1,c2,c3".split(",");
            String eplGrouped = "@Name('s0') context CtxInitiated select theString as c1, sum(intPrimitive) as c2, context.sb0.p00 as c3 from SupportBean";
            env.compileDeploy(eplGrouped, path).addListener("s0");

            env.sendEventBean(new SupportBean("G1", 1));
            env.assertListenerNotInvoked("s0");

            env.milestone(0);

            env.sendEventBean(new SupportBean_S0(1, "SB01"));

            env.milestone(1);

            env.sendEventBean(new SupportBean("G2", 2));
            env.assertPropsNew("s0", fields, new Object[]{"G2", 2, "SB01"});

            env.milestone(2);

            env.sendEventBean(new SupportBean("G3", 3));
            env.assertPropsNew("s0", fields, new Object[]{"G3", 5, "SB01"});

            env.milestone(3);

            env.sendEventBean(new SupportBean_S0(2, "SB02"));

            env.milestone(4);

            env.sendEventBean(new SupportBean("G4", 4));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"G4", 9, "SB01"}, {"G4", 4, "SB02"}});

            env.milestone(5);

            env.sendEventBean(new SupportBean("G5", 5));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"G5", 14, "SB01"}, {"G5", 9, "SB02"}});

            env.milestone(6);

            sendTimeEvent(env, "2002-05-1T08:01:00.000");

            env.milestone(7);

            env.sendEventBean(new SupportBean("G6", 6));
            env.assertListenerNotInvoked("s0");

            // clean up
            env.undeployModuleContaining("s0");
            env.undeployModuleContaining("CTX");
        }
    }

    private static class ContextInitTermOutputSnapshotWhenTerminated implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = "c1".split(",");
            RegressionPath path = new RegressionPath();
            sendTimeEvent(env, "2002-05-1T08:00:00.000");

            env.compileDeploy("@public create context EveryMinute as " +
                "initiated by pattern[every timer:at(*, *, *, *, *)] " +
                "terminated after 1 min", path);

            // test when-terminated and snapshot
            String epl = "@name('s0') context EveryMinute select sum(intPrimitive) as c1 from SupportBean output snapshot when terminated";
            env.compileDeploy(epl, path).addListener("s0");

            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.sendEventBean(new SupportBean("E1", 1));

            env.milestone(0);

            sendTimeEvent(env, "2002-05-1T08:01:10.000");
            env.sendEventBean(new SupportBean("E2", 2));

            sendTimeEvent(env, "2002-05-1T08:01:59.999");
            env.sendEventBean(new SupportBean("E3", 3));
            env.assertListenerNotInvoked("s0");

            env.milestone(1);

            // terminate
            sendTimeEvent(env, "2002-05-1T08:02:00.000");
            env.assertPropsNew("s0", fields, new Object[]{1 + 2 + 3});

            env.milestone(2);

            sendTimeEvent(env, "2002-05-1T08:02:01.000");
            env.sendEventBean(new SupportBean("E4", 4));
            env.sendEventBean(new SupportBean("E5", 5));
            env.sendEventBean(new SupportBean("E6", 6));
            env.assertListenerNotInvoked("s0");

            env.milestone(3);

            // terminate
            sendTimeEvent(env, "2002-05-1T08:03:00.000");
            env.assertPropsNew("s0", fields, new Object[]{4 + 5 + 6});

            env.undeployModuleContaining("s0");

            // test late-coming statement without "terminated"
            env.compileDeploy("@name('s0') context EveryMinute " +
                "select context.id as c0, sum(intPrimitive) as c1 from SupportBean output snapshot every 2 events", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean("E10", 1));
            env.sendEventBean(new SupportBean("E11", 2));
            env.assertListenerNotInvoked("s0");

            env.milestone(4);

            sendTimeEvent(env, "2002-05-1T08:04:00.000");
            env.sendEventBean(new SupportBean("E12", 3));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean("E13", 4));
            env.assertPropsNew("s0", fields, new Object[]{7});

            env.milestone(5);

            // terminate
            sendTimeEvent(env, "2002-05-1T08:05:00.000");
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextInitTermOutputAllEvery2AndTerminated implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            RegressionPath path = new RegressionPath();

            env.compileDeploy("@public create context EveryMinute as " +
                "initiated by pattern[every timer:at(*, *, *, *, *)] " +
                "terminated after 1 min", path);

            // test when-terminated and every 2 events output all with group by
            String[] fields = "c1,c2".split(",");
            env.compileDeploy("@name('s0') context EveryMinute " +
                "select theString as c1, sum(intPrimitive) as c2 from SupportBean group by theString output all every 2 events and when terminated order by theString asc", path);
            env.addListener("s0");

            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.sendEventBean(new SupportBean("E1", 1));

            env.milestone(0);

            sendTimeEvent(env, "2002-05-1T08:01:10.000");
            env.sendEventBean(new SupportBean("E1", 2));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E1", 1 + 2}});

            env.milestone(1);

            sendTimeEvent(env, "2002-05-1T08:01:59.999");
            env.sendEventBean(new SupportBean("E2", 3));
            env.assertListenerNotInvoked("s0");

            env.milestone(2);

            // terminate
            sendTimeEvent(env, "2002-05-1T08:02:00.000");
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E1", 1 + 2}, {"E2", 3}});

            sendTimeEvent(env, "2002-05-1T08:02:01.000");
            env.sendEventBean(new SupportBean("E4", 4));
            env.sendEventBean(new SupportBean("E5", 5));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E4", 4}, {"E5", 5}});

            env.milestone(3);

            env.sendEventBean(new SupportBean("E6", 6));
            env.assertListenerNotInvoked("s0");

            env.milestone(4);

            env.sendEventBean(new SupportBean("E4", 10));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E4", 14}, {"E5", 5}, {"E6", 6}});

            env.milestone(5);

            // terminate
            sendTimeEvent(env, "2002-05-1T08:03:00.000");
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E4", 14}, {"E5", 5}, {"E6", 6}});

            env.sendEventBean(new SupportBean("E1", -1));

            env.milestone(6);

            env.sendEventBean(new SupportBean("E6", -2));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E1", -1}, {"E6", -2}});

            env.undeployAll();
        }
    }

    private static class ContextInitTermOutputWhenExprWhenTerminatedCondition implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            env.compileDeploy("@public create context EveryMinute as " +
                "initiated by pattern[every timer:at(*, *, *, *, *)] " +
                "terminated after 1 min", path);

            // test when-terminated and every 2 events output all with group by
            String[] fields = "c0".split(",");
            String epl = "@name('s0') context EveryMinute " +
                "select theString as c0 from SupportBean output when count_insert>1 and when terminated and count_insert>0";
            env.compileDeploy(epl, path).addListener("s0");

            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.sendEventBean(new SupportBean("E1", 1));
            env.assertListenerNotInvoked("s0");

            env.milestone(0);

            env.sendEventBean(new SupportBean("E2", 1));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E1"}, {"E2"}});

            env.milestone(1);

            sendTimeEvent(env, "2002-05-1T08:01:59.999");
            env.sendEventBean(new SupportBean("E3", 3));
            env.assertListenerNotInvoked("s0");

            env.milestone(2);

            // terminate, new context partition
            sendTimeEvent(env, "2002-05-1T08:02:00.000");
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E3"}});

            sendTimeEvent(env, "2002-05-1T08:02:10.000");
            env.sendEventBean(new SupportBean("E4", 4));
            env.assertListenerNotInvoked("s0");

            env.milestone(3);

            env.sendEventBean(new SupportBean("E5", 5));
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E4"}, {"E5"}});

            sendTimeEvent(env, "2002-05-1T08:03:00.000");
            env.assertListenerNotInvoked("s0");

            env.eplToModelCompileDeploy(epl, path).undeployAll();
        }
    }

    private static class ContextInitTermOutputOnlyWhenTerminatedCondition implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            RegressionPath path = new RegressionPath();

            env.compileDeploy("@public create context EveryMinute as " +
                "initiated by pattern[every timer:at(*, *, *, *, *)] " +
                "terminated after 1 min", path);

            // test when-terminated and every 2 events output all with group by
            String[] fields = "c0".split(",");
            String epl = "@name('s0') context EveryMinute " +
                "select theString as c0 from SupportBean output when terminated and count_insert > 0";
            env.compileDeploy(epl, path);
            env.addListener("s0");

            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.sendEventBean(new SupportBean("E1", 1));

            env.milestone(0);

            env.sendEventBean(new SupportBean("E2", 1));
            env.assertListenerNotInvoked("s0");

            env.milestone(1);

            // terminate, new context partition
            sendTimeEvent(env, "2002-05-1T08:02:00.000");
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E1"}, {"E2"}});

            env.milestone(2);

            // terminate, new context partition
            sendTimeEvent(env, "2002-05-1T08:03:00.000");
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ContextInitTermOutputOnlyWhenSetAndWhenTerminatedSet implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            RegressionPath path = new RegressionPath();
            String eplContext = "@public create context EveryMinute as " +
                "initiated by pattern[every timer:at(*, *, *, *, *)] " +
                "terminated after 1 min";
            env.compileDeploy(eplContext, path);

            // include then-set and both real-time and terminated output
            String eplVariable = "@name('var') @public create variable int myvar = 0";
            env.compileDeploy(eplVariable, path);
            String eplOne = "@name('s0') context EveryMinute select theString as c0 from SupportBean " +
                "output when true " +
                "then set myvar=1 " +
                "and when terminated " +
                "then set myvar=2";
            env.compileDeploy(eplOne, path).addListener("s0");

            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.sendEventBean(new SupportBean("E3", 3));
            assertEquals(1, env.runtime().getVariableService().getVariableValue(env.deploymentId("var"), "myvar"));
            env.assertListenerInvoked("s0");

            sendTimeEvent(env, "2002-05-1T08:02:00.000"); // terminate, new context partition
            env.assertListenerInvoked("s0");
            assertEquals(2, env.runtime().getVariableService().getVariableValue(env.deploymentId("var"), "myvar"));

            env.undeployModuleContaining("s0");
            env.undeployModuleContaining("var");
            env.undeployAll();
            path.clear();

            env.compileDeploy(eplContext, path);
            env.compileDeploy(eplVariable, path);
            assertSODA(env, path, eplOne);
        }

        public EnumSet<RegressionFlag> flags() {
            return EnumSet.of(RegressionFlag.RUNTIMEOPS);
        }
    }

    private static class ContextInitTermOutputOnlyWhenTerminatedThenSet implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            String[] fields = "c0".split(",");
            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            RegressionPath path = new RegressionPath();

            env.compileDeploy("@name('var') @public create variable int myvar = 0", path);
            env.compileDeploy("@public create context EverySupportBeanS0 as " +
                "initiated by SupportBean_S0 as s0 " +
                "terminated after 1 min", path);

            // include only-terminated output with set
            env.runtimeSetVariable("var", "myvar", 0);
            String eplTwo = "@name('s0') context EverySupportBeanS0 select theString as c0 from SupportBean " +
                "output when terminated " +
                "then set myvar=10";
            env.compileDeploy(eplTwo, path).addListener("s0");

            env.sendEventBean(new SupportBean_S0(1, "S0"));

            env.sendEventBean(new SupportBean("E4", 4));
            env.assertListenerNotInvoked("s0");

            // terminate, new context partition
            sendTimeEvent(env, "2002-05-1T08:01:00.000");
            env.assertPropsPerRowLastNew("s0", fields, new Object[][]{{"E4"}});
            env.assertThat(() -> assertEquals(10, env.runtime().getVariableService().getVariableValue(env.deploymentId("var"), "myvar")));

            assertSODA(env, path, eplTwo);
        }
    }

    public static class ContextInitTermCrontab implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            sendTimeEvent(env, "2002-05-1T08:00:00.000");
            RegressionPath path = new RegressionPath();

            env.compileDeploy("@public create context EveryMinute as " +
                "initiated by pattern[every timer:at(*, *, *, *, *)] " +
                "terminated after 3 min", path);

            String[] fields = "c1,c2".split(",");
            env.compileDeploy("@name('s0') @IterableUnbound context EveryMinute select theString as c1, sum(intPrimitive) as c2 from SupportBean", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean("E1", 10));
            env.assertListenerNotInvoked("s0");
            env.assertThat(() -> {
                assertEquals(0, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 0);
            });
            env.assertPropsPerRowIterator("s0", fields, null);

            env.milestone(0);

            sendTimeEvent(env, "2002-05-1T08:01:00.000");

            env.assertThat(() -> {
                assertEquals(1, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 1);
            });
            env.milestone(1);

            env.sendEventBean(new SupportBean("E2", 5));
            Object[][] expected = new Object[][]{{"E2", 5}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            sendTimeEvent(env, "2002-05-1T08:01:59.999");

            env.assertThat(() -> {
                assertEquals(1, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 1);
            });
            env.milestone(2);

            env.sendEventBean(new SupportBean("E3", 6));
            expected = new Object[][]{{"E3", 11}};
            env.assertPropsPerRowLastNew("s0", fields, expected);

            env.milestone(3);

            env.assertPropsPerRowIterator("s0", fields, expected);

            sendTimeEvent(env, "2002-05-1T08:02:00.000");

            env.assertThat(() -> {
                assertEquals(2, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 2);
            });
            env.milestone(4);

            env.sendEventBean(new SupportBean("E4", 7));
            expected = new Object[][]{{"E4", 18}, {"E4", 7}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            sendTimeEvent(env, "2002-05-1T08:02:59.999");
            env.assertThat(() -> {
                assertEquals(2, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 2);
            });
            env.milestone(5);

            env.sendEventBean(new SupportBean("E5", 8));
            expected = new Object[][]{{"E5", 26}, {"E5", 15}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            sendTimeEvent(env, "2002-05-1T08:03:00.000");
            env.assertThat(() -> {
                assertEquals(3, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 3);
            });
            env.milestone(6);

            env.sendEventBean(new SupportBean("E6", 9));
            expected = new Object[][]{{"E6", 35}, {"E6", 24}, {"E6", 9}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            sendTimeEvent(env, "2002-05-1T08:04:00.000");
            env.assertThat(() -> {
                assertEquals(3, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 3);
            });
            env.milestone(7);

            env.sendEventBean(new SupportBean("E7", 10));
            expected = new Object[][]{{"E7", 34}, {"E7", 19}, {"E7", 10}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            sendTimeEvent(env, "2002-05-1T08:05:00.000");

            env.assertThat(() -> {
                assertEquals(3, SupportFilterServiceHelper.getFilterSvcCountApprox(env));
                AgentInstanceAssertionUtil.assertInstanceCounts(env, "s0", 3);
            });
            env.milestone(8);

            env.sendEventBean(new SupportBean("E8", 11));
            expected = new Object[][]{{"E8", 30}, {"E8", 21}, {"E8", 11}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            // assert certain keywords are valid: last keyword, timezone
            env.compileDeploy("create context CtxMonthly1 start (0, 0, 1, *, *, 0) end(59, 23, last, *, *, 59)");
            env.compileDeploy("create context CtxMonthly2 start (0, 0, 1, *, *) end(59, 23, last, *, *)");
            env.compileDeploy("create context CtxMonthly3 start (0, 0, 1, *, *, 0, 'GMT-5') end(59, 23, last, *, *, 59, 'GMT-8')");
            env.tryInvalidCompile("create context CtxMonthly4 start (0) end(*,*,*,*,*)",
                "Invalid schedule specification: Invalid number of crontab parameters, expecting between 5 and 7 parameters, received 1 [create context CtxMonthly4 start (0) end(*,*,*,*,*)]");
            env.tryInvalidCompile("create context CtxMonthly4 start (*,*,*,*,*) end(*,*,*,*,*,*,*,*)",
                "Invalid schedule specification: Invalid number of crontab parameters, expecting between 5 and 7 parameters, received 8 [create context CtxMonthly4 start (*,*,*,*,*) end(*,*,*,*,*,*,*,*)]");

            // test invalid -after
            env.tryInvalidCompile("create context CtxMonthly4 start after 1 second end after -1 seconds",
                "Invalid negative time period expression '-1 seconds' [create context CtxMonthly4 start after 1 second end after -1 seconds]");
            env.tryInvalidCompile("create context CtxMonthly4 start after -1 second end after 1 seconds",
                "Invalid negative time period expression '-1 seconds' [create context CtxMonthly4 start after -1 second end after 1 seconds]");

            env.undeployAll();
        }
    }

    private static class ContextStartEndStartNowCalMonthScoped implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            sendCurrentTime(env, "2002-02-01T09:00:00.000");
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@public create context MyCtx start SupportBean_S1 end after 1 month", path);
            env.compileDeploy("@name('s0') context MyCtx select * from SupportBean", path).addListener("s0");

            env.sendEventBean(new SupportBean_S1(1));

            env.milestone(0);

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertListenerInvoked("s0");

            env.milestone(1);

            sendCurrentTimeWithMinus(env, "2002-03-01T09:00:00.000", 1);
            env.sendEventBean(new SupportBean("E2", 2));
            env.assertListenerInvoked("s0");

            env.milestone(2);

            sendCurrentTime(env, "2002-03-01T09:00:00.000");
            env.sendEventBean(new SupportBean("E3", 3));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    public static class ContextInitTermAggregationGrouped implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            String[] fields = "c0,c1".split(",");
            String epl = "@buseventtype @public create schema SummedEvent(grp string, key string, value int);\n" +
                "@buseventtype @public create schema InitEvent(grp string);\n" +
                "@buseventtype @public create schema TermEvent(grp string);\n";

            epl += "@Name('Ctx1') create context MyContext " +
                "initiated by InitEvent as i " +
                "terminated by TermEvent(grp = i.grp);\n";

            epl += "@Name('s0') context MyContext " +
                "select key as c0, sum(value) as c1 " +
                "from SummedEvent(grp = context.i.grp) group by key;\n";
            env.compileDeploy(epl, new RegressionPath());
            env.addListener("s0");

            env.milestone(0);

            sendInitEvent(env, "CP1");
            assertPartitionInfo(env);

            env.milestone(1);

            assertPartitionInfo(env);
            sendInitEvent(env, "CP2");

            env.milestone(2);

            sendSummedEvent(env, "CP2", "G1", 100);
            env.assertPropsNew("s0", fields, new Object[]{"G1", 100});

            env.milestone(3);

            sendSummedEvent(env, "CP1", "G1", 10);
            env.assertPropsNew("s0", fields, new Object[]{"G1", 10});

            env.milestone(4);

            sendSummedEvent(env, "CP1", "G2", 5);
            env.assertPropsNew("s0", fields, new Object[]{"G2", 5});

            env.milestone(5);

            sendSummedEvent(env, "CP2", "G1", 101);
            env.assertPropsNew("s0", fields, new Object[]{"G1", 201});

            env.milestone(6);

            sendSummedEvent(env, "CP1", "G1", 11);
            env.assertPropsNew("s0", fields, new Object[]{"G1", 21});

            env.milestone(7);

            sendTermEvent(env, "CP1");
            sendSummedEvent(env, "CP1", "G1", -1);
            env.assertListenerNotInvoked("s0");

            env.milestone(8);

            sendTermEvent(env, "CP2");
            sendSummedEvent(env, "CP1", "G1", -1);
            sendSummedEvent(env, "CP2", "G1", -1);
            env.assertListenerNotInvoked("s0");

            env.milestone(9);

            sendInitEvent(env, "CP1");
            sendSummedEvent(env, "CP1", "G1", 1000);
            env.assertPropsNew("s0", fields, new Object[]{"G1", 1000});

            env.undeployAll();
            env.milestone(10);
        }

        private void assertPartitionInfo(RegressionEnvironment env) {
            env.assertThat(() -> {
                EPContextPartitionService partitionAdmin = env.runtime().getContextPartitionService();
                ContextPartitionCollection partitions = partitionAdmin.getContextPartitions(env.deploymentId("Ctx1"), "MyContext", ContextPartitionSelectorAll.INSTANCE);
                assertEquals(1, partitions.getIdentifiers().size());
                ContextPartitionIdentifierInitiatedTerminated ident = (ContextPartitionIdentifierInitiatedTerminated) partitions.getIdentifiers().values().iterator().next();
                assertEquals(null, ident.getEndTime());
                assertNotNull(ident.getProperties().get("i"));
            });
        }

        private void sendInitEvent(RegressionEnvironment env, String grp) {
            env.sendEventMap(Collections.singletonMap("grp", grp), "InitEvent");
        }

        private void sendTermEvent(RegressionEnvironment env, String grp) {
            env.sendEventMap(Collections.singletonMap("grp", grp), "TermEvent");
        }

        private void sendSummedEvent(RegressionEnvironment env, String grp, String key, int value) {
            env.sendEventMap(CollectionUtil.buildMap("grp", grp, "key", key, "value", value), "SummedEvent");
        }
    }

    public static class ContextInitTermPrevPrior implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            sendTimeEvent(env, "2002-05-1T8:00:00.000");
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@name('ctx') @public create context NineToFive as start (0, 9, *, *, *) end (0, 17, *, *, *)", path);

            String[] fields = "col1,col2,col3,col4,col5".split(",");
            env.compileDeploy("@name('s0') context NineToFive " +
                "select prev(theString) as col1, prevwindow(sb) as col2, prevtail(theString) as col3, prior(1, theString) as col4, sum(intPrimitive) as col5 " +
                "from SupportBean#keepall() as sb", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean());
            env.assertListenerNotInvoked("s0");

            env.milestone(0);

            // now started
            sendTimeEvent(env, "2002-05-1T9:00:00.000");
            SupportBean event1 = new SupportBean("E1", 1);
            env.sendEventBean(event1);
            Object[][] expected = new Object[][]{{null, new SupportBean[]{event1}, "E1", null, 1}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            env.milestone(1);

            SupportBean event2 = new SupportBean("E2", 2);
            env.sendEventBean(event2);
            env.assertPropsNew("s0", fields, new Object[]{"E1", new SupportBean[]{event2, event1}, "E1", "E1", 3});

            env.milestone(2);

            // now gone
            sendTimeEvent(env, "2002-05-1T17:00:00.000");
            env.assertPropsPerRowIterator("s0", fields, null);

            env.milestone(3);

            env.sendEventBean(new SupportBean());
            env.assertListenerNotInvoked("s0");

            env.milestone(4);

            // now started
            sendTimeEvent(env, "2002-05-2T9:00:00.000");

            env.milestone(5);

            SupportBean event3 = new SupportBean("E3", 9);
            env.sendEventBean(event3);
            expected = new Object[][]{{null, new SupportBean[]{event3}, "E3", null, 9}};
            env.assertPropsPerRowLastNew("s0", fields, expected);
            env.assertPropsPerRowIterator("s0", fields, expected);

            env.undeployAll();
        }
    }

    private static void tryAssertionNoTerminationConditionOverlapping(RegressionEnvironment env, AtomicInteger milestone, boolean soda) {

        RegressionPath path = new RegressionPath();
        env.compileDeploy(soda, "@name('ctx') @public create context SupportBeanInstanceCtx as initiated by SupportBean as sb", path);
        env.compileDeploy(soda, "@name('s0') context SupportBeanInstanceCtx " +
            "select id, context.sb.intPrimitive as sbint, context.startTime as starttime, context.endTime as endtime from SupportBean_S0(p00=context.sb.theString)", path);
        env.addListener("s0");
        String[] fields = "id,sbint,starttime,endtime".split(",");
        env.assertStatement("ctx", statement -> {
            assertEquals(StatementType.CREATE_CONTEXT, statement.getProperty(StatementProperty.STATEMENTTYPE));
            assertEquals("SupportBeanInstanceCtx", statement.getProperty(StatementProperty.CREATEOBJECTNAME));
        });

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean("P1", 100));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean("P2", 200));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10, "P2"));
        env.assertPropsNew("s0", fields, new Object[]{10, 200, 5L, null});

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(20, "P1"));
        env.assertPropsNew("s0", fields, new Object[]{20, 100, 5L, null});

        env.undeployAll();
    }

    private static void tryAssertionNoTerminationConditionNonoverlapping(RegressionEnvironment env, AtomicInteger milestone, boolean soda) {

        RegressionPath path = new RegressionPath();
        env.compileDeploy(soda, "@public create context SupportBeanInstanceCtx as start SupportBean as sb", path);
        env.compileDeploy(soda, "@name('s0') context SupportBeanInstanceCtx " +
            "select id, context.sb.intPrimitive as sbint, context.startTime as starttime, context.endTime as endtime from SupportBean_S0(p00=context.sb.theString)", path);
        env.addListener("s0");
        String[] fields = "id,sbint,starttime,endtime".split(",");

        env.sendEventBean(new SupportBean("P1", 100));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean("P2", 200));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10, "P2"));
        env.assertListenerNotInvoked("s0");

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(20, "P1"));
        env.assertPropsNew("s0", fields, new Object[]{20, 100, 5L, null});

        env.undeployAll();
    }

    private static void sendTimeEvent(RegressionEnvironment env, String time) {
        env.advanceTime(DateTime.parseDefaultMSec(time));
    }

    private static SupportBean makeEvent(String theString, int intPrimitive, long longPrimitive) {
        SupportBean bean = new SupportBean(theString, intPrimitive);
        bean.setLongPrimitive(longPrimitive);
        return bean;
    }

    private static void assertSODA(RegressionEnvironment env, RegressionPath path, String epl) {
        env.eplToModelCompileDeploy(epl, path).undeployAll();
    }

    private static void sendCurrentTime(RegressionEnvironment env, String time) {
        env.advanceTime(DateTime.parseDefaultMSec(time));
    }

    private static void sendCurrentTimeWithMinus(RegressionEnvironment env, String time, long minus) {
        env.advanceTime(DateTime.parseDefaultMSec(time) - minus);
    }

}
