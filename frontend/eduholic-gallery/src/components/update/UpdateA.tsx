import { useRef } from 'react';
import { motion, useAnimationFrame, useMotionValue, useScroll, useSpring, useTransform, useVelocity, wrap } from 'framer-motion';
import { eduholic } from '@/data/eduholic';

/**
 * TEMP — spec implementation, NOT 21st real code (awaiting retrieval).
 * Spec: @dillionverma/scroll-based-velocity (Magic UI, 21st.dev, MIT).
 * 스크롤 속도에 반응하는 업데이트 스트립.
 */
export function UpdateA() {
  const baseX = useMotionValue(0);
  const { scrollY } = useScroll();
  const scrollVelocity = useVelocity(scrollY);
  const smoothVelocity = useSpring(scrollVelocity, { damping: 50, stiffness: 400 });
  const velocityFactor = useTransform(smoothVelocity, [0, 1000], [0, 4.5], { clamp: false });
  const direction = useRef<number>(1);

  const x = useTransform(baseX, (v) => `${wrap(-25, 0, v)}%`);

  useAnimationFrame((_, delta) => {
    let moveBy = direction.current * 1.1 * (delta / 1000);
    const vf = velocityFactor.get();
    if (vf < 0) direction.current = -1;
    else if (vf > 0) direction.current = 1;
    moveBy += direction.current * moveBy * Math.min(Math.abs(vf), 5);
    baseX.set(baseX.get() + moveBy);
  });

  const row = [...eduholic.updates, ...eduholic.updates];

  return (
    <div className="overflow-hidden border-y border-border bg-navy py-4">
      <motion.div style={{ x }} className="flex w-max items-center gap-6 whitespace-nowrap pr-6">
        {[0, 1, 2, 3].map((copy) => (
          <div key={copy} className="flex items-center gap-6" aria-hidden={copy > 0}>
            {row.map((u, i) => (
              <span key={`${copy}-${i}`} className="flex items-center gap-2 text-sm font-semibold text-white">
                <span className="rounded-full bg-white/15 px-2.5 py-0.5 text-[11px] font-extrabold text-accent-light">{u.kicker}</span>
                {u.text}
                <span className="ml-4 text-white/30">●</span>
              </span>
            ))}
          </div>
        ))}
      </motion.div>
      <p className="mt-2 text-center text-[11px] text-white/50">위·아래로 스크롤해 보세요 — 속도가 스크롤에 반응합니다</p>
    </div>
  );
}
