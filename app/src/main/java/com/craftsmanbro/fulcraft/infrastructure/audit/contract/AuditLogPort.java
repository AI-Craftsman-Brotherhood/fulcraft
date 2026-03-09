package com.craftsmanbro.fulcraft.infrastructure.audit.contract;

import com.craftsmanbro.fulcraft.infrastructure.audit.model.AuditEvent;

/** Contract for recording LLM request/response audit events. */
public interface AuditLogPort {

  boolean isEnabled();

  void logExchange(AuditEvent event);
}
