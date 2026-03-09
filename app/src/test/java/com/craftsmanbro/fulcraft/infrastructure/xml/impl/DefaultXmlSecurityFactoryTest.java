package com.craftsmanbro.fulcraft.infrastructure.xml.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import org.junit.jupiter.api.Test;

class DefaultXmlSecurityFactoryTest {

  private final DefaultXmlSecurityFactory xmlSecurityFactory = new DefaultXmlSecurityFactory();

  @Test
  void createSecureDocumentBuilderFactory_disablesEntityExpansion() {
    DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();

    assertNotNull(factory);
    assertFalse(factory.isExpandEntityReferences());
    assertFalse(factory.isXIncludeAware());
  }

  @Test
  void createSecureDocumentBuilderFactory_setsSecurityFeaturesWhenSupported() {
    DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();

    assertFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    assertFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
    assertFeatureIfSupported(
        factory, "http://xml.org/sax/features/external-general-entities", false);
    assertFeatureIfSupported(
        factory, "http://xml.org/sax/features/external-parameter-entities", false);
  }

  @Test
  void createSecureDocumentBuilderFactory_setsExternalAccessAttributesWhenSupported() {
    DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();

    assertDocumentBuilderAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    assertDocumentBuilderAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
  }

  @Test
  void createSecureTransformerFactory_setsSecureProcessingWhenSupported() {
    TransformerFactory factory = xmlSecurityFactory.createTransformerFactory();

    assertNotNull(factory);
    assertTransformerFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
  }

  @Test
  void createSecureTransformerFactory_setsExternalAccessAttributesWhenSupported() {
    TransformerFactory factory = xmlSecurityFactory.createTransformerFactory();

    assertTransformerAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    assertTransformerAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
  }

  private static void assertDocumentBuilderAttributeIfSupported(
      DocumentBuilderFactory factory, String attribute, String expected) {
    try {
      assertEquals(expected, factory.getAttribute(attribute));
    } catch (IllegalArgumentException e) {
      // Parser may not support the attribute; keep the test portable.
    }
  }

  private static void assertFeatureIfSupported(
      DocumentBuilderFactory factory, String feature, boolean expected) {
    try {
      assertEquals(expected, factory.getFeature(feature));
    } catch (ParserConfigurationException e) {
      // Parser may not support the feature; keep the test portable.
    }
  }

  private static void assertTransformerFeatureIfSupported(
      TransformerFactory factory, String feature, boolean expected) {
    try {
      assertEquals(expected, factory.getFeature(feature));
    } catch (RuntimeException e) {
      // Transformer may not support the feature; keep the test portable.
    }
  }

  private static void assertTransformerAttributeIfSupported(
      TransformerFactory factory, String attribute, String expected) {
    try {
      assertEquals(expected, factory.getAttribute(attribute));
    } catch (IllegalArgumentException e) {
      // Transformer may not support the attribute; keep the test portable.
    }
  }
}
