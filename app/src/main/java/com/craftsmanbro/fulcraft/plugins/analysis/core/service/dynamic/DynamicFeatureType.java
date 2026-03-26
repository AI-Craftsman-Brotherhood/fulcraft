package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

/** Types of dynamic features detected during static analysis. */
public enum DynamicFeatureType {
  /** Reflection API usage (Class.forName, Method.invoke, etc.) */
  REFLECTION,

  /** Dynamic proxy creation (Proxy.newProxyInstance) */
  PROXY,

  /** ClassLoader operations (loadClass, defineClass) */
  CLASSLOADER,

  /** Service provider loading (ServiceLoader.load) */
  SERVICELOADER,

  /** Dependency injection patterns (Spring getBean, @Inject, etc.) */
  DI,

  /** Annotation-based code generation indicators (Lombok, etc.) */
  ANNOTATION,

  /** Serialization with dynamic type resolution (Jackson, etc.) */
  SERIALIZATION,

  /** InvokeDynamic / MethodHandles usage */
  INVOKEDYNAMIC;

  /** Stable name for serialization/reporting. */
  public String wireName() {
    return name();
  }
}
