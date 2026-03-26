package com.craftsmanbro.fulcraft.infrastructure.xml.contract;

import com.craftsmanbro.fulcraft.infrastructure.xml.model.XmlSecurityProfile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

/** Contract for creating XML parser/transformer factories with security constraints. */
public interface XmlSecurityFactoryPort {

  DocumentBuilderFactory createDocumentBuilderFactory();

  TransformerFactory createTransformerFactory();

  XmlSecurityProfile profile();
}
