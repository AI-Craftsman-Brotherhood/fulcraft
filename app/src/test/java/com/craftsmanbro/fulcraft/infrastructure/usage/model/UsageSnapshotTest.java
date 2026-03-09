package com.craftsmanbro.fulcraft.infrastructure.usage.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageSnapshotTest {

  @Test
  void getScopesRestoresNullMap() {
    UsageSnapshot snapshot = new UsageSnapshot();
    snapshot.setScopes(null);

    assertNotNull(snapshot.getScopes());
    assertTrue(snapshot.getScopes().isEmpty());
  }

  @Test
  void getOrCreateScopeReturnsSameInstance() {
    UsageSnapshot snapshot = new UsageSnapshot();

    UsageSnapshot.ScopeUsage first = snapshot.getOrCreateScope("project");
    UsageSnapshot.ScopeUsage second = snapshot.getOrCreateScope("project");

    assertSame(first, second);
  }

  @Test
  void setScopesUsesProvidedMap() {
    UsageSnapshot snapshot = new UsageSnapshot();
    Map<String, UsageSnapshot.ScopeUsage> scopes = new LinkedHashMap<>();

    snapshot.setScopes(scopes);

    assertSame(scopes, snapshot.getScopes());
  }

  @Test
  void getOrCreateScopeCreatesEntryWhenMissing() {
    UsageSnapshot snapshot = new UsageSnapshot();

    UsageSnapshot.ScopeUsage created = snapshot.getOrCreateScope("new-scope");

    assertSame(created, snapshot.getScopes().get("new-scope"));
  }

  @Test
  void scopeUsageRestoresNullMaps() {
    UsageSnapshot.ScopeUsage scopeUsage = new UsageSnapshot.ScopeUsage();
    scopeUsage.setDay(null);
    scopeUsage.setMonth(null);

    assertNotNull(scopeUsage.getDay());
    assertNotNull(scopeUsage.getMonth());
  }

  @Test
  void setDayAndMonthUseProvidedMaps() {
    UsageSnapshot.ScopeUsage scopeUsage = new UsageSnapshot.ScopeUsage();
    Map<String, UsageRecord> day = new LinkedHashMap<>();
    Map<String, UsageRecord> month = new LinkedHashMap<>();

    scopeUsage.setDay(day);
    scopeUsage.setMonth(month);

    assertSame(day, scopeUsage.getDay());
    assertSame(month, scopeUsage.getMonth());
  }

  @Test
  void getOrCreateDayAndMonthReturnSameInstance() {
    UsageSnapshot.ScopeUsage scopeUsage = new UsageSnapshot.ScopeUsage();

    UsageRecord dayFirst = scopeUsage.getOrCreateDay("2024-01-15");
    UsageRecord daySecond = scopeUsage.getOrCreateDay("2024-01-15");
    UsageRecord monthFirst = scopeUsage.getOrCreateMonth("2024-01");
    UsageRecord monthSecond = scopeUsage.getOrCreateMonth("2024-01");

    assertSame(dayFirst, daySecond);
    assertSame(monthFirst, monthSecond);
  }

  @Test
  void getOrCreateDayAndMonthCreateEntriesWhenMissing() {
    UsageSnapshot.ScopeUsage scopeUsage = new UsageSnapshot.ScopeUsage();

    UsageRecord dayRecord = scopeUsage.getOrCreateDay("2024-01-16");
    UsageRecord monthRecord = scopeUsage.getOrCreateMonth("2024-02");

    assertSame(dayRecord, scopeUsage.getDay().get("2024-01-16"));
    assertSame(monthRecord, scopeUsage.getMonth().get("2024-02"));
  }
}
