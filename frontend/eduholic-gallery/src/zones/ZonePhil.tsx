import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

const PRINCIPLES = [
  { n: '원칙 01', text: '주의력이 산만한 학생도 몰입하는 1:1 마음 코칭' },
  { n: '원칙 02', text: '공식 암기가 아닌, 문장 구조를 직접 조립하는 F2R 영문법' },
  { n: '원칙 03', text: '실수를 실력으로, 지역 내신 밀착 케어' },
  { n: '원칙 04', text: '아이 성향에 맞춘 어휘 확장부터 수능 1등급 독해 체력까지' },
];

function PhilHead() {
  return (
    <>
      <p className="flex items-center gap-2 text-xs font-bold text-muted-foreground">
        <span className="inline-block h-px w-6 bg-accent" aria-hidden /> 학습 철학
      </p>
      <h2 className="mt-3 text-[26px] font-extrabold leading-tight sm:text-4xl">박정민 원장은 이렇게 가르칩니다.</h2>
    </>
  );
}

/** CURRENT — live replica: 2×2 grid (PC) / swipe + 01/04 counter (mobile). */
export function PhilCurrent() {
  const [idx, setIdx] = useState(0);
  const rowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const row = rowRef.current;
    if (!row) return;
    const onScroll = () => {
      const i = Math.round(row.scrollLeft / (row.scrollWidth - row.clientWidth || 1) * (PRINCIPLES.length - 1));
      setIdx(Math.max(0, Math.min(PRINCIPLES.length - 1, i)));
    };
    row.addEventListener('scroll', onScroll, { passive: true });
    return () => row.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <div className="bg-[#f6f7f9] px-4 py-10 sm:py-14">
      <div className="mx-auto max-w-5xl">
        <PhilHead />
        {/* PC grid */}
        <div className="mt-8 hidden grid-cols-2 gap-4 sm:grid">
          {PRINCIPLES.map((p) => (
            <div key={p.n} className="rounded-2xl border border-border bg-white p-6 shadow-sm">
              <p className="inline-block rounded-full bg-amber-100 px-3 py-1 text-xs font-extrabold text-amber-800">{p.n}</p>
              <p className="mt-3 text-base font-extrabold leading-relaxed">{p.text}</p>
            </div>
          ))}
        </div>
        {/* Mobile swipe */}
        <div className="mt-6 sm:hidden">
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-foreground">옆으로 밀어 원칙을 확인하세요</p>
            <p className="text-xs font-extrabold tabular-nums" aria-live="polite">
              {String(idx + 1).padStart(2, '0')} / 04
            </p>
          </div>
          <div ref={rowRef} className="no-scrollbar mt-3 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 -mx-4">
            {PRINCIPLES.map((p) => (
              <div key={p.n} className="w-[82vw] shrink-0 snap-center rounded-2xl border border-border bg-white p-6 shadow-sm">
                <p className="inline-block rounded-full bg-amber-100 px-3 py-1 text-xs font-extrabold text-amber-800">{p.n}</p>
                <p className="mt-3 text-base font-extrabold leading-relaxed">{p.text}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/** OPTION A — same layout + scroll-reveal stagger. */
export function PhilOptionA() {
  return (
    <div className="bg-[#f6f7f9] px-4 py-10 sm:py-14">
      <div className="mx-auto max-w-5xl">
        <PhilHead />
        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          {PRINCIPLES.map((p, i) => (
            <motion.div
              key={p.n}
              initial={{ opacity: 0, y: 28 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-60px' }}
              transition={{ duration: 0.5, delay: (i % 2) * 0.12, ease: [0.16, 1, 0.3, 1] }}
              whileHover={{ y: -4 }}
              whileTap={{ scale: 0.98 }}
              className="rounded-2xl border border-border bg-white p-6 shadow-sm"
            >
              <p className="inline-flex items-center gap-2">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-navy text-xs font-extrabold text-white">{i + 1}</span>
                <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-extrabold text-amber-800">{p.n}</span>
              </p>
              <p className="mt-3 text-base font-extrabold leading-relaxed">{p.text}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
  );
}

/** OPTION B — stepper progression with smooth transitions (TEMP: spec implementation, NOT 21st real code; quota 0/2). */
export function PhilOptionB() {
  const [idx, setIdx] = useState(() => 0);
  const touchX = useRef<number | null>(null);
  const [direction, setDirection] = useState<'next' | 'prev' | null>(null);
  const [reduceMotion, setReduceMotion] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReduceMotion(mediaQuery.matches);
    const handler = (e: MediaQueryListEvent) => setReduceMotion(e.matches);
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);

  const goTo = (i: number) => {
    setDirection(i > idx ? 'next' : 'prev');
    setIdx(i);
    setTimeout(() => setDirection(null), 300);
  };

  const handleSwipe = (e: React.TouchEvent) => {
    if (touchX.current === null) return;
    const dx = e.changedTouches[0].clientX - touchX.current;
    if (dx < -40) setIdx((i) => Math.min(i + 1, PRINCIPLES.length - 1));
    else if (dx > 40) setIdx((i) => Math.max(i - 1, 0));
    touchX.current = null;
  };

  return (
    <div className="bg-navy px-4 py-10 sm:py-14">
      <div className="mx-auto max-w-2xl">
        <p className="text-xs font-extrabold uppercase tracking-widest text-accent-light">학습 철학 · step</p>
        <h2 className="mt-2 text-2xl font-extrabold text-white sm:text-3xl">박정민 원장은 이렇게 가르칩니다.</h2>
        <div
          className="mt-6 overflow-hidden rounded-2xl bg-white p-6 shadow-xl sm:p-8"
          onTouchStart={(e) => { touchX.current = e.touches[0].clientX; }}
          onTouchEnd={handleSwipe}
        >
          <div className="flex gap-1.5" aria-hidden>
            {PRINCIPLES.map((p, i) => (
              <motion.span
                key={p.n}
                className={cn('h-1 flex-1 rounded-full', i <= idx ? 'bg-navy' : 'bg-border')}
                animate={{ scaleX: i <= idx ? 1 : 0 }}
                initial={false}
                transition={{ duration: reduceMotion ? 0 : 0.4, ease: [0.16, 1, 0.3, 1], delay: i * 0.05 }}
              />
            ))}
          </div>
          <div className="mt-4 min-h-[7rem]" aria-live="polite">
            <motion.div
              key={idx}
              initial={reduceMotion ? { opacity: 1, x: 0 } : { opacity: 0, x: direction === 'prev' ? 30 : -30 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: reduceMotion ? 0 : 0.35, ease: [0.16, 1, 0.3, 1] }}
              className="min-h-[7rem]"
            >
              <p className="text-xs font-extrabold text-accent">{PRINCIPLES[idx].n} · {idx + 1} / 4</p>
              <motion.p
                className="mt-2 text-xl font-extrabold leading-relaxed sm:text-2xl"
                initial={reduceMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: reduceMotion ? 0 : 0.35, ease: [0.16, 1, 0.3, 1], delay: reduceMotion ? 0 : 0.1 }}
              >
                {PRINCIPLES[idx].text}
              </motion.p>
            </motion.div>
          </div>
          <div className="mt-4 flex items-center justify-between">
            <div className="flex gap-2">
              {PRINCIPLES.map((p, i) => (
                <motion.button
                  key={p.n}
                  type="button"
                  onClick={() => goTo(i)}
                  aria-label={`${p.n} 보기`}
                  aria-current={i === idx}
                  className={cn(
                    'h-9 w-9 rounded-full text-xs font-extrabold',
                    i === idx ? 'bg-navy text-white' : 'border border-border text-muted-foreground',
                  )}
                  whileHover={reduceMotion ? {} : { scale: 1.08 }}
                  whileTap={reduceMotion ? {} : { scale: 0.92 }}
                  animate={{ scale: i === idx ? 1.08 : 1 }}
                  transition={{ type: 'spring', stiffness: 300, damping: 20 }}
                >
                  {i + 1}
                </motion.button>
              ))}
            </div>
            <div className="flex gap-2">
              <motion.button
                type="button"
                onClick={() => goTo(idx - 1)}
                disabled={idx === 0}
                aria-label="이전 원칙"
                className="rounded-full border border-border p-2 disabled:opacity-30"
                whileHover={reduceMotion ? {} : { scale: 1.05 }}
                whileTap={reduceMotion ? {} : { scale: 0.95 }}
              >
                <ChevronLeft className="h-4 w-4" />
              </motion.button>
              <motion.button
                type="button"
                onClick={() => goTo(idx + 1)}
                disabled={idx === PRINCIPLES.length - 1}
                aria-label="다음 원칙"
                className="rounded-full border border-border p-2 disabled:opacity-30"
                whileHover={reduceMotion ? {} : { scale: 1.05 }}
                whileTap={reduceMotion ? {} : { scale: 0.95 }}
              >
                <ChevronRight className="h-4 w-4" />
              </motion.button>
            </div>
          </div>
        </div>
        <p className="mt-2 text-center text-[11px] text-white/50">탭·버튼·스와이프로 이동 (내용은 운영과 동일)</p>
      </div>
    </div>
  );
}
