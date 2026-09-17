import { useEffect, useRef, useState } from 'react';
import { animate, useInView } from 'framer-motion';
import { eduholic } from '@/data/eduholic';

/**
 * PROOF A — Count-up stats.
 * Spec: @cnippet-dev/counting-number (21st.dev, 라이선스 미표기 — 코드 반입 시 확인/교체 필요).
 * 실제 운영 수치(수강료·향상률·경력)가 스크롤 진입 시 카운트업.
 */
export function ProofA() {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: '-80px' });
  const [vals, setVals] = useState<number[]>(eduholic.stats.map(() => 0));

  useEffect(() => {
    if (!inView) return;
    const controls = eduholic.stats.map((s, i) =>
      animate(0, s.value, {
        duration: 1.8,
        ease: [0.16, 1, 0.3, 1],
        onUpdate: (v) =>
          setVals((prev) => {
            const next = [...prev];
            next[i] = v;
            return next;
          }),
      }),
    );
    return () => controls.forEach((c) => c.stop());
  }, [inView]);

  return (
    <div ref={ref} className="bg-paper px-4 py-8">
      <div className="mx-auto grid max-w-3xl grid-cols-3 gap-2 sm:gap-4">
        {eduholic.stats.map((s, i) => (
          <div key={s.label} className="rounded-2xl border border-border bg-white p-3 text-center shadow-sm sm:p-6">
            <p className="text-xl font-extrabold tabular-nums text-navy sm:text-4xl" aria-live="polite">
              {s.prefix}{Math.round(vals[i])}{s.suffix}
            </p>
            <p className="mt-1 text-[11px] font-semibold text-muted-foreground sm:text-sm">{s.label}</p>
          </div>
        ))}
      </div>
      <p className="mt-2 text-center text-[11px] text-muted-foreground">실제 운영 홈페이지의 수치 · 스크롤 진입 시 1회 재생</p>
    </div>
  );
}
