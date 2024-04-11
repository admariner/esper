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
package com.espertech.esper.regressionlib.suite.epl.variable;

import com.espertech.esper.common.internal.support.SupportBean;
import com.espertech.esper.common.internal.support.SupportBean_S0;
import com.espertech.esper.regressionlib.framework.RegressionEnvironment;
import com.espertech.esper.regressionlib.framework.RegressionExecution;
import com.espertech.esper.regressionlib.framework.RegressionFlag;
import com.espertech.esper.regressionlib.framework.RegressionPath;

import java.util.EnumSet;

import static org.junit.Assert.assertTrue;

public class EPLVariablesPerf implements RegressionExecution {
    @Override
    public EnumSet<RegressionFlag> flags() {
        return EnumSet.of(RegressionFlag.EXCLUDEWHENINSTRUMENTED, RegressionFlag.PERFORMANCE);
    }

    public void run(RegressionEnvironment env) {
        RegressionPath path = new RegressionPath();
        env.compileDeploy("@public create window MyWindow#keepall as SupportBean", path);
        env.compileDeploy("insert into MyWindow select * from SupportBean", path);
        env.compileDeploy("@public create const variable String MYCONST = 'E331'", path);

        for (int i = 0; i < 10000; i++) {
            env.sendEventBean(new SupportBean("E" + i, i * -1));
        }

        // test join
        env.compileDeploy("@name('s0') select * from SupportBean_S0 s0 unidirectional, MyWindow sb where theString = MYCONST", path);
        env.addListener("s0");

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            env.sendEventBean(new SupportBean_S0(i, "E" + i));
            env.assertPropsNew("s0", "sb.theString,sb.intPrimitive".split(","), new Object[]{"E331", -331});
        }
        long delta = System.currentTimeMillis() - start;
        assertTrue("delta=" + delta, delta < 600);
        env.undeployModuleContaining("s0");

        // test subquery
        env.compileDeploy("@name('s0') select * from SupportBean_S0 where exists (select * from MyWindow where theString = MYCONST)", path);
        env.addListener("s0");

        start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            env.sendEventBean(new SupportBean_S0(i, "E" + i));
            env.assertListenerInvoked("s0");
        }
        delta = System.currentTimeMillis() - start;
        assertTrue("delta=" + delta, delta < 500);

        env.undeployModuleContaining("s0");
        env.undeployAll();
    }
}
