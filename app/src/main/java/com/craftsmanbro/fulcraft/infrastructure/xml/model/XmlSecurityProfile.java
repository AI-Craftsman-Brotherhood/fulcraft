package com.craftsmanbro.fulcraft.infrastructure.xml.model;

/**
 * Security profile for XML parsing/transformation.
 *
 * <p>The default strict profile blocks external entity resolution and external resources.
 */
public record XmlSecurityProfile(
    boolean secureProcessing,
    boolean disallowDoctypeDeclaration,
    boolean allowExternalGeneralEntities,
    boolean allowExternalParameterEntities,
    String allowedExternalDtd,
    String allowedExternalSchema,
    String allowedExternalStylesheet) {

  private static final String NO_EXTERNAL_ACCESS = "";

  public XmlSecurityProfile {
    allowedExternalDtd = normalizeAccessValue(allowedExternalDtd);
    allowedExternalSchema = normalizeAccessValue(allowedExternalSchema);
    allowedExternalStylesheet = normalizeAccessValue(allowedExternalStylesheet);
  }

  public static XmlSecurityProfile strictDefaults() {
    return new XmlSecurityProfile(
        true,
        true,
        false,
        false,
        NO_EXTERNAL_ACCESS,
        NO_EXTERNAL_ACCESS,
        NO_EXTERNAL_ACCESS);
  }

  private static String normalizeAccessValue(final String value) {
    return value == null ? NO_EXTERNAL_ACCESS : value;
  }
}
