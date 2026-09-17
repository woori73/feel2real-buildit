import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { ChevronLeft, ChevronRight, Quote } from 'lucide-react';
import { cn } from '@/lib/utils';
import { eduholic } from '@/data/eduholic';

const items = [
  { role: '원장 메시지', text: eduholic.heroQuote },
  { role: '원칙 01', text: '주의력이 산만한 학생도 몰입하는 1:1 마음 코칭' },
  { role: '원칙 02', text: '공식 암기가 아닌, 문장 구조를 직접 조립하는 F2R 영문법' },
  { role: '원칙 03', text: '실수를 실력으로, 지역 내신 밀착 케어' },
  { role: '원칙 04', text: '아이 성향에 맞춘 어휘 확장부터 수능 1등급 독해 체력까지' },
];

/**
 * TEMP — spec implementation, NOT 21st real code (awaiting retrieval).
 * Spec: @manuarora700/animated-testimonials (Aceternity UI, 21st.dev, MIT).
 * 단어별 등장 + 자동재생 + 이전/다음. 문구는 실제 학습 철학 그대로.
 */
export function ProofB() {
  const [idx, setIdx] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused) return;
    const t = setInterval(() => setIdx((i) => (i + 1) % items.length), 6000);
    return () => clearInterval(t);
  }, [paused]);

  const words = items[idx].text.split(' ');

  return (
    <div
      className="bg-navy px-4 py-8"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onTouchStart={() => setPaused(true)}
      onTouchEnd={() => setPaused(false)}
    >
      <div className="mx-auto max-w-2xl rounded-2xl bg-white/5 p-5 backdrop-blur sm:p-8">
        <Quote className="h-6 w-6 text-accent-light" />
        <div className="mt-3 min-h-[7rem] sm:min-h-[6rem]">
          <AnimatePresence mode="wait">
            <motion.blockquote
              key={idx}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -14 }}
              transition={{ duration: 0.35 }}
            >
              <span className="text-[11px] font-extrabold uppercase tracking-widest text-accent-light">{items[idx].role}</span>
              <p className="mt-1 text-base font-bold leading-relaxed text-white sm:text-xl" aria-live="polite">
                {words.map((w, i) => (
                  <motion.span
                    key={`${idx}-${i}`}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.15 + i * 0.045 }}
                    className="inline-block"
                  >
                    {w}{'\u00A0'}
                  </motion.span>
                ))}
              </p>
            </motion.blockquote>
          </AnimatePresence>
        </div>
        <div className="mt-4 flex items-center justify-between">
          <div className="flex gap-1.5">
            {items.map((it, i) => (
              <button key={it.role} type="button" onClick={() => setIdx(i)} aria-label={it.role} className={cn('h-1.5 rounded-full transition-all', i === idx ? 'w-6 bg-white' : 'w-1.5 bg-white/30')} />
            ))}
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => setIdx((idx - 1 + items.length) % items.length)} aria-label="이전" className="rounded-full border border-white/25 p-2 text-white"><ChevronLeft className="h-4 w-4" /></button>
            <button type="button" onClick={() => setIdx((idx + 1) % items.length)} aria-label="다음" className="rounded-full border border-white/25 p-2 text-white"><ChevronRight className="h-4 w-4" /></button>
          </div>
        </div>
      </div>
    </div>
  );
}
