import { useState } from 'react';
import { cn } from '@/lib/utils';
import { eduholic } from '@/data/eduholic';

/**
 * UPDATE B — Live ticker.
 * Spec: marquee 패턴 (21st.dev marquee 카테고리 — tom_ui/marquee 계열, CSS 기반).
 * 호버/터치홀드에서 멈추고, 탭하면 LIVE 페이지로.
 */
export function UpdateB() {
  const [held, setHeld] = useState(false);
  const items = [...eduholic.updates, ...eduholic.curriculum.map((c) => ({ kicker: '수업', text: `${c.name} — ${c.sub}` }))];
  const loop = [...items, ...items];

  return (
    <div className="border-y border-border bg-white py-4">
      <div
        className="overflow-hidden"
        onMouseEnter={() => setHeld(true)}
        onMouseLeave={() => setHeld(false)}
        onTouchStart={() => setHeld(true)}
        onTouchEnd={() => setHeld(false)}
      >
        <div className={cn('flex w-max gap-3 pr-3 animate-gallery-marquee')} style={held ? { animationPlayState: 'paused' } : undefined}>
          {loop.map((u, i) => (
            <a
              key={i}
              href={eduholic.livePage}
              target="_blank"
              rel="noreferrer"
              aria-hidden={i >= items.length}
              tabIndex={i >= items.length ? -1 : 0}
              className="flex shrink-0 items-center gap-2 rounded-full border border-border bg-paper px-4 py-2 text-xs font-semibold"
            >
              <span className="flex items-center gap-1.5">
                <span className="relative flex h-2 w-2">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                  <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
                </span>
                <span className="font-extrabold text-navy">{u.kicker}</span>
              </span>
              {u.text}
            </a>
          ))}
        </div>
      </div>
      <p className="mt-2 text-center text-[11px] text-muted-foreground">{held ? '멈춤 — 탭하면 LIVE 페이지로 이동' : '손가락을 올리면 멈춥니다 · 탭하면 이동'}</p>
    </div>
  );
}
