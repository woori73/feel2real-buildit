package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 기술지표 deterministic 계산기.
 * AI 계산 금지 — 순수 수학 함수만 둔다. 인스턴스 상태 없음, 스레드 안전.
 *
 * 수식 기준:
 * - SMA(n): 종가 n일 단순이동평균
 * - RSI14: Wilder smoothing (최초 14일은 단순평균 후 지수평활)
 * - MACD: EMA12 - EMA26, Signal = MACD의 EMA9 (EMA seed는 최초 n개 SMA)
 * - 52주 고/저: 최근 250거래일(없으면 전체) 고가 최대/저가 최소
 */
public final class WemadeIndicatorService {

    private WemadeIndicatorService() {}

    /** endIdx 포함 n개 SMA. 데이터 부족 시 null. */
    public static Double sma(List<Double> values, int n, int endIdx) {
        if (values == null || endIdx < n - 1 || endIdx >= values.size()) return null;
        double sum = 0;
        for (int i = endIdx - n + 1; i <= endIdx; i++) sum += values.get(i);
        return sum / n;
    }

    /** EMA 시리즈. 인덱스 n-1부터 값 존재, 이전은 null. seed = 최초 n개 SMA. */
    public static List<Double> ema(List<Double> values, int n) {
        List<Double> out = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) out.add(null);
        if (values.size() < n) return out;
        double k = 2.0 / (n + 1);
        double prev = 0;
        for (int i = 0; i < n; i++) prev += values.get(i);
        prev /= n;
        out.set(n - 1, prev);
        for (int i = n; i < values.size(); i++) {
            prev = values.get(i) * k + prev * (1 - k);
            out.set(i, prev);
        }
        return out;
    }

    /** RSI14 시리즈 (Wilder). 인덱스 14부터 값 존재, 이전은 null. */
    public static List<Double> rsi14(List<Double> closes) {
        List<Double> out = new ArrayList<>(closes.size());
        for (int i = 0; i < closes.size(); i++) out.add(null);
        if (closes.size() < 15) return out;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= 14; i++) {
            double d = closes.get(i) - closes.get(i - 1);
            if (d > 0) avgGain += d; else avgLoss -= d;
        }
        avgGain /= 14; avgLoss /= 14;
        out.set(14, rsiValue(avgGain, avgLoss));
        for (int i = 15; i < closes.size(); i++) {
            double d = closes.get(i) - closes.get(i - 1);
            double gain = Math.max(d, 0), loss = Math.max(-d, 0);
            avgGain = (avgGain * 13 + gain) / 14;
            avgLoss = (avgLoss * 13 + loss) / 14;
            out.set(i, rsiValue(avgGain, avgLoss));
        }
        return out;
    }

    private static double rsiValue(double avgGain, double avgLoss) {
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - 100.0 / (1 + rs);
    }

    /** MACD 라인 시리즈 (EMA12 - EMA26). 양쪽 EMA가 있는 구간부터 값 존재. */
    public static List<Double> macdLine(List<Double> closes) {
        List<Double> e12 = ema(closes, 12);
        List<Double> e26 = ema(closes, 26);
        List<Double> out = new ArrayList<>(closes.size());
        for (int i = 0; i < closes.size(); i++) {
            Double a = e12.get(i), b = e26.get(i);
            out.add(a == null || b == null ? null : a - b);
        }
        return out;
    }

    /** Signal 라인 = MACD 라인의 EMA9. MACD null 구간 제외 후 계산, 앞부분 null 패딩. */
    public static List<Double> macdSignal(List<Double> macd) {
        List<Double> compact = new ArrayList<>();
        int firstIdx = -1;
        for (int i = 0; i < macd.size(); i++) {
            if (macd.get(i) != null) { if (firstIdx < 0) firstIdx = i; compact.add(macd.get(i)); }
        }
        List<Double> out = new ArrayList<>(macd.size());
        for (int i = 0; i < macd.size(); i++) out.add(null);
        if (compact.size() < 9) return out;
        List<Double> sig = ema(compact, 9);
        for (int i = 0; i < sig.size(); i++) out.set(firstIdx + i, sig.get(i));
        return out;
    }

    /** 최근 250거래일(미만이면 전체) 고가 최고치. */
    public static Double high52w(List<Double> highs) {
        return extreme(highs, true);
    }

    /** 최근 250거래일(미만이면 전체) 저가 최저치. */
    public static Double low52w(List<Double> lows) {
        return extreme(lows, false);
    }

    private static Double extreme(List<Double> vals, boolean max) {
        if (vals == null || vals.isEmpty()) return null;
        int from = Math.max(0, vals.size() - 250);
        double e = vals.get(from);
        for (int i = from + 1; i < vals.size(); i++) {
            e = max ? Math.max(e, vals.get(i)) : Math.min(e, vals.get(i));
        }
        return e;
    }

    /**
     * 볼린저 밴드 상단 시리즈 (SMA(n) + k*표준편차). 인덱스 n-1부터 값 존재.
     * AI 계산 금지 — 순수 수학 함수.
     */
    public static List<Double> bollingerUpper(List<Double> values, int n, double k) {
        return bollingerBand(values, n, k, 1);
    }

    /** 볼린저 밴드 하단 시리즈 (SMA(n) - k*표준편차). */
    public static List<Double> bollingerLower(List<Double> values, int n, double k) {
        return bollingerBand(values, n, k, -1);
    }

    /** 볼린저 밴드 중간선 = SMA(n). */
    public static List<Double> bollingerMiddle(List<Double> values, int n) {
        List<Double> out = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) out.add(sma(values, n, i));
        return out;
    }

    private static List<Double> bollingerBand(List<Double> values, int n, double k, int sign) {
        List<Double> out = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) out.add(null);
        if (values == null || values.size() < n) return out;
        for (int end = n - 1; end < values.size(); end++) {
            Double m = sma(values, n, end);
            if (m == null) continue;
            double var = 0;
            for (int i = end - n + 1; i <= end; i++) {
                double d = values.get(i) - m;
                var += d * d;
            }
            double sd = Math.sqrt(var / n);
            out.set(end, m + sign * k * sd);
        }
        return out;
    }

    /**
     * %B = (현재가 - 하단) / (상단 - 하단). 0~1 범위, 밴드 돌파 시 1 초과 가능.
     * 이상수급 탐지의 "볼린저 상단 접근" 판단에 사용한다.
     */
    public static Double bollingerPercentB(List<Double> values, int n, double k, int endIdx) {
        List<Double> upper = bollingerUpper(values, n, k);
        List<Double> lower = bollingerLower(values, n, k);
        if (endIdx < 0 || endIdx >= values.size()) return null;
        Double u = upper.get(endIdx), l = lower.get(endIdx);
        if (u == null || l == null || u.equals(l)) return null;
        return (values.get(endIdx) - l) / (u - l);
    }
}
