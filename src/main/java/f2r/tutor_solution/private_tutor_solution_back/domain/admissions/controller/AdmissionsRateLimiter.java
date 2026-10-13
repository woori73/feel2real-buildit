package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 입시상담 채팅 API rate limiter — IP 기반 슬라이딩 윈도우 (분당 N회, 시간당 M회).
 *
 * <p>보안 핵심:
 * <ul>
 *   <li>{@code X-Forwarded-For}는 신뢰된 프록시
 *       ({@code app.admissions.rate-limit.trusted-proxies}, IP 또는 CIDR 목록)에서 수신한
 *       요청에 대해서만 신뢰한다. 신뢰되지 않은 경로의 XFF는 위조 가능하므로 무시하고
 *       {@code getRemoteAddr()}을 사용한다.</li>
 *   <li>IP별 요청 기록은 상한({@code max-tracked-ips}, 기본 10,000)을 두어 LRU 방식으로
 *       퇴출하고, 1시간 이상 활동이 없는 항목은 주기적으로 정리한다 — 기록의 무한 증가 방지.</li>
 * </ul>
 *
 * <p>운영 환경(ALB 등 프록시 뒤)에서는 신뢰 프록시 대역(CIDR)을 반드시 설정해야 실제
 * 클라이언트 IP 기준으로 제한이 동작한다. 미설정 시 XFF를 무시하므로 안전 측으로 동작한다.
 * (예: {@code app.admissions.rate-limit.trusted-proxies=10.0.0.0/8})
 */
@Component
public class AdmissionsRateLimiter {

    private static final long SWEEP_INTERVAL_MS = 60_000L;
    private static final long IDLE_EVICT_MS = 3_600_000L;

    private final int perMinute;
    private final int perHour;
    private final int maxTrackedIps;
    private final LongSupplier nowMs;
    private final TrustedProxyMatcher trustedProxies;

    /**
     * IP → 요청 시각 deque (오름차순). access-order LinkedHashMap으로 LRU 퇴출.
     * 모든 접근은 synchronized 메서드 안에서만 수행한다.
     */
    private final LinkedHashMap<String, Deque<Long>> buckets;

    private long lastSweepMs;

    /** Spring용 생성자. */
    @Autowired
    public AdmissionsRateLimiter(
            @Value("${app.admissions.rate-limit.per-minute:10}") int perMinute,
            @Value("${app.admissions.rate-limit.per-hour:60}") int perHour,
            @Value("${app.admissions.rate-limit.max-tracked-ips:10000}") int maxTrackedIps,
            @Value("${app.admissions.rate-limit.trusted-proxies:}") String trustedProxiesCsv) {
        this(perMinute, perHour, maxTrackedIps, trustedProxiesCsv, System::currentTimeMillis);
    }

    /** 테스트용 생성자 (시간 제어 가능). */
    AdmissionsRateLimiter(int perMinute, int perHour, int maxTrackedIps,
                          String trustedProxiesCsv, LongSupplier nowMs) {
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.maxTrackedIps = Math.max(1, maxTrackedIps);
        this.nowMs = nowMs;
        this.trustedProxies = new TrustedProxyMatcher(trustedProxiesCsv);
        int cap = Math.max(16, this.maxTrackedIps);
        this.buckets = new LinkedHashMap<>(cap, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Long>> eldest) {
                return size() > AdmissionsRateLimiter.this.maxTrackedIps;
            }
        };
        this.lastSweepMs = nowMs.getAsLong();
    }

    /**
     * 클라이언트 IP 판별. 신뢰된 프록시에서 온 요청일 때만 X-Forwarded-For의 첫 번째
     * 항목을 사용하고, 그 외에는 헤더 위조 가능성을 배제하기 위해 remoteAddr를 사용한다.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && trustedProxies.isTrusted(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = sanitizeXffEntry(xff.split(",", -1)[0]);
                if (!first.isEmpty() && parseIp(first) != null) {
                    return first;
                }
            }
        }
        return remoteAddr;
    }

    /**
     * 요청 허용 여부. 허용되면 요청 시각을 기록하고 true를, 제한 초과면 false를 반환한다
     * (차단된 요청은 기록하지 않는다).
     */
    public synchronized boolean tryAcquire(String ip) {
        if (ip == null) {
            return false;
        }
        long now = nowMs.getAsLong();
        maybeSweep(now);
        Deque<Long> times = buckets.get(ip);
        if (times == null) {
            times = new ArrayDeque<>();
            buckets.put(ip, times);
        }
        long oneMinuteAgo = now - 60_000L;
        long oneHourAgo = now - 3_600_000L;
        while (!times.isEmpty() && times.peekFirst() < oneHourAgo) {
            times.pollFirst();
        }
        long lastMinute = 0;
        for (long t : times) {
            if (t >= oneMinuteAgo) {
                lastMinute++;
            }
        }
        if (lastMinute >= perMinute || times.size() >= perHour) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /** 현재 추적 중인 IP 수 (테스트/모니터링용). */
    public synchronized int trackedIpCount() {
        return buckets.size();
    }

    /** 1시간 이상 활동이 없는 IP 항목 정리 (최대 60초 간격으로 수행). */
    private void maybeSweep(long now) {
        if (now - lastSweepMs < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepMs = now;
        long idleBefore = now - IDLE_EVICT_MS;
        Iterator<Map.Entry<String, Deque<Long>>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> times = it.next().getValue();
            while (!times.isEmpty() && times.peekFirst() < idleBefore) {
                times.pollFirst();
            }
            if (times.isEmpty()) {
                it.remove();
            }
        }
    }

    /** XFF 첫 항목 정제: {@code [ipv6]:port} 형태와 {@code ipv4:port} 형태의 포트 제거. */
    static String sanitizeXffEntry(String raw) {
        if (raw == null) {
            return "";
        }
        String e = raw.strip();
        if (e.startsWith("[")) {
            int end = e.indexOf(']');
            return end > 0 ? e.substring(1, end).strip() : "";
        }
        // "1.2.3.4:5678" → "1.2.3.4" (IPv4에만 적용; IPv6는 콜론이 여러 개)
        int firstColon = e.indexOf(':');
        int lastColon = e.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon && e.indexOf('.') > 0) {
            e = e.substring(0, firstColon).strip();
        }
        return e;
    }

    /** IP 리터럴 파싱 (DNS 조회 없음 — 위조 헤더 처리 시 지연/차단 방지). 실패 시 null. */
    static byte[] parseIp(String s) {
        if (s == null || s.isEmpty() || s.length() > 45) {
            return null;
        }
        byte[] v4 = parseIpv4(s);
        if (v4 != null) {
            return v4;
        }
        return parseIpv6(s);
    }

    private static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            for (int j = 0; j < p.length(); j++) {
                if (!Character.isDigit(p.charAt(j))) {
                    return null;
                }
            }
            int v = Integer.parseInt(p);
            if (v > 255) {
                return null;
            }
            b[i] = (byte) v;
        }
        return b;
    }

    private static byte[] parseIpv6(String s) {
        int pct = s.indexOf('%');
        if (pct >= 0) {
            s = s.substring(0, pct); // scope id 제거
        }
        if (s.isEmpty()) {
            return null;
        }
        int dbl = s.indexOf("::");
        if (dbl >= 0 && s.indexOf("::", dbl + 2) >= 0) {
            return null; // "::" 2회 이상
        }
        List<Integer> head = parseV6Parts(dbl >= 0 ? s.substring(0, dbl) : s);
        List<Integer> tail = dbl >= 0 ? parseV6Parts(s.substring(dbl + 2)) : List.of();
        if (head == null || tail == null) {
            return null;
        }
        int total = head.size() + tail.size();
        if (dbl >= 0) {
            if (total > 8) {
                return null;
            }
        } else if (total != 8) {
            return null;
        }
        byte[] out = new byte[16];
        int pos = 0;
        for (int v : head) {
            out[pos++] = (byte) (v >>> 8);
            out[pos++] = (byte) v;
        }
        if (dbl >= 0) {
            pos = 16 - tail.size() * 2;
        }
        for (int v : tail) {
            out[pos++] = (byte) (v >>> 8);
            out[pos++] = (byte) v;
        }
        return out;
    }

    /** 콜론 구분 16비트 그룹 파싱. 빈 문자열이면 빈 리스트. 마지막 토큰의 내장 IPv4 허용. */
    private static List<Integer> parseV6Parts(String part) {
        List<Integer> vals = new ArrayList<>();
        if (part.isEmpty()) {
            return vals;
        }
        String[] tokens = part.split(":", -1);
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.indexOf('.') >= 0) {
                if (i != tokens.length - 1) {
                    return null; // IPv4는 마지막 토큰에만 허용
                }
                byte[] v4 = parseIpv4(t);
                if (v4 == null) {
                    return null;
                }
                vals.add(((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF));
                vals.add(((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF));
            } else {
                if (t.isEmpty() || t.length() > 4) {
                    return null;
                }
                int v;
                try {
                    v = Integer.parseInt(t, 16);
                } catch (NumberFormatException e) {
                    return null;
                }
                vals.add(v);
            }
        }
        return vals;
    }

    /** 신뢰 프록시 판별기 — 정확한 IP 또는 CIDR 목록 (IPv4/IPv6). */
    static final class TrustedProxyMatcher {
        private final List<byte[]> networks = new ArrayList<>();
        private final List<Integer> prefixLens = new ArrayList<>();

        TrustedProxyMatcher(String csv) {
            if (csv == null) {
                return;
            }
            for (String token : csv.split(",", -1)) {
                String t = token.strip();
                if (t.isEmpty()) {
                    continue;
                }
                int slash = t.indexOf('/');
                try {
                    if (slash < 0) {
                        byte[] addr = parseIp(t);
                        if (addr == null) {
                            continue;
                        }
                        networks.add(addr);
                        prefixLens.add(addr.length * 8);
                    } else {
                        byte[] addr = parseIp(t.substring(0, slash).strip());
                        if (addr == null) {
                            continue;
                        }
                        int pfx = Integer.parseInt(t.substring(slash + 1).strip());
                        if (pfx < 0 || pfx > addr.length * 8) {
                            continue;
                        }
                        networks.add(addr);
                        prefixLens.add(pfx);
                    }
                } catch (NumberFormatException ignored) {
                    // 잘못된 항목은 무시
                }
            }
        }

        boolean isTrusted(String remoteAddr) {
            if (networks.isEmpty() || remoteAddr == null) {
                return false;
            }
            byte[] r = parseIp(remoteAddr);
            if (r == null) {
                return false;
            }
            for (int i = 0; i < networks.size(); i++) {
                byte[] n = networks.get(i);
                if (n.length != r.length) {
                    continue;
                }
                if (prefixMatch(n, r, prefixLens.get(i))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean prefixMatch(byte[] net, byte[] addr, int prefixLen) {
            int full = prefixLen / 8;
            int rem = prefixLen % 8;
            for (int i = 0; i < full; i++) {
                if (net[i] != addr[i]) {
                    return false;
                }
            }
            if (rem > 0) {
                int mask = (0xFF << (8 - rem)) & 0xFF;
                if ((net[full] & mask) != (addr[full] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
