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
package com.espertech.esper.common.internal.xmlxsd.core;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.common.ConfigurationCommonEventTypeXMLDOM;
import com.espertech.esper.common.internal.event.xml.SchemaModel;
import com.espertech.esper.common.internal.event.xml.SchemaXMLEventType;
import com.espertech.esper.common.internal.event.xml.XMLEventBean;
import com.espertech.esper.common.internal.support.SupportClasspathImport;
import junit.framework.TestCase;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import java.net.URL;

public class TestSchemaXMLEventType extends TestCase {

    private EventBean eventSchemaOne;

    protected void setUp() throws Exception {

        URL schemaUrl = TestSchemaXMLEventType.class.getClassLoader().getResource("regression/simpleSchema.xsd");
        ConfigurationCommonEventTypeXMLDOM configNoNS = new ConfigurationCommonEventTypeXMLDOM();
        configNoNS.setXPathPropertyExpr(true);
        configNoNS.setSchemaResource(schemaUrl.toString());
        configNoNS.setRootElementName("simpleEvent");
        configNoNS.addXPathProperty("customProp", "count(/ss:simpleEvent/ss:nested3/ss:nested4)", XPathConstants.NUMBER);
        configNoNS.addNamespacePrefix("ss", "samples:schemas:simpleSchema");
        SchemaModel model = XSDSchemaMapper.loadAndMap(schemaUrl.toString(), null, SupportClasspathImport.INSTANCE);
        SchemaXMLEventType eventTypeNoNS = new SchemaXMLEventType(null, configNoNS, model, null, null, null, null, null, new EventTypeXMLXSDHandlerImpl());

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);

        Document noNSDoc = builderFactory.newDocumentBuilder().parse(TestSchemaXMLEventType.class.getClassLoader().getResourceAsStream("regression/simpleWithSchema.xml"));
        eventSchemaOne = new XMLEventBean(noNSDoc.getDocumentElement(), eventTypeNoNS);
    }

    public void testSimpleProperies() {
        assertEquals("SAMPLE_V6", eventSchemaOne.get("prop4"));
    }

    public void testNestedProperties() {
        assertEquals(Boolean.TRUE, eventSchemaOne.get("nested1.prop2"));
        assertEquals(Boolean.class, eventSchemaOne.get("nested1.prop2").getClass());
    }

    public void testMappedProperties() {
        assertEquals("SAMPLE_V8", eventSchemaOne.get("nested3.nested4('a').prop5[1]"));
        assertEquals("SAMPLE_V11", eventSchemaOne.get("nested3.nested4('c').prop5[1]"));
    }

    public void testIndexedProperties() {
        assertEquals(5, eventSchemaOne.get("nested1.nested2.prop3[2]"));
        assertEquals(Integer.class, eventSchemaOne.getEventType().getPropertyType("nested1.nested2.prop3[2]"));
    }

    public void testCustomProperty() {
        assertEquals(Double.class, eventSchemaOne.getEventType().getPropertyType("customProp"));
        assertEquals(3d, eventSchemaOne.get("customProp"));
    }

    public void testAttrProperty() {
        assertEquals(Boolean.TRUE, eventSchemaOne.get("prop4.attr2"));
        assertEquals(Boolean.class, eventSchemaOne.getEventType().getPropertyType("prop4.attr2"));

        assertEquals("c", eventSchemaOne.get("nested3.nested4[2].id"));
        assertEquals(String.class, eventSchemaOne.getEventType().getPropertyType("nested3.nested4[1].id"));
    }

    public void testInvalidCollectionAccess() {
        try {
            String prop = "nested3.nested4.id";
            eventSchemaOne.getEventType().getGetter(prop);
            fail("Invalid collection access: " + prop + " accepted");
        } catch (Exception e) {
            //Expected
        }
        try {
            String prop = "nested3.nested4.nested5";
            eventSchemaOne.getEventType().getGetter(prop);
            fail("Invalid collection access: " + prop + " accepted");
        } catch (Exception e) {
            //Expected
        }
    }
}
