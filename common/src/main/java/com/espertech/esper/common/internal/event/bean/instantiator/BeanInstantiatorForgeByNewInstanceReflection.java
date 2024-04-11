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
package com.espertech.esper.common.internal.event.bean.instantiator;

import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.newInstance;

public class BeanInstantiatorForgeByNewInstanceReflection implements BeanInstantiatorForge, BeanInstantiator {
    private final static Logger log = LoggerFactory.getLogger(BeanInstantiatorForgeByNewInstanceReflection.class);

    private final EPTypeClass clazz;

    public BeanInstantiatorForgeByNewInstanceReflection(EPTypeClass clazz) {
        this.clazz = clazz;
    }

    public Object instantiate() {
        try {
            return clazz.getType().getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            return handle(e);
        }
    }

    public CodegenExpression make(CodegenMethodScope parent, CodegenClassScope codegenClassScope) {
        return newInstance(clazz);
    }

    public BeanInstantiator getBeanInstantiator() {
        return this;
    }

    private Object handle(Exception e) {
        String message = "Unexpected exception encountered invoking newInstance on class '" + clazz.getTypeName() + "': " + e.getMessage();
        log.error(message, e);
        return null;
    }
}
