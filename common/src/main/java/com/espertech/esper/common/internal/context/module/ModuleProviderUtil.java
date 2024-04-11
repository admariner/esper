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
package com.espertech.esper.common.internal.context.module;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EPException;
import com.espertech.esper.common.internal.collection.PathRegistry;
import com.espertech.esper.common.internal.epl.classprovided.core.ClassProvided;
import com.espertech.esper.common.internal.epl.classprovided.core.ClassProvidedImportClassLoaderFactory;

import java.lang.reflect.InvocationTargetException;

public class ModuleProviderUtil {
    public static ModuleProviderCLPair analyze(EPCompiled compiled, ClassLoader classLoaderParent, PathRegistry<String, ClassProvided> classProvidedPathRegistry) {
        ClassLoader classLoader = ClassProvidedImportClassLoaderFactory.getClassLoader(compiled.getClasses(), classLoaderParent, classProvidedPathRegistry);
        String resourceClassName = compiled.getManifest().getModuleProviderClassName();

        // load module resource class
        Class clazz;
        try {
            clazz = classLoader.loadClass(resourceClassName);
        } catch (ClassNotFoundException e) {
            throw new EPException(e);
        }

        // instantiate
        ModuleProvider moduleResource;
        try {
            moduleResource = (ModuleProvider) clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |InvocationTargetException e) {
            throw new EPException(e);
        }

        return new ModuleProviderCLPair(classLoader, moduleResource);
    }
}
