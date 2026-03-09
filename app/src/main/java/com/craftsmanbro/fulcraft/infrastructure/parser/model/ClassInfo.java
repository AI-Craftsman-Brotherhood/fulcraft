package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassInfo {

  public static final String UNKNOWN_CLASS = "UnknownClass";

  @JsonProperty("fqn")
  private String fqn;

  @JsonProperty("file_path")
  private String filePath;

  @JsonProperty("loc")
  private int loc;

  @JsonProperty("method_count")
  private int methodCount;

  @JsonProperty("is_interface")
  private boolean isInterface;

  @JsonProperty("is_abstract")
  private boolean isAbstract;

  @JsonProperty("is_anonymous")
  private boolean isAnonymous;

  @JsonProperty("is_nested_class")
  private boolean isNestedClass;

  @JsonProperty("has_nested_classes")
  private boolean hasNestedClasses;

  @JsonProperty("extends_types")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<String> extendsTypes = new ArrayList<>();

  @JsonProperty("implements_types")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<String> implementsTypes = new ArrayList<>();

  @JsonProperty("fields")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<FieldInfo> fields = new ArrayList<>();

  @JsonProperty("annotations")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<String> annotations = new ArrayList<>();

  @JsonProperty("imports")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<String> imports = new ArrayList<>();

  @JsonProperty("methods")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<MethodInfo> methods = new ArrayList<>();

  @JsonProperty("is_dead_code")
  private boolean isDeadCode;

  public String getFqn() {
    return fqn;
  }

  public void setFqn(final String fqn) {
    this.fqn = fqn;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(final String filePath) {
    this.filePath = filePath;
  }

  public int getLoc() {
    return loc;
  }

  public void setLoc(final int loc) {
    this.loc = loc;
  }

  public int getMethodCount() {
    if (methods.isEmpty()) {
      return methodCount;
    }
    return methods.size();
  }

  public void setMethodCount(final int methodCount) {
    this.methodCount = methodCount;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public void setInterface(final boolean isInterface) {
    this.isInterface = isInterface;
  }

  public boolean isAbstract() {
    return isAbstract;
  }

  public void setAbstract(final boolean isAbstract) {
    this.isAbstract = isAbstract;
  }

  public boolean isAnonymous() {
    return isAnonymous;
  }

  public void setAnonymous(final boolean isAnonymous) {
    this.isAnonymous = isAnonymous;
  }

  public boolean isNestedClass() {
    return isNestedClass;
  }

  public void setNestedClass(final boolean isNestedClass) {
    this.isNestedClass = isNestedClass;
  }

  public boolean hasNestedClasses() {
    return hasNestedClasses;
  }

  public void setHasNestedClasses(final boolean hasNestedClasses) {
    this.hasNestedClasses = hasNestedClasses;
  }

  public List<String> getExtendsTypes() {
    return Collections.unmodifiableList(extendsTypes);
  }

  public void setExtendsTypes(final List<String> extendsTypes) {
    this.extendsTypes = Objects.requireNonNullElseGet(extendsTypes, ArrayList::new);
  }

  public void addExtendsType(final String type) {
    if (type != null && !type.isBlank()) {
      extendsTypes.add(type);
    }
  }

  public List<String> getImplementsTypes() {
    return Collections.unmodifiableList(implementsTypes);
  }

  public void setImplementsTypes(final List<String> implementsTypes) {
    this.implementsTypes = Objects.requireNonNullElseGet(implementsTypes, ArrayList::new);
  }

  public void addImplementsType(final String type) {
    if (type != null && !type.isBlank()) {
      implementsTypes.add(type);
    }
  }

  public List<FieldInfo> getFields() {
    return Collections.unmodifiableList(fields);
  }

  public void setFields(final List<FieldInfo> fields) {
    this.fields = Objects.requireNonNullElseGet(fields, ArrayList::new);
  }

  public void addField(final FieldInfo field) {
    if (field != null) {
      fields.add(field);
    }
  }

  public List<String> getAnnotations() {
    return Collections.unmodifiableList(annotations);
  }

  public void setAnnotations(final List<String> annotations) {
    this.annotations = Objects.requireNonNullElseGet(annotations, ArrayList::new);
  }

  public void addAnnotation(final String annotation) {
    if (annotation != null && !annotation.isBlank()) {
      annotations.add(annotation);
    }
  }

  public List<String> getImports() {
    return Collections.unmodifiableList(imports);
  }

  public void setImports(final List<String> imports) {
    this.imports = Objects.requireNonNullElseGet(imports, ArrayList::new);
  }

  public void addImport(final String importName) {
    if (importName != null && !importName.isBlank()) {
      imports.add(importName);
    }
  }

  public List<MethodInfo> getMethods() {
    return Collections.unmodifiableList(methods);
  }

  public void setMethods(final List<MethodInfo> methods) {
    this.methods = Objects.requireNonNullElseGet(methods, ArrayList::new);
    this.methodCount = this.methods.size();
  }

  public void addMethod(final MethodInfo method) {
    if (method != null) {
      methods.add(method);
      methodCount = methods.size();
    }
  }

  public boolean isDeadCode() {
    return isDeadCode;
  }

  public void setDeadCode(final boolean isDeadCode) {
    this.isDeadCode = isDeadCode;
  }
}
