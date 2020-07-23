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
package com.espertech.esper.common.internal.epl.index.base;

import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;

/**
 * Table of events allowing add and remove. Lookup in table is coordinated
 * through the underlying implementation.
 */
public interface EventTableFactory {
    public Class getEventTableClass();

    public EventTable[] makeEventTables(ExprEvaluatorContext exprEvaluatorContext, Integer subqueryNumber);

    public String toQueryPlan();
}
