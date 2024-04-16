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
package com.espertech.esper.common.internal.epl.expression.core;


import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeNull;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRef;
import com.espertech.esper.common.internal.epl.expression.codegen.CodegenLegoCast;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;
import com.espertech.esper.common.internal.event.core.EventPropertyGetterSPI;
import com.espertech.esper.common.internal.event.core.EventTypeSPI;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class ExprIdentNodeEvaluatorContext implements ExprIdentNodeEvaluator {

    private final int streamNum;
    private final EPType resultType;
    private final EventPropertyGetterSPI getter;
    private final EventTypeSPI eventType;
    private final EventPropertyGetterSPI propertyGetterNonContext;

    public ExprIdentNodeEvaluatorContext(int streamNum, EPType resultType, EventPropertyGetterSPI getter, EventTypeSPI eventType, EventPropertyGetterSPI propertyGetterNonContext) {
        this.streamNum = streamNum;
        this.resultType = resultType;
        this.getter = getter;
        this.eventType = eventType;
        this.propertyGetterNonContext = propertyGetterNonContext;
    }

    public boolean evaluatePropertyExists(EventBean[] eventsPerStream, boolean isNewData) {
        return true;
    }

    public int getStreamNum() {
        return streamNum;
    }

    public Object evaluate(EventBean[] eventsPerStream, boolean isNewData, ExprEvaluatorContext context) {
        if (context.getContextProperties() != null) {
            return getter.get(context.getContextProperties());
        }
        return null;
    }

    public CodegenExpression codegen(EPTypeClass requiredType, CodegenMethodScope codegenMethodScope, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        if (resultType == null || resultType == EPTypeNull.INSTANCE) {
            return constantNull();
        }
        CodegenMethod methodNode = codegenMethodScope.makeChild((EPTypeClass) resultType, this.getClass(), codegenClassScope);
        CodegenExpressionRef refExprEvalCtx = exprSymbol.getAddExprEvalCtx(methodNode);

        methodNode.getBlock()
            .ifCondition(notEqualsNull(refExprEvalCtx))
            .blockReturn(CodegenLegoCast.castSafeFromObjectType(resultType, getter.eventBeanGetCodegen(exprDotMethod(refExprEvalCtx, "getContextProperties"), methodNode, codegenClassScope)))
            .methodReturn(constantNull());
        return localMethod(methodNode);
    }

    public EPType getEvaluationType() {
        return resultType;
    }

    public EventPropertyGetterSPI getGetter() {
        return getter;
    }

    public EventPropertyGetterSPI getGetterNonContext() {
        return propertyGetterNonContext;
    }

    public boolean isContextEvaluated() {
        return true;
    }

    public void setOptionalEvent(boolean optionalEvent) {
    }

    public EventTypeSPI getEventType() {
        return eventType;
    }
}
