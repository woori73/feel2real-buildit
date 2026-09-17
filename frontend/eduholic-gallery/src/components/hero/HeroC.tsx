import { useRef } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import { eduholic } from '@/data/eduholic';

/**
 * HERO C — Scroll-expansion media hero.
 * Spec: @arunachalam/scroll-expansion-hero (21st.dev, MIT).
 * 작은 라운드 미디어가 스크롤에 따라 풀블리드로 확장.
 */
export function HeroC() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'center center'] });
  const scale = useTransform(scrollYProgress, [0, 1], [0.72, 1]);
  const radius = useTransform(scrollYProgress, [0, 1], [32, 0]);
  const captionY = useTransform(scrollYProgress, [0.4, 1], [40, 0]);
  const captionOpacity = useTransform(scrollYProgress, [0.4, 1], [0, 1]);

  return (
    <div ref={ref} className="relative bg-paper px-4 py-10 sm:py-14">
      <div className="mx-auto max-w-2xl text-center">
        <p className="text-xs font-extrabold uppercase tracking-widest text-accent">2학기 영어 내신 LIVE</p>
        <h2 className="mt-2 text-2xl font-extrabold sm:text-4xl">학교별 실제 자료를 읽고,<br />학교에 맞는 예상문제를 만듭니다.</h2>
      </div>
      <motion.div style={{ scale }} className="mx-auto mt-6 max-w-5xl">
        <motion.video
          style={{ borderRadius: radius }}
          className="aspect-video w-full object-cover shadow-xl"
          src={eduholic.heroVideo}
          poster={eduholic.principalPhoto}
          muted
          loop
          playsInline
          autoPlay
        />
      </motion.div>
      <motion.div style={{ y: captionY, opacity: captionOpacity }} className="mx-auto mt-4 flex max-w-5xl flex-col gap-2 rounded-2xl border border-border bg-white p-4 sm:flex-row sm:items-center">
        <div className="flex-1">
          <p className="text-sm font-bold">{eduholic.liveFeature.title}</p>
          <p className="mt-1 text-xs leading-relaxed text-muted-foreground sm:text-sm">{eduholic.liveFeature.desc}</p>
        </div>
        <a href={eduholic.livePage} target="_blank" rel="noreferrer" className="shrink-0 rounded-xl bg-navy px-5 py-2.5 text-center text-sm font-bold text-white">
          중간고사 대비 현장 보기 →
        </a>
      </motion.div>
    </div>
  );
}
