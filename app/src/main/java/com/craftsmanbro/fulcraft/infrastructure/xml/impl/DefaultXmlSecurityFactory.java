package com.craftsmanbro.fulcraft.infrastructure.xml.impl;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.xml.contract.XmlSecurityFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.xml.model.XmlSecurityProfile;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

/** Default JAXP-based implementation of {@link XmlSecurityFactoryPort}. */
public final class DefaultXmlSecurityFactory implements XmlSecurityFactoryPort {

  private static final String LOG_MESSAGE_KEY = "infra.common.log.message";
  private static final String PARSER_SECURITY_FEATURES_UNSUPPORTED =
      "XML parser security features not supported";
  private static final String PARSER_ACCESS_RESTRICTIONS_UNSUPPORTED =
      "XML parser access restrictions not supported";
  private static final String TRANSFORMER_SECURE_PROCESSING_UNSUPPORTED =
      "XML transformer secure processing not supported";
  private static final String TRANSFORMER_ACCESS_RESTRICTIONS_UNSUPPORTED =
      "XML transformer access restrictions not supported";

  private static final String DISALLOW_DOCTYPE_DECL_FEATURE =
      "http://apache.org/xml/features/disallow-doctype-decl";
  private static final String EXTERNAL_GENERAL_ENTITIES_FEATURE =
      "http://xml.org/sax/features/external-general-entities";
  private static final String EXTERNAL_PARAMETER_ENTITIES_FEATURE =
      "http://xml.org/sax/features/external-parameter-entities";

  private final XmlSecurityProfile profile;

  public DefaultXmlSecurityFactory() {
    this(XmlSecurityProfile.strictDefaults());
  }

  public DefaultXmlSecurityFactory(final XmlSecurityProfile profile) {
    this.profile = profile == null ? XmlSecurityProfile.strictDefaults() : profile;
  }

  @Override
  public DocumentBuilderFactory createDocumentBuilderFactory() {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setExpandEntityReferences(false);
    factory.setXIncludeAware(false);
    configureDocumentBuilderFeatures(factory);
    configureDocumentBuilderAccessRestrictions(factory);
    return factory;
  }

  @Override
  public TransformerFactory createTransformerFactory() {
    final TransformerFactory factory = TransformerFactory.newInstance();
    configureTransformerSecureProcessing(factory);
    configureTransformerAccessRestrictions(factory);
    return factory;
  }

  private void configureDocumentBuilderFeatures(final DocumentBuilderFactory factory) {
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, profile.secureProcessing());
      factory.setFeature(
          DISALLOW_DOCTYPE_DECL_FEATURE, profile.disallowDoctypeDeclaration());
      factory.setFeature(
          EXTERNAL_GENERAL_ENTITIES_FEATURE, profile.allowExternalGeneralEntities());
      factory.setFeature(
          EXTERNAL_PARAMETER_ENTITIES_FEATURE, profile.allowExternalParameterEntities());
    } catch (ParserConfigurationException e) {
      logUnsupported(PARSER_SECURITY_FEATURES_UNSUPPORTED, e);
    }
  }

  private void configureDocumentBuilderAccessRestrictions(final DocumentBuilderFactory factory) {
    try {
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, profile.allowedExternalDtd());
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, profile.allowedExternalSchema());
    } catch (IllegalArgumentException e) {
      logUnsupported(PARSER_ACCESS_RESTRICTIONS_UNSUPPORTED, e);
    }
  }

  private void configureTransformerSecureProcessing(final TransformerFactory factory) {
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, profile.secureProcessing());
    } catch (TransformerConfigurationException e) {
      logUnsupported(TRANSFORMER_SECURE_PROCESSING_UNSUPPORTED, e);
    }
  }

  private void configureTransformerAccessRestrictions(final TransformerFactory factory) {
    try {
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, profile.allowedExternalDtd());
      factory.setAttribute(
          XMLConstants.ACCESS_EXTERNAL_STYLESHEET, profile.allowedExternalStylesheet());
    } catch (IllegalArgumentException e) {
      logUnsupported(TRANSFORMER_ACCESS_RESTRICTIONS_UNSUPPORTED, e);
    }
  }

  private static void logUnsupported(final String context, final Exception e) {
    logDebug(context + ": " + e.getMessage());
  }

  private static void logDebug(final String message) {
    Logger.debug(MessageSource.getMessage(LOG_MESSAGE_KEY, message));
  }

  @Override
  public XmlSecurityProfile profile() {
    return profile;
  }
}
