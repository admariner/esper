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
package com.espertech.esper.common.internal.epl.virtualdw;

import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluator;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;
import com.espertech.esper.common.internal.epl.index.base.EventTable;
import com.espertech.esper.common.internal.epl.join.lookup.IndexedPropDesc;
import com.espertech.esper.common.internal.epl.join.querygraph.QueryGraphValueEntryRange;
import com.espertech.esper.common.internal.epl.lookup.LookupStrategyDesc;
import com.espertech.esper.common.internal.epl.lookup.LookupStrategyType;
import com.espertech.esper.common.internal.epl.lookup.SubordTableLookupStrategy;
import com.espertech.esper.common.internal.epl.lookup.SubordTableLookupStrategyFactory;

/**
 * Strategy for looking up, in some sort of table or index, or a set of events, potentially based on the
 * events properties, and returning a set of matched events.
 */
public class SubordTableLookupStrategyFactoryVDW implements SubordTableLookupStrategyFactory {
    public final static EPTypeClass EPTYPE = new EPTypeClass(SubordTableLookupStrategyFactoryVDW.class);

    private IndexedPropDesc[] indexHashedProps;
    private IndexedPropDesc[] indexBtreeProps;
    private boolean nwOnTrigger;
    private ExprEvaluator[] hashEvals;
    private EPTypeClass[] hashCoercionTypes;
    private QueryGraphValueEntryRange[] rangeEvals;
    private EPTypeClass[] rangeCoercionTypes;
    private int numOuterStreams;

    public SubordTableLookupStrategy makeStrategy(EventTable[] eventTable, ExprEvaluatorContext exprEvaluatorContext, VirtualDWView vdw) {
        return vdw.getSubordinateLookupStrategy(this, exprEvaluatorContext);
    }

    public void setIndexHashedProps(IndexedPropDesc[] indexHashedProps) {
        this.indexHashedProps = indexHashedProps;
    }

    public void setIndexBtreeProps(IndexedPropDesc[] indexBtreeProps) {
        this.indexBtreeProps = indexBtreeProps;
    }

    public IndexedPropDesc[] getIndexHashedProps() {
        return indexHashedProps;
    }

    public IndexedPropDesc[] getIndexBtreeProps() {
        return indexBtreeProps;
    }

    public boolean isNwOnTrigger() {
        return nwOnTrigger;
    }

    public void setNwOnTrigger(boolean nwOnTrigger) {
        this.nwOnTrigger = nwOnTrigger;
    }

    public ExprEvaluator[] getHashEvals() {
        return hashEvals;
    }

    public void setHashEvals(ExprEvaluator[] hashEvals) {
        this.hashEvals = hashEvals;
    }

    public EPTypeClass[] getHashCoercionTypes() {
        return hashCoercionTypes;
    }

    public void setHashCoercionTypes(EPTypeClass[] hashCoercionTypes) {
        this.hashCoercionTypes = hashCoercionTypes;
    }

    public QueryGraphValueEntryRange[] getRangeEvals() {
        return rangeEvals;
    }

    public void setRangeEvals(QueryGraphValueEntryRange[] rangeEvals) {
        this.rangeEvals = rangeEvals;
    }

    public EPTypeClass[] getRangeCoercionTypes() {
        return rangeCoercionTypes;
    }

    public void setRangeCoercionTypes(EPTypeClass[] rangeCoercionTypes) {
        this.rangeCoercionTypes = rangeCoercionTypes;
    }

    public int getNumOuterStreams() {
        return numOuterStreams;
    }

    public void setNumOuterStreams(int numOuterStreams) {
        this.numOuterStreams = numOuterStreams;
    }

    public LookupStrategyDesc getLookupStrategyDesc() {
        return new LookupStrategyDesc(LookupStrategyType.VDW);
    }
}
