import { useState, type ReactNode } from 'react';
import { Check, ExternalLink } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface PickState {
  hero?: string;
  school?: string;
  update?: string;
  proof?: string;
  cta?: string;
}

interface Props {
  id: string;
  area: keyof PickState;
  code: string;
  title: string;
  component: string;
  source: string;
  sourceUrl: string;
  interaction: string;
  mobile: string;
  license: string;
  picks: PickState;
  onPick: (area: keyof PickState, code: string) => void;
  children: ReactNode;
}

export function CandidateFrame(p: Props) {
  const [open, setOpen] = useState(true);
  const selected = p.picks[p.area] === p.code;
  return (
    <article id={p.id} className="scroll-mt-32 overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex flex-wrap items-center gap-2 border-b border-border bg-paper px-4 py-3">
        <span className="rounded-full bg-navy px-2.5 py-1 text-xs font-extrabold text-white">{p.code}</span>
        <h3 className="text-base font-bold">{p.title}</h3>
        <div className="ml-auto flex gap-2">
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="rounded-lg border border-border px-3 py-1.5 text-xs font-semibold text-muted-foreground"
          >
            {open ? '데모 접기' : '데모 펼치기'}
          </button>
          <button
            type="button"
            onClick={() => p.onPick(p.area, p.code)}
            className={cn(
              'flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-bold',
              selected ? 'bg-emerald-600 text-white' : 'bg-navy text-white',
            )}
          >
            {selected && <Check className="h-3.5 w-3.5" />}
            {selected ? '선택됨' : '이 후보 선택'}
          </button>
        </div>
      </div>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-1 px-4 py-3 text-xs sm:grid-cols-2">
        <div className="flex gap-2"><dt className="shrink-0 font-bold text-muted-foreground">21st component</dt><dd className="font-semibold">{p.component}</dd></div>
        <div className="flex gap-2"><dt className="shrink-0 font-bold text-muted-foreground">원본</dt><dd><a href={p.sourceUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-semibold text-accent">{p.source}<ExternalLink className="h-3 w-3" /></a></dd></div>
        <div className="flex gap-2"><dt className="shrink-0 font-bold text-muted-foreground">Interaction</dt><dd>{p.interaction}</dd></div>
        <div className="flex gap-2"><dt className="shrink-0 font-bold text-muted-foreground">모바일</dt><dd>{p.mobile}</dd></div>
        <div className="flex gap-2 sm:col-span-2"><dt className="shrink-0 font-bold text-muted-foreground">License</dt><dd>{p.license}</dd></div>
      </dl>
      {open && <div className="border-t border-border">{p.children}</div>}
    </article>
  );
}
