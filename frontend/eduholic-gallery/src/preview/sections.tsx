import { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import {
  FileText,
  Brain,
  Lightbulb,
  FileCheck,
  Award,
} from 'lucide-react';
import { MapPin, Phone, X } from 'lucide-react';
import { ACAD_API, eduholic } from '@/data/eduholic';

const tel = `tel:${eduholic.cta.phone.replace(/-/g, '')}`;

export function SiteNav() {
  return (
    <nav className="sticky top-0 z-40 border-b border-border bg-white/90 backdrop-blur" aria-label="학원 메뉴">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <a href={eduholic.livePage} target="_blank" rel="noreferrer" className="flex items-center gap-2">
          <img src={eduholic.logo} alt="압구정에듀홀릭학원 로고" className="h-8 w-8 rounded-lg object-cover" />
          <span className="text-sm font-extrabold">{eduholic.academy}</span>
        </a>
        <a href={tel} className="flex items-center gap-1.5 rounded-full bg-navy px-4 py-2 text-xs font-bold text-white">
          <Phone className="h-3.5 w-3.5" /> 상담 신청
        </a>
      </div>
    </nav>
  );
}

export function DirectorIntro() {
  return (
    <section className="bg-white px-4 py-10 sm:py-14" aria-labelledby="director-title">
      <div className="mx-auto grid max-w-5xl gap-6 sm:grid-cols-2 sm:items-center">
        <div>
          <p className="text-xs font-extrabold uppercase tracking-widest text-accent">🎓 대표 원장 소개</p>
          <h2 id="director-title" className="mt-2 text-2xl font-extrabold sm:text-3xl">
            {eduholic.director}
            <span className="mt-1 block text-sm font-semibold text-muted-foreground">{eduholic.directorTitle}</span>
          </h2>
          <p className="mt-3 border-l-4 border-accent pl-3 text-sm leading-relaxed text-muted-foreground">
            박정민 원장은 영어 교육에 전념하며 쌓아온 노하우로, 각 학생의 수준과 속도에 맞춘 효율적인 학습을 설계합니다.
          </p>
          <ul className="mt-4 space-y-1.5">
            {eduholic.credentials.map((c) => (
              <li key={c} className="flex items-start gap-2 text-xs text-muted-foreground sm:text-sm">
                <span className="mt-0.5 font-extrabold text-emerald-600">✓</span> {c}
              </li>
            ))}
          </ul>
        </div>
        <div className="overflow-hidden rounded-2xl border border-border shadow-sm">
          <span className="block overflow-hidden">
            <video
              src={eduholic.introVideo}
              poster={eduholic.introVideoPoster}
              controls
              playsInline
              preload="metadata"
              className="aspect-video w-full bg-ink"
              aria-label="원장 소개 업로드 영상"
            />
          </span>
          <span className="block bg-paper p-3 text-center text-xs font-bold">원장 소개 영상 · 위 영상을 직접 재생하세요</span>
        </div>
      </div>
    </section>
  );
}

const PHILOSOPHY = [
  { n: '원칙 01', text: '주의력이 산만한 학생도 몰입하는 1:1 마음 코칭' },
  { n: '원칙 02', text: '공식 암기가 아닌, 문장 구조를 직접 조립하는 F2R 영문법' },
  { n: '원칙 03', text: '실수를 실력으로, 지역 내신 밀착 케어' },
  { n: '원칙 04', text: '아이 성향에 맞춘 어휘 확장부터 수능 1등급 독해 체력까지' },
];

export function Philosophy() {
  return (
    <section className="bg-paper px-4 py-10 sm:py-14" aria-labelledby="phil-title">
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-extrabold uppercase tracking-widest text-accent">학습 철학</p>
        <h2 id="phil-title" className="mt-2 text-2xl font-extrabold sm:text-3xl">박정민 원장은 이렇게 가르칩니다.</h2>
        <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {PHILOSOPHY.map((p) => (
            <div key={p.n} className="rounded-2xl border border-border bg-white p-5 shadow-sm">
              <p className="text-xs font-extrabold text-accent">{p.n}</p>
              <p className="mt-2 text-sm font-bold leading-relaxed">{p.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

const AI_STEPS = [
  { n: '1', t: '학교 자료', d: '우리 학교 기출·프린트·수업 자료를 모읍니다.', icon: FileText },
  { n: '2', t: '출제자 Digital Twin', d: 'AI가 반복되는 출제 유형과 배점 패턴을 분석합니다.', icon: Brain },
  { n: '3', t: '수업 Insight', d: '오랜 현장 수업 경험과 분석 결과를 결합합니다.', icon: Lightbulb },
  { n: '4', t: '학교별 문제 제작', d: '우리 학교 시험 유형에 맞춘 문항을 만듭니다.', icon: FileCheck },
  { n: '5', t: 'FINAL', d: '실전과 같은 시험지로 시험을 대비합니다.', icon: Award },
];

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.15, delayChildren: 0.2 },
  },
};

const stepVariants = {
  hidden: { opacity: 0, y: 30 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, ease: [0.16, 1, 0.3, 1] },
  },
};

const iconVariants = {
  hidden: { scale: 0, rotate: -90 },
  visible: (i: number) => ({
    scale: 1,
    rotate: 0,
    transition: { duration: 0.5, delay: 0.3 + i * 0.1, ease: [0.34, 1.56, 0.64, 1] },
  }),
  active: { scale: 1.1, transition: { duration: 0.3 } },
};

function ConnectionLine({ isMobile, animated, delay = 0, reduceMotion }: { isMobile: boolean; animated: boolean; delay?: number; reduceMotion: boolean }) {
  const pathRef = useRef<SVGPathElement>(null);

  const d = isMobile ? 'M 0 0 L 0 60' : 'M 0 0 L 60 0';

  return (
    <svg className="connection-line" width={isMobile ? 2 : 80} height={isMobile ? 80 : 2} style={{ flex: 'none' }}>
      <motion.path
        ref={pathRef}
        d={d}
        stroke="url(#ai-gradient)"
        strokeWidth={isMobile ? 2 : 2.5}
        fill="none"
        strokeLinecap="round"
        opacity={0.6}
        initial={{ pathLength: 0 }}
        animate={{ pathLength: animated && !reduceMotion ? 1 : 0 }}
        transition={{ duration: 1.2, delay, ease: [0.16, 1, 0.3, 1] }}
      />
    </svg>
  );
}

function NeuralBrain({ className = '' }) {
  const nodes = [
    { x: 50, y: 50, r: 8, delay: 0 },
    { x: 20, y: 25, r: 5, delay: 0.1 },
    { x: 80, y: 25, r: 5, delay: 0.2 },
    { x: 15, y: 65, r: 4, delay: 0.3 },
    { x: 50, y: 15, r: 4, delay: 0.15 },
    { x: 85, y: 60, r: 4, delay: 0.25 },
    { x: 30, y: 80, r: 3, delay: 0.35 },
    { x: 70, y: 75, r: 3, delay: 0.4 },
    { x: 50, y: 85, r: 3, delay: 0.3 },
  ];

  const connections = [
    [0, 1], [0, 2], [0, 4], [0, 5], [0, 8],
    [1, 3], [1, 4], [2, 5], [2, 6], [3, 7], [5, 7], [4, 8],
  ];

  return (
    <svg className={className} viewBox="0 0 100 100" width="100" height="100" aria-hidden="true">
      <defs>
        <radialGradient id="brain-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#fbbf24" stopOpacity={0.4} />
          <stop offset="70%" stopColor="#f59e0b" stopOpacity={0.1} />
          <stop offset="100%" stopColor="#f59e0b" stopOpacity={0} />
        </radialGradient>
      </defs>
      <g className="neural-connections" filter="url(#glow-blur)">
        {connections.map(([a, b], i) => (
          <motion.line
            key={i}
            x1={nodes[a].x}
            y1={nodes[a].y}
            x2={nodes[b].x}
            y2={nodes[b].y}
            stroke="#fbbf24"
            strokeWidth="0.8"
            strokeOpacity={0.3}
            initial={{ pathLength: 0 }}
            animate={{ pathLength: 1 }}
            transition={{ duration: 1.5, delay: 0.5 + i * 0.05, ease: 'easeInOut', repeat: Infinity, repeatType: 'reverse' }}
          />
        ))}
      </g>
      {nodes.map((node, i) => (
        <motion.circle
          key={i}
          cx={node.x}
          cy={node.y}
          r={node.r}
          fill="#fbbf24"
          initial={{ scale: 0 }}
          animate={{
            scale: [1, 1.3, 1],
            opacity: [0.6, 1, 0.6],
          }}
          transition={{
            duration: 2,
            delay: node.delay,
            ease: 'easeInOut',
            repeat: Infinity,
          }}
        />
      ))}
    </svg>
  );
}

function StepCard({
  step,
  index,
  isMobile,
  isLast,
  animated,
  reduceMotion,
}: {
  step: (typeof AI_STEPS)[0];
  index: number;
  isMobile: boolean;
  isLast: boolean;
  animated: boolean;
  reduceMotion: boolean;
}) {
  const Icon = step.icon;
  const isTwin = index === 1;
  const isFinal = index === 4;

  return (
    <motion.li
      className="relative"
      variants={stepVariants}
      custom={index}
      style={{ flex: isMobile ? 'none' : '1 1 0', minWidth: isMobile ? '100%' : 'auto' }}
    >
      {!isMobile && !isLast && <ConnectionLine isMobile={false} animated={animated} delay={index * 0.15} reduceMotion={reduceMotion} />}
      <div className={`relative flex flex-col items-center ${isMobile ? 'w-full' : ''}`}>
        <motion.div
          className="relative flex items-center justify-center w-16 h-16 rounded-2xl bg-white/10 backdrop-blur border border-white/20"
          variants={iconVariants}
          animate={animated ? 'visible' : 'hidden'}
          custom={index}
          whileHover={isTwin ? undefined : { scale: 1.05 }}
          transition={reduceMotion ? { duration: 0 } : undefined}
        >
          {isTwin ? (
            <NeuralBrain className="w-12 h-12" />
          ) : (
            <Icon className="w-6 h-6 text-accent" aria-hidden="true" />
          )}
          {isFinal && animated && (
            <motion.div
              className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-emerald-500"
              initial={reduceMotion ? { scale: 1, rotate: 0 } : { scale: 0, rotate: -180 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ delay: 1.5, type: 'spring', stiffness: 200, damping: 15 }}
            >
              <Award className="w-3 h-3 text-white absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2" />
            </motion.div>
          )}
        </motion.div>
        <motion.p
          className="mt-3 text-center text-sm font-extrabold text-white"
          style={{ fontSize: isMobile ? '14px' : '13px' }}
          initial={{ opacity: 0, y: 10 }}
          animate={animated ? { opacity: 1, y: 0 } : { opacity: 0, y: 10 }}
          transition={{ duration: reduceMotion ? 0 : 0.4, delay: 0.4 + index * 0.1 }}
        >
          {step.t}
        </motion.p>
        <motion.p
          className="mt-2 text-center text-xs leading-relaxed text-white/65 px-2"
          style={{ fontSize: isMobile ? '12px' : '11px', maxWidth: isMobile ? 'none' : '160px' }}
          initial={{ opacity: 0, y: 10 }}
          animate={animated ? { opacity: 1, y: 0 } : { opacity: 0, y: 10 }}
          transition={{ duration: reduceMotion ? 0 : 0.4, delay: 0.5 + index * 0.1 }}
        >
          {step.d}
        </motion.p>
        {isMobile && index < 4 && (
          <ConnectionLine isMobile={true} animated={animated} delay={(index + 0.5) * 0.15} reduceMotion={reduceMotion} />
        )}
      </div>
    </motion.li>
  );
}

export function AiSteps() {
  const [animated, setAnimated] = useState(false);
  const [reduceMotion, setReduceMotion] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReduceMotion(mediaQuery.matches);
    const handler = (e: MediaQueryListEvent) => setReduceMotion(e.matches);
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setAnimated(true);
          observer.disconnect();
        }
      },
      { threshold: 0.3, rootMargin: '-50px' }
    );
    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

  return (
    <section
      ref={sectionRef}
      className="bg-ink px-4 py-10 text-white sm:py-14"
      aria-labelledby="ai-title"
    >
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-extrabold uppercase tracking-widest text-accent-light">AI NATIVE 내신</p>
        <h2 id="ai-title" className="mt-2 text-2xl font-extrabold sm:text-3xl">박정민 원장 X AI, 최적의 학교 내신 대비</h2>
        <p className="mt-2 max-w-2xl text-sm text-white/70">시험 직전에 나오는 학교 선생님의 자료까지 AI에 즉시 학습시켜 최고의 적중률을 만드는 wiki내신 시스템</p>

        <svg className="sr-only" aria-hidden="true">
          <defs>
            <linearGradient id="ai-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#fbbf24" />
              <stop offset="50%" stopColor="#f59e0b" />
              <stop offset="100%" stopColor="#fbbf24" />
            </linearGradient>
            <radialGradient id="ai-glow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#fde047" stopOpacity={1} />
              <stop offset="100%" stopColor="#f59e0b" stopOpacity={0} />
            </radialGradient>
            <filter id="glow-blur">
              <feGaussianBlur stdDeviation="1" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>
        </svg>

        <motion.ol
          className={`relative ${isMobile ? 'flex flex-col gap-4' : 'flex items-center gap-0'}`}
          variants={containerVariants}
          initial="hidden"
          animate={animated ? 'visible' : 'hidden'}
          transition={reduceMotion ? { staggerChildren: 0, delayChildren: 0 } : undefined}
          role="list"
          aria-label="AI 내신 5단계 프로세스"
        >
          {AI_STEPS.map((step, i) => (
            <StepCard
              key={step.n}
              step={step}
              index={i}
              isMobile={isMobile}
              isLast={i === 4}
              animated={animated}
              reduceMotion={reduceMotion}
            />
          ))}
        </motion.ol>
      </div>
    </section>
  );
}

export function ClassVideos() {
  return (
    <section className="bg-white px-4 py-10 sm:py-14" aria-labelledby="cv-title">
      <div className="mx-auto max-w-5xl">
        <h2 id="cv-title" className="text-2xl font-extrabold sm:text-3xl">에듀홀릭의 실제 수업 현장</h2>
        <div className="no-scrollbar mt-6 flex gap-3 overflow-x-auto pb-1 sm:grid sm:grid-cols-4 sm:overflow-visible">
          {eduholic.classVideos.map((v) => (
            <a key={v.id} href={`${ACAD_API}/videos/${v.id}/stream`} target="_blank" rel="noreferrer" className="group w-56 shrink-0 overflow-hidden rounded-2xl border border-border sm:w-auto">
              <span className="relative block overflow-hidden">
                <img src={v.thumb} alt={v.title} loading="lazy" className="aspect-video w-full object-cover transition group-hover:scale-[1.03]" />
                <span className="absolute bottom-2 right-2 rounded-md bg-black/70 px-2 py-0.5 text-[11px] font-bold text-white">▶ {v.duration}</span>
              </span>
              <span className="block p-3 text-xs font-bold">{v.title}</span>
            </a>
          ))}
        </div>
      </div>
    </section>
  );
}

export function Curriculum() {
  return (
    <section className="bg-paper px-4 py-10 sm:py-14" aria-labelledby="cu-title">
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-extrabold uppercase tracking-widest text-accent">대표 수업</p>
        <h2 id="cu-title" className="mt-2 text-2xl font-extrabold sm:text-3xl">압구정에듀홀릭 커리큘럼</h2>
        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          {eduholic.curriculum.map((c) => (
            <article key={c.name} className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
              <img src={c.img} alt={`${c.name} 대표 이미지`} loading="lazy" className="aspect-[16/9] w-full object-cover" />
              <div className="p-5">
              <p className="text-xs font-extrabold text-accent">{c.name}</p>
              <h3 className="mt-1 text-base font-extrabold">{c.sub}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{c.desc}</p>
              <p className="mt-1 text-xs font-semibold text-muted-foreground">{c.target}</p>
              <a href={tel} className="mt-4 block rounded-xl bg-navy px-4 py-2.5 text-center text-sm font-bold text-white">상담 신청하기</a>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

const MAP_URL = 'https://map.naver.com/p/entry/place/1422644365?searchType=place&lng=127.0309965&lat=37.5274022';

export function VisitFooter() {
  return (
    <footer className="bg-white px-4 py-10" aria-label="방문 안내 및 학원 정보">
      <div className="mx-auto max-w-5xl">
        <h2 className="text-xl font-extrabold sm:text-2xl">최신 소식 & 방문 안내</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <a href={eduholic.cta.blog} target="_blank" rel="noreferrer" className="flex items-center gap-3 rounded-2xl border border-border p-4">
            <span className="text-2xl">📷</span>
            <span><span className="block text-sm font-extrabold">네이버 블로그</span><span className="block text-xs text-muted-foreground">학원 공식 블로그</span></span>
          </a>
          <a href={MAP_URL} target="_blank" rel="noreferrer" className="flex items-center gap-3 rounded-2xl border border-border p-4">
            <span className="text-2xl">📍</span>
            <span><span className="block text-sm font-extrabold">네이버 지도</span><span className="block text-xs text-muted-foreground">방문 안내 · 상담 예약</span></span>
          </a>
        </div>
        <div className="mt-8 border-t border-border pt-6 text-center">
          <p className="flex items-center justify-center gap-2">
            <img src={eduholic.logo} alt="압구정에듀홀릭학원 로고" className="h-7 w-7 rounded-lg object-cover" />
            <span className="text-sm font-extrabold">{eduholic.academy}</span>
          </p>
          <p className="mt-1 flex items-center justify-center gap-1 text-xs text-muted-foreground">
            <MapPin className="h-3.5 w-3.5" /> {eduholic.cta.address} · {eduholic.cta.phone}
          </p>
          <p className="mt-2 text-[11px] text-muted-foreground">상호명: 압구정에듀홀릭학원 · 대표: 박정민 · 사업자등록번호: 759-91-01977</p>
          <p className="mt-1 text-[11px] text-muted-foreground">© 2026 압구정에듀홀릭학원. Powered by F2R.</p>
        </div>
      </div>
    </footer>
  );
}

/** Midterm campaign popup ? same content/flow as live #eduholicMidtermPopup. */
export function MidtermPopup() {
  const [open, setOpen] = useState(true);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 p-4 sm:items-center" role="dialog" aria-modal="true" aria-label="중간고사 내신 LIVE 안내">
      <div className="relative w-full max-w-sm overflow-hidden rounded-2xl bg-white shadow-2xl">
        <button type="button" onClick={() => setOpen(false)} aria-label="닫기" className="absolute right-3 top-3 z-10 rounded-full bg-black/50 p-1.5 text-white"><X className="h-4 w-4" /></button>
        <img src={eduholic.popupVisual} alt="2026 2학기 중간고사 내신 LIVE" className="aspect-[4/3] w-full object-cover" />
        <div className="p-5">
          <p className="text-[11px] font-extrabold uppercase tracking-widest text-accent">{eduholic.popup.kicker}</p>
          <p className="mt-1 text-lg font-extrabold">{eduholic.popup.title}</p>
          <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{eduholic.popup.desc}</p>
          <a href={eduholic.livePage} target="_blank" rel="noreferrer" className="mt-4 block rounded-xl bg-navy px-4 py-3 text-center text-sm font-bold text-white">{eduholic.popup.primary}</a>
          <button type="button" onClick={() => setOpen(false)} className="mt-2 w-full py-1 text-center text-xs text-muted-foreground">{eduholic.popup.dismiss}</button>
        </div>
      </div>
    </div>
  );
}

