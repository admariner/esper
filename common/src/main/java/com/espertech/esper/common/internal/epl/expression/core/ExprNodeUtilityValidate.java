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

import com.espertech.esper.common.client.configuration.compiler.ConfigurationCompilerExpression;
import com.espertech.esper.common.client.hook.aggfunc.AggregationFunctionForge;
import com.espertech.esper.common.client.type.EPType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeNull;
import com.espertech.esper.common.client.util.TimePeriod;
import com.espertech.esper.common.internal.collection.Pair;
import com.espertech.esper.common.internal.compile.stage1.spec.OnTriggerSetAssignment;
import com.espertech.esper.common.internal.epl.enummethod.dot.ExprDeclaredOrLambdaNode;
import com.espertech.esper.common.internal.epl.enummethod.dot.ExprLambdaGoesNode;
import com.espertech.esper.common.internal.epl.expression.agg.base.ExprAggregateNode;
import com.espertech.esper.common.internal.epl.expression.agg.base.ExprAggregateNodeUtil;
import com.espertech.esper.common.internal.epl.expression.agg.method.ExprPlugInAggNode;
import com.espertech.esper.common.internal.epl.expression.assign.*;
import com.espertech.esper.common.internal.epl.expression.chain.Chainable;
import com.espertech.esper.common.internal.epl.expression.chain.ChainableArray;
import com.espertech.esper.common.internal.epl.expression.chain.ChainableCall;
import com.espertech.esper.common.internal.epl.expression.chain.ChainableName;
import com.espertech.esper.common.internal.epl.expression.dot.core.ExprDotNode;
import com.espertech.esper.common.internal.epl.expression.dot.core.ExprDotNodeImpl;
import com.espertech.esper.common.internal.epl.expression.funcs.ExprPlugInSingleRowNode;
import com.espertech.esper.common.internal.epl.expression.ops.ExprEqualsNode;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectNode;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNode;
import com.espertech.esper.common.internal.epl.expression.time.node.ExprTimePeriod;
import com.espertech.esper.common.internal.epl.expression.variable.ExprVariableNode;
import com.espertech.esper.common.internal.epl.expression.visitor.ExprNodeGroupingVisitorWParent;
import com.espertech.esper.common.internal.epl.expression.visitor.ExprNodeSubselectDeclaredDotVisitor;
import com.espertech.esper.common.internal.epl.expression.visitor.ExprNodeSummaryVisitor;
import com.espertech.esper.common.internal.epl.expression.visitor.ExprNodeViewResourceVisitor;
import com.espertech.esper.common.internal.event.property.MappedPropertyParseResult;
import com.espertech.esper.common.internal.settings.*;
import com.espertech.esper.common.internal.util.CollectionUtil;
import com.espertech.esper.common.internal.util.EnumValue;
import com.espertech.esper.common.internal.util.JavaClassHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.*;
import java.util.function.Supplier;

import static com.espertech.esper.common.internal.event.propertyparser.PropertyParserNoDep.parseMappedProperty;

public class ExprNodeUtilityValidate {
    private static final Logger log = LoggerFactory.getLogger(ExprNodeUtilityValidate.class);

    public static EPTypeClass validateLHSTypeAnyAllSomeIn(EPType type) throws ExprValidationException {
        // collections, array or map not supported
        String message = "Collection or array comparison and null-type values are not allowed for the IN, ANY, SOME or ALL keywords";
        if (type == null || type == EPTypeNull.INSTANCE) {
            throw new ExprValidationException(message);
        }
        EPTypeClass typeClass = (EPTypeClass) type;
        if (typeClass.getType().isArray() || JavaClassHelper.isImplementsInterface(typeClass, Collection.class) || JavaClassHelper.isImplementsInterface(typeClass, Map.class)) {
            throw new ExprValidationException(message);
        }
        return typeClass;
    }

    public static EPTypeClass validateReturnsNumeric(ExprForge forge) throws ExprValidationException {
        EPType type = forge.getEvaluationType();
        validateReturnsNumeric(forge, () -> "Implicit conversion from datatype '" +
            (type == null || type == EPTypeNull.INSTANCE ? "null" : type.getTypeName()) +
            "' to numeric is not allowed");
        return (EPTypeClass) type;
    }

    public static void validateReturnsNumeric(ExprForge forge, Supplier<String> msg) throws ExprValidationException {
        EPType type = forge.getEvaluationType();
        if (!JavaClassHelper.isNumeric(type)) {
            throw new ExprValidationException(msg.get());
        }
    }

    public static void validatePlainExpression(ExprNodeOrigin origin, ExprNode[] expressions) throws ExprValidationException {
        ExprNodeSummaryVisitor summaryVisitor = new ExprNodeSummaryVisitor();
        for (ExprNode expression : expressions) {
            validatePlainExpression(origin, expression, summaryVisitor);
        }
    }

    public static void validatePlainExpression(ExprNodeOrigin origin, ExprNode expression) throws ExprValidationException {
        ExprNodeSummaryVisitor summaryVisitor = new ExprNodeSummaryVisitor();
        validatePlainExpression(origin, expression, summaryVisitor);
    }

    public static void validateAssignment(boolean allowLHSVariables, ExprNodeOrigin origin, OnTriggerSetAssignment spec, ExprValidationContext validationContext) throws ExprValidationException {
        // equals-assignments are "a=1" and "a[1]=2" and such
        // they are not "a.reset()"
        ExprAssignment assignment = checkGetStraightAssignment(spec.getExpression(), allowLHSVariables);
        if (assignment == null) {
            assignment = new ExprAssignmentCurly(spec.getExpression());
        }
        assignment.validate(origin, validationContext);
        spec.setValidated(assignment);
    }

    /**
     * Check if the expression is minimal: does not have a subselect, aggregation and does not need view resources
     *
     * @param expression to inspect
     * @return null if minimal, otherwise name of offending sub-expression
     */
    public static String isMinimalExpression(ExprNode expression) {
        ExprNodeSubselectDeclaredDotVisitor subselectVisitor = new ExprNodeSubselectDeclaredDotVisitor();
        expression.accept(subselectVisitor);
        if (subselectVisitor.getSubselects().size() > 0) {
            return "a subselect";
        }

        ExprNodeViewResourceVisitor viewResourceVisitor = new ExprNodeViewResourceVisitor();
        expression.accept(viewResourceVisitor);
        if (viewResourceVisitor.getExprNodes().size() > 0) {
            return "a function that requires view resources (prior, prev)";
        }

        List<ExprAggregateNode> aggregateNodes = new LinkedList<ExprAggregateNode>();
        ExprAggregateNodeUtil.getAggregatesBottomUp(expression, aggregateNodes);
        if (!aggregateNodes.isEmpty()) {
            return "an aggregation function";
        }
        return null;
    }

    /**
     * Validates the expression node subtree that has this
     * node as root. Some of the nodes of the tree, including the
     * root, might be replaced in the process.
     *
     * @param origin            validate origin
     * @param exprNode          node
     * @param validationContext context
     * @return the root node of the validated subtree, possibly
     * different than the root node of the unvalidated subtree
     * @throws ExprValidationException when the validation fails
     */
    public static ExprNode getValidatedSubtree(ExprNodeOrigin origin, ExprNode exprNode, ExprValidationContext validationContext) throws ExprValidationException {
        if (exprNode instanceof ExprLambdaGoesNode) {
            return exprNode;
        }

        try {
            return getValidatedSubtreeInternal(exprNode, validationContext, true);
        } catch (ExprValidationException ex) {
            try {
                String text;
                if (exprNode instanceof ExprSubselectNode) {
                    ExprSubselectNode subselect = (ExprSubselectNode) exprNode;
                    text = ExprNodeUtilityMake.getSubqueryInfoText(subselect);
                } else {
                    text = ExprNodeUtilityPrint.toExpressionStringMinPrecedenceSafe(exprNode);
                    if (text.length() > 40) {
                        String shortened = text.substring(0, 35);
                        text = shortened + "...(" + text.length() + " chars)";
                    }
                    text = "'" + text + "'";
                }
                throw makeValidationExWExpression(origin, text, ex);
            } catch (RuntimeException rtex) {
                log.debug("Failed to render nice validation message text: " + rtex.getMessage(), rtex);
                throw ex;
            }
        }
    }

    public static ExprValidationException makeValidationExWExpression(ExprNodeOrigin origin, String text, ExprValidationException ex) {
        return new ExprValidationException("Failed to validate " +
            origin.getClauseName() +
            " expression " +
            text + ": " +
            ex.getMessage(), ex);
    }

    public static boolean validateNamedExpectType(ExprNamedParameterNode namedParameterNode, Class[] expectedTypes) throws ExprValidationException {
        if (namedParameterNode.getChildNodes().length != 1) {
            throw getNamedValidationException(namedParameterNode.getParameterName(), expectedTypes);
        }

        ExprNode childNode = namedParameterNode.getChildNodes()[0];
        EPType type = JavaClassHelper.getBoxedType(childNode.getForge().getEvaluationType());

        boolean found = false;
        for (Class expectedType : expectedTypes) {
            if (expectedType == TimePeriod.class && childNode instanceof ExprTimePeriod) {
                found = true;
                break;
            }
            Class expectedBoxedTypeMayNull = JavaClassHelper.getBoxedType(expectedType);
            if (type == null || type == EPTypeNull.INSTANCE) {
                if (expectedBoxedTypeMayNull == null) {
                    found = true;
                    break;
                }
            } else {
                EPTypeClass typeClass = (EPTypeClass) type;
                if (typeClass.getType() == expectedType) {
                    found = true;
                    break;
                }
            }
        }

        if (found) {
            return namedParameterNode.getChildNodes()[0].getForge().getForgeConstantType().isCompileTimeConstant();
        }
        throw getNamedValidationException(namedParameterNode.getParameterName(), expectedTypes);
    }

    private static ExprValidationException getNamedValidationException(String parameterName, Class[] expected) {
        String expectedType;
        if (expected.length == 1) {
            expectedType = "a " + JavaClassHelper.getSimpleNameForClass(expected[0]) + "-typed value";
        } else {
            StringWriter buf = new StringWriter();
            buf.append("any of the following types: ");
            String delimiter = "";
            for (Class clazz : expected) {
                buf.append(delimiter);
                buf.append(JavaClassHelper.getSimpleNameForClass(clazz));
                delimiter = ",";
            }
            expectedType = buf.toString();
        }
        String message = "Failed to validate named parameter '" + parameterName + "', expected a single expression returning " + expectedType;
        return new ExprValidationException(message);
    }

    public static Map<String, ExprNamedParameterNode> getNamedExpressionsHandleDups(List<ExprNode> parameters) throws ExprValidationException {
        Map<String, ExprNamedParameterNode> nameds = null;

        for (ExprNode node : parameters) {
            if (node instanceof ExprNamedParameterNode) {
                ExprNamedParameterNode named = (ExprNamedParameterNode) node;
                if (nameds == null) {
                    nameds = new HashMap<String, ExprNamedParameterNode>();
                }
                String lowerCaseName = named.getParameterName().toLowerCase(Locale.ENGLISH);
                if (nameds.containsKey(lowerCaseName)) {
                    throw new ExprValidationException("Duplicate parameter '" + lowerCaseName + "'");
                }
                nameds.put(lowerCaseName, named);
            }
        }
        if (nameds == null) {
            return Collections.emptyMap();
        }
        return nameds;
    }

    public static void validateNamed(Map<String, ExprNamedParameterNode> namedExpressions, String[] namedParameters) throws ExprValidationException {
        for (Map.Entry<String, ExprNamedParameterNode> entry : namedExpressions.entrySet()) {
            boolean found = false;
            for (String named : namedParameters) {
                if (named.equals(entry.getKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new ExprValidationException("Unexpected named parameter '" + entry.getKey() + "', expecting any of the following: " + CollectionUtil.toStringArray(namedParameters));
            }
        }
    }

    private static ExprNode getValidatedSubtreeInternal(ExprNode exprNode, ExprValidationContext validationContext, boolean isTopLevel) throws ExprValidationException {
        ExprNode result = exprNode;
        if (exprNode instanceof ExprLambdaGoesNode) {
            return exprNode;
        }

        for (int i = 0; i < exprNode.getChildNodes().length; i++) {
            ExprNode childNode = exprNode.getChildNodes()[i];
            if (childNode instanceof ExprDeclaredOrLambdaNode) {
                ExprDeclaredOrLambdaNode node = (ExprDeclaredOrLambdaNode) childNode;
                if (node.validated()) {
                    continue;
                }
            }
            ExprNode childNodeValidated = getValidatedSubtreeInternal(childNode, validationContext, false);
            exprNode.setChildNode(i, childNodeValidated);
        }

        try {
            ExprNode optionalReplacement = exprNode.validate(validationContext);
            if (optionalReplacement != null) {
                return getValidatedSubtreeInternal(optionalReplacement, validationContext, isTopLevel);
            }
        } catch (ExprValidationException e) {
            if (exprNode instanceof ExprIdentNode) {
                ExprIdentNode identNode = (ExprIdentNode) exprNode;
                try {
                    result = resolveStaticMethodOrField(identNode, e, validationContext);
                } catch (ExprValidationException ex) {
                    e = ex;
                    result = resolveAsStreamName(identNode, e, validationContext);
                }
            } else {
                throw e;
            }
        }

        // For top-level expressions check if we perform audit
        if (isTopLevel) {
            if (validationContext.isExpressionAudit()) {
                return (ExprNode) ExprNodeProxy.newInstance(result);
            }
        } else {
            if (validationContext.isExpressionNestedAudit() && !(result instanceof ExprIdentNode) && !(ExprNodeUtilityQuery.isConstant(result))) {
                return (ExprNode) ExprNodeProxy.newInstance(result);
            }
        }

        return result;
    }

    public static void getValidatedSubtree(ExprNodeOrigin origin, ExprNode[] exprNode, ExprValidationContext validationContext) throws ExprValidationException {
        if (exprNode == null) {
            return;
        }
        for (int i = 0; i < exprNode.length; i++) {
            exprNode[i] = getValidatedSubtree(origin, exprNode[i], validationContext);
        }
    }

    public static void getValidatedSubtree(ExprNodeOrigin origin, ExprNode[][] exprNode, ExprValidationContext validationContext) throws ExprValidationException {
        if (exprNode == null) {
            return;
        }
        for (ExprNode[] anExprNode : exprNode) {
            getValidatedSubtree(origin, anExprNode, validationContext);
        }
    }

    public static void validate(ExprNodeOrigin origin, List<Chainable> chainSpec, ExprValidationContext validationContext) throws ExprValidationException {
        // validate all parameters
        for (Chainable chainElement : chainSpec) {
            chainElement.validate(origin, validationContext);
        }
    }

    private static void validatePlainExpression(ExprNodeOrigin origin, ExprNode expression, ExprNodeSummaryVisitor summaryVisitor) throws ExprValidationException {
        expression.accept(summaryVisitor);
        if (summaryVisitor.isHasAggregation() || summaryVisitor.isHasSubselect() || summaryVisitor.isHasStreamSelect() || summaryVisitor.isHasPreviousPrior()) {
            String text = ExprNodeUtilityPrint.toExpressionStringMinPrecedenceSafe(expression);
            throw new ExprValidationException("Invalid " + origin.getClauseName() + " expression '" + text + "': Aggregation, sub-select, previous or prior functions are not supported in this context");
        }
    }

    // Since static method calls such as "Class.method('a')" and mapped properties "Stream.property('key')"
    // look the same, however as the validation could not resolve "Stream.property('key')" before calling this method,
    // this method tries to resolve the mapped property as a static method.
    // Assumes that this is an ExprIdentNode.
    private static ExprNode resolveStaticMethodOrField(ExprIdentNode identNode, ExprValidationException propertyException, ExprValidationContext validationContext)
        throws ExprValidationException {
        // Reconstruct the original string
        StringBuilder mappedProperty = new StringBuilder(identNode.getUnresolvedPropertyName());
        if (identNode.getStreamOrPropertyName() != null) {
            mappedProperty.insert(0, identNode.getStreamOrPropertyName() + '.');
        }

        // Parse the mapped property format into a class name, method and single string parameter
        MappedPropertyParseResult parse = parseMappedProperty(mappedProperty.toString());
        if (parse == null) {
            ExprConstantNode constNode = resolveIdentAsEnumConst(mappedProperty.toString(), validationContext.getClasspathImportService(), validationContext.getClassProvidedClasspathExtension());
            if (constNode == null) {
                throw propertyException;
            } else {
                return constNode;
            }
        }

        // If there is a class name, assume a static method is possible.
        if (parse.getClassName() != null) {
            List<ExprNode> parameters = new ArrayList<>(1);
            parameters.add(new ExprConstantNodeImpl(parse.getArgString()));
            List<Chainable> chain = new ArrayList<Chainable>();
            chain.add(new ChainableName(parse.getClassName()));
            chain.add(new ChainableCall(parse.getMethodName(), parameters));
            ConfigurationCompilerExpression exprconfig = validationContext.getStatementCompileTimeService().getConfiguration().getCompiler().getExpression();
            ExprNode result = new ExprDotNodeImpl(chain, exprconfig.isDuckTyping(), exprconfig.isUdfCache());

            // Validate
            try {
                result.validate(validationContext);
            } catch (ExprValidationException e) {
                throw new ExprValidationException("Failed to resolve enumeration method, date-time method or mapped property '" + mappedProperty + "': " + e.getMessage());
            }

            return result;
        }

        // There is no class name, try a single-row function
        String functionName = parse.getMethodName();
        try {
            Pair<Class, ClasspathImportSingleRowDesc> classMethodPair = validationContext.getClasspathImportService().resolveSingleRow(functionName, validationContext.getClassProvidedClasspathExtension());
            List<ExprNode> parameters = Collections.singletonList(new ExprConstantNodeImpl(parse.getArgString()));
            List<Chainable> chain = Collections.singletonList(new ChainableCall(classMethodPair.getSecond().getMethodName(), parameters));
            ExprNode result = new ExprPlugInSingleRowNode(functionName, classMethodPair.getFirst(), chain, classMethodPair.getSecond());

            // Validate
            try {
                result.validate(validationContext);
            } catch (RuntimeException e) {
                throw new ExprValidationException("Plug-in aggregation function '" + parse.getMethodName() + "' failed validation: " + e.getMessage());
            }

            return result;
        } catch (ClasspathImportUndefinedException e) {
            // Not an single-row function
        } catch (ClasspathImportException e) {
            throw new IllegalStateException("Error resolving single-row function: " + e.getMessage(), e);
        }

        // Try an aggregation function factory
        try {
            AggregationFunctionForge aggregationForge = validationContext.getClasspathImportService().resolveAggregationFunction(parse.getMethodName(), validationContext.getClassProvidedClasspathExtension());
            ExprNode result = new ExprPlugInAggNode(false, aggregationForge, parse.getMethodName());
            result.addChildNode(new ExprConstantNodeImpl(parse.getArgString()));

            // Validate
            try {
                result.validate(validationContext);
            } catch (RuntimeException e) {
                throw new ExprValidationException("Plug-in aggregation function '" + parse.getMethodName() + "' failed validation: " + e.getMessage());
            }

            return result;
        } catch (ClasspathImportUndefinedException e) {
            // Not an aggregation function
        } catch (ClasspathImportException e) {
            throw new IllegalStateException("Error resolving aggregation: " + e.getMessage(), e);
        }

        // absolutely cannot be resolved
        throw propertyException;
    }

    private static ExprNode resolveAsStreamName(ExprIdentNode identNode, ExprValidationException existingException, ExprValidationContext validationContext)
        throws ExprValidationException {
        ExprStreamUnderlyingNode exprStream = new ExprStreamUnderlyingNodeImpl(identNode.getUnresolvedPropertyName(), false);

        try {
            exprStream.validate(validationContext);
        } catch (ExprValidationException ex) {
            throw existingException;
        }

        return exprStream;
    }

    private static ExprConstantNode resolveIdentAsEnumConst(String constant, ClasspathImportServiceCompileTime classpathImportService, ClasspathExtensionClass classpathExtension) {
        EnumValue enumValue = ClasspathImportCompileTimeUtil.resolveIdentAsEnum(constant, classpathImportService, classpathExtension, false);
        if (enumValue != null) {
            return new ExprConstantNodeImpl(enumValue);
        }
        return null;
    }

    private static ExprAssignment checkGetStraightAssignment(ExprNode node, boolean allowLHSVariables) throws ExprValidationException {
        Pair<String, ExprNode> prop = checkGetAssignmentToProp(node);
        if (prop != null) {
            return new ExprAssignmentStraight(node, new ExprAssignmentLHSIdent(prop.getFirst()), prop.getSecond());
        }
        if (!(node instanceof ExprEqualsNode)) {
            return null;
        }
        ExprEqualsNode equals = (ExprEqualsNode) node;
        ExprNode lhs = equals.getChildNodes()[0];
        ExprNode rhs = equals.getChildNodes()[1];

        if (lhs instanceof ExprVariableNode) {
            ExprVariableNode variableNode = (ExprVariableNode) equals.getChildNodes()[0];
            if (!allowLHSVariables) {
                throw new ExprValidationException("Left-hand-side does not allow variables for variable '" + variableNode.getVariableMetadata().getVariableName() + "'");
            }
            String variableNameWSubprop = variableNode.getVariableNameWithSubProp();
            String variableName = variableNameWSubprop;
            String subPropertyName = null;
            int indexOfDot = variableNameWSubprop.indexOf('.');
            if (indexOfDot != -1) {
                subPropertyName = variableNameWSubprop.substring(indexOfDot + 1);
                variableName = variableNameWSubprop.substring(0, indexOfDot);
            }

            ExprAssignmentLHS lhsAssign;
            if (subPropertyName != null) {
                lhsAssign = new ExprAssignmentLHSIdentWSubprop(variableName, subPropertyName);
            } else {
                lhsAssign = new ExprAssignmentLHSIdent(variableName);
            }
            return new ExprAssignmentStraight(node, lhsAssign, rhs);
        }
        if (lhs instanceof ExprDotNode) {
            ExprDotNode dot = (ExprDotNode) lhs;
            List<Chainable> chainables = dot.getChainSpec();
            if (chainables.size() == 2 && chainables.get(0) instanceof ChainableName && chainables.get(1) instanceof ChainableArray) {
                ChainableName name = (ChainableName) chainables.get(0);
                ChainableArray array = (ChainableArray) chainables.get(1);
                return new ExprAssignmentStraight(node, new ExprAssignmentLHSArrayElement(name.getName(), array.getIndexes()), rhs);
            }
            if (allowLHSVariables && dot.getChildNodes()[0] instanceof ExprVariableNode && chainables.size() == 1 && chainables.get(0) instanceof ChainableArray) {
                ExprVariableNode variable = (ExprVariableNode) dot.getChildNodes()[0];
                ChainableArray array = (ChainableArray) chainables.get(0);
                return new ExprAssignmentStraight(node, new ExprAssignmentLHSArrayElement(variable.getVariableMetadata().getVariableName(), array.getIndexes()), rhs);
            }
            throw new ExprValidationException("Unrecognized left-hand-side assignment '" + ExprNodeUtilityPrint.toExpressionStringMinPrecedenceSafe(dot) + "'");
        }
        if (lhs instanceof ExprTableAccessNode) {
            throw new ExprValidationException("Table access expression not allowed on the left hand side, please remove the table prefix");
        }
        return null;
    }

    private static Pair<String, ExprNode> checkGetAssignmentToProp(ExprNode node) {
        if (!(node instanceof ExprEqualsNode)) {
            return null;
        }
        ExprEqualsNode equals = (ExprEqualsNode) node;
        if (!(equals.getChildNodes()[0] instanceof ExprIdentNode)) {
            return null;
        }
        ExprIdentNode identNode = (ExprIdentNode) equals.getChildNodes()[0];
        return new Pair<>(identNode.getFullUnresolvedName(), equals.getChildNodes()[1]);
    }

    public static ExprEqualsNode getEqualsNodeIfAssignment(ExprNode node) {
        if (!(node instanceof ExprEqualsNode)) {
            return null;
        }
        return (ExprEqualsNode) node;
    }

    public static void validateNoSpecialsGroupByExpressions(ExprNode[] groupByNodes) throws ExprValidationException {
        ExprNodeSubselectDeclaredDotVisitor visitorSubselects = new ExprNodeSubselectDeclaredDotVisitor();
        ExprNodeGroupingVisitorWParent visitorGrouping = new ExprNodeGroupingVisitorWParent();
        List<ExprAggregateNode> aggNodesInGroupBy = new ArrayList<ExprAggregateNode>(1);

        for (ExprNode groupByNode : groupByNodes) {

            // no subselects
            groupByNode.accept(visitorSubselects);
            if (visitorSubselects.getSubselects().size() > 0) {
                throw new ExprValidationException("Subselects not allowed within group-by");
            }

            // no special grouping-clauses
            groupByNode.accept(visitorGrouping);
            if (!visitorGrouping.getGroupingIdNodes().isEmpty()) {
                throw ExprGroupingIdNode.makeException("grouping_id");
            }
            if (!visitorGrouping.getGroupingNodes().isEmpty()) {
                throw ExprGroupingIdNode.makeException("grouping");
            }

            // no aggregations allowed
            ExprAggregateNodeUtil.getAggregatesBottomUp(groupByNode, aggNodesInGroupBy);
            if (!aggNodesInGroupBy.isEmpty()) {
                throw new ExprValidationException("Group-by expressions cannot contain aggregate functions");
            }
        }
    }
}
