import { useEffect, useRef, useState } from 'react';
import { animate, motion, useInView, useScroll, useTransform } from 'framer-motion';
import { Volume2, VolumeX } from 'lucide-react';
import { eduholic } from '@/data/eduholic';

const tel = 'tel:0263241453';
// Link mapping (documented): live 상담 신청하기 opens the consult modal and
// 대표 수업 보기 scrolls to the class section. In this isolated zone both
// point at the real counsel channel (tel:) and the real LIVE page.
const HERO_LINKS = { counsel: tel, classes: eduholic.livePage };

function HeroMedia({ motionStyle }: { motionStyle?: React.CSSProperties }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [muted, setMuted] = useState(true);
  return (
    <div style={motionStyle} className="mx-auto w-full max-w-[340px]">
      <div className="overflow-hidden rounded-[2rem] bg-ink p-2 shadow-2xl">
        <div className="relative overflow-hidden rounded-[1.6rem]">
          <img
            src={eduholic.principalPhoto}
            alt="압구정에듀홀릭학원 원장 사진"
            className="aspect-[3/4] w-full object-cover"
          />
          <video
            ref={videoRef}
            src={eduholic.heroVideo}
            poster={eduholic.principalPhoto}
            muted={muted}
            loop
            playsInline
            autoPlay
            preload="metadata"
            className="absolute inset-0 h-full w-full object-cover"
            aria-label="박정민 원장 소개 영상"
          />
          <button
            type="button"
            onClick={() => {
              const v = videoRef.current;
              if (!v) return;
              v.muted = !v.muted;
              setMuted(v.muted);
            }}
            aria-label={muted ? '소리 켜기' : '소리 끄기'}
            className="absolute bottom-3 right-3 rounded-full bg-black/60 p-2.5 text-white"
          >
            {muted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
          </button>
        </div>
      </div>
      <p className="mt-2 text-center text-xs font-semibold text-muted-foreground">박정민 원장 · {eduholic.academy}</p>
    </div>
  );
}

function HeroCopy({ hideStats = false }: { hideStats?: boolean }) {
  return (
    <div>
      <p className="flex items-center gap-2 text-xs font-bold text-muted-foreground">
        <span className="inline-block h-px w-6 bg-accent" aria-hidden /> 박정민 원장
      </p>
      <h2 className="mt-3 text-[28px] font-extrabold leading-tight sm:text-[46px]">{eduholic.academy}</h2>
      <p className="mt-4 border-l-2 border-amber-400 pl-3 text-sm leading-relaxed text-muted-foreground sm:text-base">
        {eduholic.heroQuote}
      </p>
      <div className="mt-6 flex flex-col gap-2 sm:flex-row">
        <a href={HERO_LINKS.counsel} className="rounded-full bg-ink px-7 py-3 text-center text-sm font-bold text-white">
          상담 신청하기
        </a>
        <a href={HERO_LINKS.classes} target="_blank" rel="noreferrer" className="rounded-full border border-border px-7 py-3 text-center text-sm font-bold">
          대표 수업 보기
        </a>
      </div>
      {!hideStats && (
      <dl className="mt-8 flex gap-6 sm:gap-8">
        {[
          { v: '35–58만원', l: '월 수강료' },
          { v: '87%', l: '성적 향상률' },
          { v: '20년', l: '운영 경력' },
        ].map((s) => (
          <div key={s.l}>
            <dt className="sr-only">{s.l}</dt>
            <dd className="text-lg font-extrabold text-navy sm:text-xl">{s.v}</dd>
            <dd className="mt-0.5 text-[11px] text-muted-foreground sm:text-xs">{s.l}</dd>
          </div>
        ))}
      </dl>
      )}
    </div>
  );
}

export function HeroCurrent() {
  return (
    <div className="bg-white px-4 py-10 sm:py-14">
      <div className="mx-auto grid max-w-5xl gap-8 sm:grid-cols-2 sm:items-center">
        <div className="order-2 sm:order-1"><HeroCopy /></div>
        <div className="order-1 sm:order-2"><HeroMedia /></div>
      </div>
    </div>
  );
}

export function HeroOptionA() {
  return (
    <div className="bg-white px-4 py-10 sm:py-14">
      <div className="mx-auto grid max-w-5xl gap-8 sm:grid-cols-2 sm:items-center">
        <div className="order-2 sm:order-1">
          <motion.div initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }} transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}>
            <HeroCopy />
          </motion.div>
        </div>
        <div className="order-1 sm:order-2">
          <motion.div initial={{ opacity: 0, scale: 0.96, y: 16 }} whileInView={{ opacity: 1, scale: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }} transition={{ duration: 0.65, ease: [0.16, 1, 0.3, 1] }}>
            <HeroMedia />
            <motion.p
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.5 }}
              className="mx-auto mt-3 w-fit rounded-full bg-amber-100 px-3 py-1 text-[11px] font-extrabold text-amber-800"
            >
              20년 운영 · 지역 내신 밀착
            </motion.p>
          </motion.div>
        </div>
      </div>
    </div>
  );
}

function CountStat({ value, prefix = '', suffix = '', label }: { value: number; prefix?: string; suffix?: string; label: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: '-40px' });
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!inView) return;
    const c = animate(0, value, { duration: 1.6, ease: [0.16, 1, 0.3, 1], onUpdate: (v) => setN(v) });
    return () => c.stop();
  }, [inView, value]);
  return (
    <div>
      <dd className="text-lg font-extrabold tabular-nums text-navy sm:text-xl">
        <span ref={ref}>{prefix}{Math.round(n)}{suffix}</span>
      </dd>
      <dd className="mt-0.5 text-[11px] text-muted-foreground sm:text-xs">{label}</dd>
    </div>
  );
}

export function HeroOptionB() {
  const { scrollY } = useScroll();
  const mediaY = useTransform(scrollY, [0, 600], [0, 46]);
  const copyY = useTransform(scrollY, [0, 600], [0, -30]);
  return (
    <div className="overflow-hidden bg-white px-4 py-10 sm:py-14">
      <div className="mx-auto grid max-w-5xl gap-8 sm:grid-cols-2 sm:items-center">
        <motion.div style={{ y: copyY }} className="order-2 sm:order-1">
          <HeroCopy hideStats />
          <dl className="mt-8 flex gap-6 sm:gap-8">
            <CountStat value={58} prefix="35–" suffix="만원" label="월 수강료" />
            <CountStat value={87} suffix="%" label="성적 향상률" />
            <CountStat value={20} suffix="년" label="운영 경력" />
          </dl>
        </motion.div>
        <motion.div style={{ y: mediaY }} className="order-1 sm:order-2">
          <HeroMedia />
        </motion.div>
      </div>
    </div>
  );
}
