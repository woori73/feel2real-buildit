package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>전역 일일 상한({@code app.admissions.rate-limit.daily-limit}, 기본 500)은 IP와 무관하게 하루(KST) 동안
 * 허용하는 전체 요청 수를 제한한다. IP를 바꿔가며 호출해도 AI 호출 총량이 이 값을 넘지 않는다.
 * 카운터는 JVM 메모리에 있으므로 인스턴스별로 계산되며 재시작하면 초기화된다.
 *
 * <p>운영 환경(ALB 등 프록시 뒤)에서는 신뢰 프록시 대역(CIDR)을 반드시 설정해야 실제
 * 클라이언트 IP 기준으로 제한이 동작한다. 미설정 시 XFF를 무시하므로 안전 측으로 동작한다.
 * (예: {@code app.admissions.rate-limit.trusted-proxies=10.0.0.0/8})
 */
@Component
public class AdmissionsRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsRateLimiter.class);

    static final int DEFAULT_DAILY_LIMIT = 500;
    private static final long KST_OFFSET_MS = 9L * 3_600_000L;
    private static final long DAY_MS = 86_400_000L;
    private static final long SWEEP_INTERVAL_MS = 60_000L;
    private static final long IDLE_EVICT_MS = 3_600_000L;

    private final int perMinute;
    private final int perHour;
    private final int maxTrackedIps;
    private final int dailyLimit;
    private final LongSupplier nowMs;
    private final TrustedProxyMatcher trustedProxies;

    /**
     * IP → 요청 시각 deque (오름차순). access-order LinkedHashMap으로 LRU 퇴출.
     * 모든 접근은 synchronized 메서드 안에서만 수행한다.
     */
    private final LinkedHashMap<String, Deque<Long>> buckets;

    private long lastSweepMs;

    /** 전역 일일 카운터: KST 기준 일자(epoch day)와 그날 허용한 요청 수. synchronized 메서드에서만 접근. */
    private long dailyEpochDay = Long.MIN_VALUE;
    private int dailyCount;

    /** Spring용 생성자. */
    @Autowired
    public AdmissionsRateLimiter(
            @Value("${app.admissions.rate-limit.per-minute:10}") int perMinute,
            @Value("${app.admissions.rate-limit.per-hour:60}") int perHour,
            @Value("${app.admissions.rate-limit.max-tracked-ips:10000}") int maxTrackedIps,
            @Value("${app.admissions.rate-limit.trusted-proxies:}") String trustedProxiesCsv,
            @Value("${app.admissions.rate-limit.daily-limit:" + DEFAULT_DAILY_LIMIT + "}") int dailyLimit) {
        this(perMinute, perHour, maxTrackedIps, trustedProxiesCsv, dailyLimit, System::currentTimeMillis);
    }

    /** 테스트용 생성자 (시간 제어 가능, 일일 상한은 기본값). */
    AdmissionsRateLimiter(int perMinute, int perHour, int maxTrackedIps,
                          String trustedProxiesCsv, LongSupplier nowMs) {
        this(perMinute, perHour, maxTrackedIps, trustedProxiesCsv, DEFAULT_DAILY_LIMIT, nowMs);
    }

    /** 테스트용 생성자 (시간·일일 상한 제어 가능). */
    AdmissionsRateLimiter(int perMinute, int perHour, int maxTrackedIps,
                          String trustedProxiesCsv, int dailyLimit, LongSupplier nowMs) {
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.maxTrackedIps = Math.max(1, maxTrackedIps);
        if (dailyLimit < 1) {
            log.warn("admissions daily-limit={} is invalid (must be >= 1); using default {}", dailyLimit, DEFAULT_DAILY_LIMIT);
        }
        this.dailyLimit = dailyLimit < 1 ? DEFAULT_DAILY_LIMIT : dailyLimit;
        this.nowMs = nowMs;
        this.trustedProxies = new TrustedProxyMatcher(trustedProxiesCsv);
        if (!trustedProxies.invalidEntries().isEmpty()) {
            log.warn("admissions trusted-proxies has {} invalid/too-broad entries ignored: {}",
                    trustedProxies.invalidEntries().size(), trustedProxies.invalidEntries());
        }
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
     * 클라이언트 IP 판별. 요청을 직접 보낸 상대(remoteAddr)가 신뢰 프록시일 때만 X-Forwarded-For를 본다.
     *
     * <p>XFF의 <b>왼쪽 항목은 클라이언트가 임의로 보낸 값</b>이라(ALB 등은 실제 접속 IP를 오른쪽에 덧붙일 뿐)
     * 그대로 쓰면 IP 제한을 우회할 수 있다. 그래서 오른쪽부터 읽으며 신뢰 프록시 항목을 건너뛰고,
     * 처음 만나는 비신뢰 IP를 클라이언트로 본다. 해석할 수 없는 항목을 만나면 값을 믿을 수 없으므로
     * remoteAddr로 폴백한다.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || !trustedProxies.isTrusted(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }
        String[] parts = xff.split(",", -1);
        String candidate = null;
        for (int i = parts.length - 1; i >= 0; i--) {
            String entry = sanitizeXffEntry(parts[i]);
            if (entry.isEmpty() || parseIp(entry) == null) {
                return remoteAddr;
            }
            if (!trustedProxies.isTrusted(entry)) {
                return entry;
            }
            candidate = entry;
        }
        return candidate != null ? candidate : remoteAddr;
    }

    /** 설정에서 무시된(형식 오류·과도하게 넓은) 신뢰 프록시 항목 (테스트/모니터링용). */
    public List<String> invalidTrustedProxyEntries() {
        return trustedProxies.invalidEntries();
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

    /**
     * 전역 일일 상한 확인. KST 날짜가 바뀌면 카운터를 0으로 되돌린다.
     * 허용되면 카운트를 올리고 true, 상한에 도달했으면 false(카운트는 올리지 않음).
     */
    public synchronized boolean tryAcquireDaily() {
        long today = (nowMs.getAsLong() + KST_OFFSET_MS) / DAY_MS;
        if (today != dailyEpochDay) {
            dailyEpochDay = today;
            dailyCount = 0;
        }
        if (dailyCount >= dailyLimit) {
            return false;
        }
        dailyCount++;
        return true;
    }

    /** 오늘(KST) 허용한 요청 수 (테스트/모니터링용). */
    public synchronized int dailyUsed() {
        long today = (nowMs.getAsLong() + KST_OFFSET_MS) / DAY_MS;
        return today == dailyEpochDay ? dailyCount : 0;
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
        private final List<String> invalid = new ArrayList<>();

        /** 형식이 틀렸거나 과도하게 넓어 무시한 항목. */
        List<String> invalidEntries() {
            return List.copyOf(invalid);
        }

        /** 이보다 넓은 대역(IPv4 /8 미만, IPv6 /16 미만)을 프록시로 신뢰하면 XFF 위조를 막을 수 없으므로 거부한다. */
        private static boolean tooBroad(byte[] addr, int prefixLen) {
            return prefixLen < (addr.length == 4 ? 8 : 16);
        }

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
                            invalid.add(t);
                            continue;
                        }
                        networks.add(addr);
                        prefixLens.add(addr.length * 8);
                    } else {
                        byte[] addr = parseIp(t.substring(0, slash).strip());
                        if (addr == null) {
                            invalid.add(t);
                            continue;
                        }
                        int pfx = Integer.parseInt(t.substring(slash + 1).strip());
                        if (pfx < 0 || pfx > addr.length * 8 || tooBroad(addr, pfx)) {
                            invalid.add(t);
                            continue;
                        }
                        networks.add(addr);
                        prefixLens.add(pfx);
                    }
                } catch (NumberFormatException e) {
                    invalid.add(t);
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
