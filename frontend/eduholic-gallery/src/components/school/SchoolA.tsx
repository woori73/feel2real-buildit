import { useEffect, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { LIVE, eduholic } from '@/data/eduholic';
import { VideoThumb } from '@/components/VideoThumb';

/**
 * SCHOOL A — Squeeze accordion.
 * Spec: @yura/carousel-squeeze (21st.dev, MIT).
 * PC: 가로 어코디언(선택 패널 확장) / 모바일: 세로 아코디언. 자동재생+탭 확장.
 */
export function SchoolA() {
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const schools = eduholic.schools;

  useEffect(() => {
    if (paused) return;
    const t = setInterval(() => setActive((i) => (i + 1) % schools.length), 5000);
    return () => clearInterval(t);
  }, [paused, schools.length]);

  return (
    <div
      className="bg-white px-4 py-8"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onTouchStart={() => setPaused(true)}
    >
      <p className="text-center text-xs font-extrabold uppercase tracking-widest text-accent">학교별 내신 대비</p>
      {/* 모바일: 세로 아코디언 */}
      <div className="mx-auto mt-4 max-w-xl sm:hidden">
        {schools.map((s, i) => {
          const open = i === active;
          return (
            <div key={s.name} className={cn('mb-2 overflow-hidden rounded-2xl border transition', open ? 'border-navy shadow-lg' : 'border-border')}>
              <button type="button" onClick={() => setActive(i)} className="flex w-full items-center gap-3 p-3 text-left" aria-expanded={open}>
                <VideoThumb seek={1 + i * 4} label={`${s.name} 썸네일`} className="h-12 w-12 rounded-xl" />
                <span className="flex-1">
                  <span className="block text-sm font-extrabold">{s.name} · {s.grade}</span>
                  <span className="block text-xs text-muted-foreground">{s.scope}</span>
                </span>
                <span className={cn('text-lg transition', open && 'rotate-90 text-accent')}>›</span>
              </button>
              <div className={cn('grid transition-all', open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0')}>
                <div className="overflow-hidden">
                  <VideoThumb seek={1 + i * 4} label={`${s.name} 내신 자료`} className="aspect-[16/9] w-full" />
                  <div className="p-4">
                    <p className="text-sm font-bold">{s.point}</p>
                    <div className="mt-3 flex gap-2">
                      <a href={s.wiki} target="_blank" rel="noreferrer" className="flex-1 rounded-xl bg-navy px-3 py-2 text-center text-xs font-bold text-white">시험범위 Wiki →</a>
                      <a href={`${LIVE}${s.analysis}`} target="_blank" rel="noreferrer" className="flex flex-1 items-center justify-center gap-1 rounded-xl border border-border px-3 py-2 text-xs font-bold">출제 분석 <ArrowRight className="h-3 w-3" /></a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
      {/* PC: 가로 스퀴즈 */}
      <div className="mx-auto mt-4 hidden max-w-5xl gap-2 sm:flex">
        {schools.map((s, i) => {
          const open = i === active;
          return (
            <button
              key={s.name}
              type="button"
              onClick={() => setActive(i)}
              aria-expanded={open}
              className={cn(
                'relative min-h-[380px] overflow-hidden rounded-2xl text-left transition-all duration-500',
                open ? 'flex-[3.2]' : 'flex-[1] hover:flex-[1.4]',
              )}
            >
              <VideoThumb seek={1 + i * 4} label={`${s.name} 배경`} className="absolute inset-0 h-full w-full" />
              <span className={cn('absolute inset-0', open ? 'bg-gradient-to-t from-black/80 via-black/25 to-transparent' : 'bg-black/45')} />              {!open && (
                <span className="absolute inset-0 flex items-center justify-center">
                  <span className="rotate-90 whitespace-nowrap text-sm font-extrabold tracking-widest text-white sm:[writing-mode:vertical-rl] sm:rotate-0">{s.name}</span>
                </span>
              )}
              {open && (
                <span className="absolute inset-x-0 bottom-0 block p-5">
                  <span className="block text-lg font-extrabold text-white">{s.name} · {s.grade}</span>
                  <span className="mt-1 block text-sm text-white/85">{s.scope} · {s.point}</span>
                  <span className="mt-3 flex gap-2">
                    <a href={s.wiki} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()} className="rounded-xl bg-white px-4 py-2 text-xs font-bold text-ink">시험범위 Wiki →</a>
                    <a href={`${LIVE}${s.analysis}`} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()} className="rounded-xl border border-white/40 px-4 py-2 text-xs font-bold text-white">출제 분석 →</a>
                  </span>
                </span>
              )}
            </button>
          );
        })}
      </div>
      <div className="mt-3 flex justify-center gap-1.5">
        {schools.map((s, i) => (
          <button key={s.name} type="button" onClick={() => setActive(i)} aria-label={`${s.name} 보기`} className={cn('h-1.5 rounded-full transition-all', i === active ? 'w-6 bg-navy' : 'w-1.5 bg-border')} />
        ))}
      </div>
    </div>
  );
}
