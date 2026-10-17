package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * EASY 위젯 응답 캐시 — 항목 수와 수명에 모두 상한이 있다.
 *
 * <p>요청 파라미터(businessId, month)가 키가 되므로 상한이 없으면 서로 다른 값으로 힙이 계속 늘 수 있다.
 * 그래서 (1) 만료된 항목은 조회 시와 저장 시 제거하고, (2) 항목 수가 maxEntries에 닿으면 가장 오래된
 * 항목부터 밀어낸다. 모든 접근은 하나의 모니터 안에서 직렬화된다(항목 수가 작아 비용은 무시할 수준).
 */
final class EasyDashboardWidgetCache {

  static final int DEFAULT_MAX_ENTRIES = 200;
  static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000L;

  private record Entry(long createdAt, EasyDashboardWidgetResponse response) {}

  private final int maxEntries;
  private final long ttlMillis;
  private final LongSupplier clockMillis;
  /** 삽입 순서 유지 — 맨 앞이 가장 오래된 항목. */
  private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

  EasyDashboardWidgetCache() {
    this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_MILLIS, System::currentTimeMillis);
  }

  EasyDashboardWidgetCache(int maxEntries, long ttlMillis, LongSupplier clockMillis) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be >= 1");
    }
    this.maxEntries = maxEntries;
    this.ttlMillis = ttlMillis;
    this.clockMillis = clockMillis;
  }

  synchronized Optional<EasyDashboardWidgetResponse> get(String key) {
    Entry entry = entries.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (isExpired(entry, clockMillis.getAsLong())) {
      entries.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.response());
  }

  synchronized void put(String key, EasyDashboardWidgetResponse response) {
    long now = clockMillis.getAsLong();
    purgeExpired(now);
    entries.remove(key);
    while (entries.size() >= maxEntries) {
      Iterator<String> oldest = entries.keySet().iterator();
      oldest.next();
      oldest.remove();
    }
    entries.put(key, new Entry(now, response));
  }

  synchronized void clear() {
    entries.clear();
  }

  synchronized int size() {
    return entries.size();
  }

  private void purgeExpired(long now) {
    for (Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
      if (isExpired(it.next().getValue(), now)) {
        it.remove();
      }
    }
  }

  private boolean isExpired(Entry entry, long now) {
    return now - entry.createdAt() >= ttlMillis;
  }
}
