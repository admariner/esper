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

import com.espertech.esper.common.client.dataflow.annotations.DataFlowOpParameter;
import com.espertech.esper.common.client.dataflow.core.EPDataFlowOperatorParameterProvider;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeNull;
import com.espertech.esper.common.internal.epl.expression.etc.ExprEvalSystemProperty;
import com.espertech.esper.common.internal.event.bean.core.PropertyHelper;
import com.espertech.esper.common.internal.event.core.WriteablePropertyDescriptor;
import com.espertech.esper.common.internal.event.property.MappedProperty;
import com.espertech.esper.common.internal.event.property.Property;
import com.espertech.esper.common.internal.event.property.PropertyParser;
import com.espertech.esper.common.internal.settings.ClasspathImportService;
import com.espertech.esper.common.internal.util.ClassHelperGenericType;
import com.espertech.esper.common.internal.util.ClassHelperPrint;
import com.espertech.esper.common.internal.util.JavaClassHelper;
import com.espertech.esper.common.internal.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

public class PopulateUtil {
    private final static String CLASS_PROPERTY_NAME = "class";
    private final static Logger log = LoggerFactory.getLogger(PopulateUtil.class);

    public static void populateSpecCheckParameters(PopulateFieldWValueDescriptor[] descriptors, Map<String, Object> jsonRaw, Object spec, ExprNodeOrigin exprNodeOrigin, ExprValidationContext exprValidationContext)
        throws ExprValidationException {
        // lowercase keys
        Map<String, Object> lowerCaseJsonRaw = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : jsonRaw.entrySet()) {
            lowerCaseJsonRaw.put(entry.getKey().toLowerCase(Locale.ENGLISH), entry.getValue());
        }
        jsonRaw = lowerCaseJsonRaw;

        // apply values
        for (PopulateFieldWValueDescriptor desc : descriptors) {
            Object value = jsonRaw.remove(desc.getPropertyName().toLowerCase(Locale.ENGLISH));
            Object coerced = coerceProperty(desc.getPropertyName(), desc.getContainerType(), value, desc.getFieldType(), exprNodeOrigin, exprValidationContext, desc.isForceNumeric(), false);
            desc.getSetter().set(coerced);
        }

        // should not have remaining parameters
        if (!jsonRaw.isEmpty()) {
            throw new ExprValidationException("Unrecognized parameter '" + jsonRaw.keySet().iterator().next() + "'");
        }
    }

    public static Object coerceProperty(String propertyName, EPTypeClass containingType, Object value, EPTypeClass type, ExprNodeOrigin exprNodeOrigin, ExprValidationContext exprValidationContext, boolean forceNumeric, boolean includeClassNameInEx) throws ExprValidationException {
        // handle system-property exception
        if (value instanceof ExprNode) {
            if (value instanceof ExprIdentNode) {
                ExprIdentNode identNode = (ExprIdentNode) value;
                Property prop;
                try {
                    prop = PropertyParser.parseAndWalkLaxToSimple(identNode.getFullUnresolvedName());
                } catch (Exception ex) {
                    throw new ExprValidationException("Failed to parse property '" + identNode.getFullUnresolvedName() + "'");
                }
                if (!(prop instanceof MappedProperty)) {
                    throw new ExprValidationException("Unrecognized property '" + identNode.getFullUnresolvedName() + "'");
                }
                MappedProperty mappedProperty = (MappedProperty) prop;
                if (mappedProperty.getPropertyNameAtomic().toLowerCase(Locale.ENGLISH).equals(ExprEvalSystemProperty.SYSTEM_PROPETIES_NAME)) {
                    if (type.getType() == ExprNode.class) {
                        return new ExprEvalSystemProperty(mappedProperty.getKey());
                    } else {
                        return System.getProperty(mappedProperty.getKey());
                    }
                }
            } else {
                ExprNode exprNode = (ExprNode) value;
                if (type.getType() == ExprNode.class) {
                    return exprNode;
                }
                if (!exprNode.getForge().getForgeConstantType().isCompileTimeConstant()) {
                    throw new ExprValidationException("Failed to determine parameter for property '" + propertyName + "' as the parameter is not a compile-time constant expression");
                }

                // handle inner-objects which have a "class" property name
                boolean innerObject = false;
                if (exprNode instanceof ExprConstantNode) {
                    ExprConstantNode constantNode = (ExprConstantNode) exprNode;
                    if (constantNode.getConstantValue() instanceof Map) {
                        Map<String, Object> constants = (Map<String, Object>) constantNode.getConstantValue();
                        if (constants.containsKey(CLASS_PROPERTY_NAME)) {
                            innerObject = true;
                            Set<String> names = constants.keySet();
                            Map<String, Object> values = new LinkedHashMap<>();
                            int count = 0;
                            for (String key : names) {
                                if (key.equals(CLASS_PROPERTY_NAME)) {
                                    // class property becomes string
                                    values.put(key, constants.get(CLASS_PROPERTY_NAME));
                                } else {
                                    // non-class properties become expressions
                                    values.put(key, constantNode.getChildNodes()[count]);
                                }
                                count++;
                            }
                            value = values;
                        }
                    }
                }
                if (!innerObject) {
                    value = exprNode.getForge().getExprEvaluator().evaluate(null, true, null);
                }
            }
        }

        if (value == null) {
            return null;
        }
        if (value.getClass() == type.getType()) {
            return value;
        }
        if (JavaClassHelper.isAssignmentCompatible(value.getClass(), type.getType())) {
            if (forceNumeric && JavaClassHelper.getBoxedType(value.getClass()) != JavaClassHelper.getBoxedType(type).getType() && JavaClassHelper.isNumeric(ClassHelperGenericType.getClassEPType(type.getType())) && JavaClassHelper.isNumeric(ClassHelperGenericType.getClassEPType(value.getClass()))) {
                value = JavaClassHelper.coerceBoxed((Number) value, JavaClassHelper.getBoxedType(type).getType());
            }
            return value;
        }
        if (JavaClassHelper.isSubclassOrImplementsInterface(value.getClass(), type.getType())) {
            return value;
        }
        if (type.getType().isArray()) {
            EPTypeClass componentType = JavaClassHelper.getArrayComponentType(type);
            if (!(value instanceof Collection)) {
                String detail = "expects an array but receives a value of type " + value.getClass().getName();
                throw new ExprValidationException(getExceptionText(propertyName, containingType, includeClassNameInEx, detail));
            }
            Object[] items = ((Collection) value).toArray();
            Object coercedArray = Array.newInstance(componentType.getType(), items.length);
            for (int i = 0; i < items.length; i++) {
                Object coercedValue = coerceProperty(propertyName + " (array element)", type, items[i], componentType, exprNodeOrigin, exprValidationContext, false, includeClassNameInEx);
                Array.set(coercedArray, i, coercedValue);
            }
            return coercedArray;
        }
        if (!(value instanceof Map)) {
            String detail = "expects an " + ClassHelperPrint.getClassNameFullyQualPretty(type) + " but receives a value of type " + ClassHelperPrint.getClassNameFullyQualPrettyObject(value);
            throw new ExprValidationException(getExceptionText(propertyName, containingType, includeClassNameInEx, detail));
        }
        Map<String, Object> props = (Map<String, Object>) value;
        return instantiatePopulateObject(props, type, exprNodeOrigin, exprValidationContext);
    }

    public static Object instantiatePopulateObject(Map<String, Object> objectProperties, EPTypeClass topClass, ExprNodeOrigin exprNodeOrigin, ExprValidationContext exprValidationContext) throws ExprValidationException {

        Class applicableClass = topClass.getType();
        if (topClass.getType().isInterface()) {
            applicableClass = findInterfaceImplementation(objectProperties, topClass.getType(), exprValidationContext.getClasspathImportService());
        }

        Object top;
        try {
            top = applicableClass.getDeclaredConstructor().newInstance();
        } catch (RuntimeException | InvocationTargetException e) {
            throw new ExprValidationException("Exception instantiating class " + applicableClass.getName() + ": " + e.getMessage(), e);
        } catch (InstantiationException e) {
            throw new ExprValidationException(getMessageExceptionInstantiating(applicableClass), e);
        } catch (IllegalAccessException e) {
            throw new ExprValidationException("Illegal access to construct class " + applicableClass.getName() + ": " + e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new ExprValidationException("Failed to find default constructor for " + applicableClass.getName() + ": " + e.getMessage(), e);
        }

        populateObject(objectProperties, top, exprNodeOrigin, exprValidationContext);

        return top;
    }

    private static Class findInterfaceImplementation(Map<String, Object> properties, Class topClass, ClasspathImportService classpathImportService) throws ExprValidationException {
        String message = "Failed to find implementation for interface " + topClass.getName();

        // Allow to populate the special "class" field
        if (!properties.containsKey(CLASS_PROPERTY_NAME)) {
            throw new ExprValidationException(message + ", for interfaces please specified the '" + CLASS_PROPERTY_NAME + "' field that provides the class name either as a simple class name or fully qualified");
        }

        Class clazz = null;
        String className = (String) properties.get(CLASS_PROPERTY_NAME);
        try {
            clazz = JavaClassHelper.getClassForName(className, classpathImportService.getClassForNameProvider());
        } catch (ClassNotFoundException e) {

            if (!className.contains(".")) {
                className = topClass.getPackage().getName() + "." + className;
                try {
                    clazz = JavaClassHelper.getClassForName(className, classpathImportService.getClassForNameProvider());
                } catch (ClassNotFoundException ex) {
                }
            }

            if (clazz == null) {
                throw new ExprValidationPropertyException(message + ", could not find class by name '" + className + "'");
            }
        }

        if (!JavaClassHelper.isSubclassOrImplementsInterface(clazz, topClass)) {
            throw new ExprValidationException(message + ", class " + ClassHelperPrint.getClassNameFullyQualPretty(clazz) + " does not implement the interface");
        }
        return clazz;
    }

    public static void populateObject(String operatorName, int operatorNum, String dataFlowName, Map<String, Object> objectProperties, Object top, ExprNodeOrigin exprNodeOrigin, ExprValidationContext exprValidationContext, EPDataFlowOperatorParameterProvider optionalParameterProvider, Map<String, Object> optionalParameterURIs)
        throws ExprValidationException {
        EPTypeClass applicableClass = ClassHelperGenericType.getClassEPType(top.getClass());
        Set<WriteablePropertyDescriptor> writables = PropertyHelper.getWritableProperties(applicableClass.getType());
        Set<Field> annotatedFields = JavaClassHelper.findAnnotatedFields(top.getClass(), DataFlowOpParameter.class);
        Set<Method> annotatedMethods = JavaClassHelper.findAnnotatedMethods(top.getClass(), DataFlowOpParameter.class);

        // find catch-all methods
        Set<Method> catchAllMethods = new LinkedHashSet<Method>();
        if (annotatedMethods != null) {
            for (Method method : annotatedMethods) {
                DataFlowOpParameter anno = (DataFlowOpParameter) JavaClassHelper.getAnnotations(DataFlowOpParameter.class, method.getDeclaredAnnotations()).get(0);
                if (anno.all()) {
                    if (method.getParameterTypes().length == 2 && method.getParameterTypes()[0] == String.class && method.getParameterTypes()[1] == ExprNode.class) {
                        catchAllMethods.add(method);
                        continue;
                    }
                    throw new ExprValidationException("Invalid annotation for catch-call method '" + method.getName() + "', method must take (String, ExprNode) as parameters");
                }
            }
        }

        // map provided values
        for (Map.Entry<String, Object> property : objectProperties.entrySet()) {
            boolean found = false;
            String propertyName = property.getKey();

            // invoke catch-all setters
            for (Method method : catchAllMethods) {
                try {
                    method.invoke(top, new Object[]{propertyName, property.getValue()});
                } catch (IllegalAccessException e) {
                    throw new ExprValidationException("Illegal access invoking method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + method.getName(), e);
                } catch (InvocationTargetException e) {
                    throw new ExprValidationException("Exception invoking method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + method.getName() + ": " + e.getTargetException().getMessage(), e);
                }
                found = true;
            }

            if (propertyName.toLowerCase(Locale.ENGLISH).equals(CLASS_PROPERTY_NAME)) {
                continue;
            }

            // use the writeable property descriptor (appropriate setter method) from writing the property
            WriteablePropertyDescriptor descriptor = findDescriptor(applicableClass, propertyName, writables);
            if (descriptor != null) {
                if (descriptor.getType() == EPTypeNull.INSTANCE) {
                    throw new IllegalArgumentException("Null-type value cannot be assigned to");
                }
                Object coerceProperty = coerceProperty(propertyName, applicableClass, property.getValue(), (EPTypeClass) descriptor.getType(), exprNodeOrigin, exprValidationContext, false, true);

                try {
                    descriptor.getWriteMethod().invoke(top, new Object[]{coerceProperty});
                } catch (IllegalArgumentException e) {
                    throw new ExprValidationException("Illegal argument invoking setter method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + descriptor.getWriteMethod().getName() + " provided value " + coerceProperty, e);
                } catch (IllegalAccessException e) {
                    throw new ExprValidationException("Illegal access invoking setter method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + descriptor.getWriteMethod().getName(), e);
                } catch (InvocationTargetException e) {
                    throw new ExprValidationException("Exception invoking setter method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + descriptor.getWriteMethod().getName() + ": " + e.getTargetException().getMessage(), e);
                }
                continue;
            }

            // find the field annotated with {@link @GraphOpProperty}
            for (Field annotatedField : annotatedFields) {
                DataFlowOpParameter anno = (DataFlowOpParameter) JavaClassHelper.getAnnotations(DataFlowOpParameter.class, annotatedField.getDeclaredAnnotations()).get(0);
                if (anno.name().equals(propertyName) || annotatedField.getName().equals(propertyName)) {
                    EPTypeClass fieldClass = ClassHelperGenericType.getFieldEPType(annotatedField);
                    Object coerceProperty = coerceProperty(propertyName, applicableClass, property.getValue(), fieldClass, exprNodeOrigin, exprValidationContext, true, true);
                    try {
                        annotatedField.setAccessible(true);
                        annotatedField.set(top, coerceProperty);
                    } catch (Exception e) {
                        throw new ExprValidationException("Failed to set field '" + annotatedField.getName() + "': " + e.getMessage(), e);
                    }
                    found = true;
                    break;
                }
            }
            if (found) {
                continue;
            }

            throw new ExprValidationException("Failed to find writable property '" + propertyName + "' for class " + applicableClass.getTypeName());
        }

        // second pass: if a parameter URI - value pairs were provided, check that
        if (optionalParameterURIs != null) {
            for (Field annotatedField : annotatedFields) {
                try {
                    annotatedField.setAccessible(true);
                    String uri = operatorName + "/" + annotatedField.getName();
                    if (optionalParameterURIs.containsKey(uri)) {
                        Object value = optionalParameterURIs.get(uri);
                        annotatedField.set(top, value);
                        if (log.isDebugEnabled()) {
                            log.debug("Found parameter '" + uri + "' for data flow " + dataFlowName + " setting " + value);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Not found parameter '" + uri + "' for data flow " + dataFlowName);
                        }
                    }
                } catch (Exception e) {
                    throw new ExprValidationException("Failed to set field '" + annotatedField.getName() + "': " + e.getMessage(), e);
                }
            }

            for (Method method : annotatedMethods) {
                DataFlowOpParameter anno = (DataFlowOpParameter) JavaClassHelper.getAnnotations(DataFlowOpParameter.class, method.getDeclaredAnnotations()).get(0);
                if (anno.all()) {
                    if (method.getParameterTypes().length == 2 && method.getParameterTypes()[0] == String.class && method.getParameterTypes()[1] == Object.class) {
                        for (Map.Entry<String, Object> entry : optionalParameterURIs.entrySet()) {
                            String[] elements = URIUtil.parsePathElements(URI.create(entry.getKey()));
                            if (elements.length < 2) {
                                throw new ExprValidationException("Failed to parse URI '" + entry.getKey() + "', expected " +
                                    "'operator_name/property_name' format");
                            }
                            if (elements[0].equals(operatorName)) {
                                try {
                                    method.invoke(top, new Object[]{elements[1], entry.getValue()});
                                } catch (IllegalAccessException e) {
                                    throw new ExprValidationException("Illegal access invoking method for property '" + entry.getKey() + "' for class " + applicableClass.getTypeName() + " method " + method.getName(), e);
                                } catch (InvocationTargetException e) {
                                    throw new ExprValidationException("Exception invoking method for property '" + entry.getKey() + "' for class " + applicableClass.getTypeName() + " method " + method.getName() + ": " + e.getTargetException().getMessage(), e);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void populateObject(Map<String, Object> objectProperties, Object top, ExprNodeOrigin exprNodeOrigin, ExprValidationContext exprValidationContext)
        throws ExprValidationException {
        EPTypeClass applicableClass = ClassHelperGenericType.getClassEPType(top.getClass());
        Set<WriteablePropertyDescriptor> writables = PropertyHelper.getWritableProperties(applicableClass.getType());
        Set<Field> annotatedFields = JavaClassHelper.findAnnotatedFields(top.getClass(), DataFlowOpParameter.class);
        Set<Method> annotatedMethods = JavaClassHelper.findAnnotatedMethods(top.getClass(), DataFlowOpParameter.class);

        // find catch-all methods
        Set<Method> catchAllMethods = new LinkedHashSet<Method>();
        if (annotatedMethods != null) {
            for (Method method : annotatedMethods) {
                DataFlowOpParameter anno = (DataFlowOpParameter) JavaClassHelper.getAnnotations(DataFlowOpParameter.class, method.getDeclaredAnnotations()).get(0);
                if (anno.all()) {
                    if (method.getParameterTypes().length == 2 && method.getParameterTypes()[0] == String.class && method.getParameterTypes()[1] == Object.class) {
                        catchAllMethods.add(method);
                        continue;
                    }
                    throw new ExprValidationException("Invalid annotation for catch-call");
                }
            }
        }

        // map provided values
        for (Map.Entry<String, Object> property : objectProperties.entrySet()) {
            boolean found = false;
            String propertyName = property.getKey();

            // invoke catch-all setters
            for (Method method : catchAllMethods) {
                try {
                    method.invoke(top, new Object[]{propertyName, property.getValue()});
                } catch (IllegalAccessException e) {
                    throw new ExprValidationException("Illegal access invoking method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + method.getName(), e);
                } catch (InvocationTargetException e) {
                    throw new ExprValidationException("Exception invoking method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + method.getName() + ": " + e.getTargetException().getMessage(), e);
                }
                found = true;
            }

            if (propertyName.toLowerCase(Locale.ENGLISH).equals(CLASS_PROPERTY_NAME)) {
                continue;
            }

            // use the writeable property descriptor (appropriate setter method) from writing the property
            WriteablePropertyDescriptor descriptor = findDescriptor(applicableClass, propertyName, writables);
            if (descriptor != null) {
                if (descriptor.getType() == EPTypeNull.INSTANCE) {
                    throw new IllegalArgumentException("Null-type value cannot be assigned to");
                }
                Object coerceProperty = coerceProperty(propertyName, applicableClass, property.getValue(), (EPTypeClass) descriptor.getType(), exprNodeOrigin, exprValidationContext, false, true);

                try {
                    descriptor.getWriteMethod().invoke(top, new Object[]{coerceProperty});
                } catch (IllegalArgumentException e) {
                    throw new ExprValidationException("Illegal argument invoking setter method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + descriptor.getWriteMethod().getName() + " provided value " + coerceProperty, e);
                } catch (IllegalAccessException e) {
                    throw new ExprValidationException("Illegal access invoking setter method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + descriptor.getWriteMethod().getName(), e);
                } catch (InvocationTargetException e) {
                    throw new ExprValidationException("Exception invoking setter method for property '" + propertyName + "' for class " + applicableClass.getTypeName() + " method " + descriptor.getWriteMethod().getName() + ": " + e.getTargetException().getMessage(), e);
                }
                continue;
            }

            // find the field annotated with {@link @GraphOpProperty}
            for (Field annotatedField : annotatedFields) {
                DataFlowOpParameter anno = (DataFlowOpParameter) JavaClassHelper.getAnnotations(DataFlowOpParameter.class, annotatedField.getDeclaredAnnotations()).get(0);
                if (anno.name().equals(propertyName) || annotatedField.getName().equals(propertyName)) {
                    EPTypeClass typeClass = ClassHelperGenericType.getFieldEPType(annotatedField);
                    Object coerceProperty = coerceProperty(propertyName, applicableClass, property.getValue(), typeClass, exprNodeOrigin, exprValidationContext, true, true);
                    try {
                        annotatedField.setAccessible(true);
                        annotatedField.set(top, coerceProperty);
                    } catch (Exception e) {
                        throw new ExprValidationException("Failed to set field '" + annotatedField.getName() + "': " + e.getMessage(), e);
                    }
                    found = true;
                    break;
                }
            }

            if (found) {
                continue;
            }

            throw new ExprValidationException("Failed to find writable property '" + propertyName + "' for class " + applicableClass.getTypeName());
        }
    }

    private static String getExceptionText(String propertyName, EPTypeClass containingType, boolean includeClassNameInEx, String detailText) {
        String msg = "Property '" + propertyName + "'";
        if (includeClassNameInEx) {
            msg += " of class " + ClassHelperPrint.getClassNameFullyQualPretty(containingType);
        }
        msg += " " + detailText;
        return msg;
    }

    private static WriteablePropertyDescriptor findDescriptor(EPTypeClass clazz, String propertyName, Set<WriteablePropertyDescriptor> writables)
        throws ExprValidationException {
        for (WriteablePropertyDescriptor desc : writables) {
            if (desc.getPropertyName().toLowerCase(Locale.ENGLISH).equals(propertyName.toLowerCase(Locale.ENGLISH))) {
                return desc;
            }
        }
        return null;
    }

    private static String getMessageExceptionInstantiating(Class clazz) {
        return "Exception instantiating class " + clazz.getName() + ", please make sure the class has a public no-arg constructor (and for inner classes is declared static)";
    }
}
