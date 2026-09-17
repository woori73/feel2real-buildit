import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function ZoneFrame({
  id,
  no,
  title,
  keep,
  children,
}: {
  id: string;
  no: string;
  title: string;
  keep: string[];
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-20 border-t-4 border-ink bg-paper py-10" aria-label={title}>
      <div className="mx-auto max-w-5xl px-4">
        <p className="text-xs font-extrabold uppercase tracking-widest text-accent">{no}</p>
        <h2 className="mt-1 text-2xl font-extrabold sm:text-3xl">{title}</h2>
        <div className="mt-3 flex flex-wrap gap-1.5" aria-label="보존 목록">
          {keep.map((k) => (
            <span key={k} className="rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-bold text-emerald-700 ring-1 ring-emerald-200">
              ✓ {k}
            </span>
          ))}
        </div>
        <div className="mt-6 space-y-8">{children}</div>
      </div>
    </section>
  );
}

export function Variant({
  code,
  title,
  note,
  children,
  dark,
  selected,
}: {
  code: 'CURRENT' | 'OPTION A' | 'OPTION B';
  title: string;
  note: string;
  children: ReactNode;
  dark?: boolean;
  selected?: boolean;
}) {
  return (
    <article className={cn('overflow-hidden rounded-2xl border bg-white shadow-sm', selected ? 'border-emerald-500 ring-2 ring-emerald-200' : 'border-border')}>
      <div className={cn('flex flex-wrap items-center gap-2 border-b border-border px-4 py-3', dark ? 'bg-ink text-white' : 'bg-paper')}>
        <span className={cn(
          'rounded-full px-2.5 py-1 text-xs font-extrabold',
          code === 'CURRENT' ? 'bg-emerald-600 text-white' : 'bg-navy text-white',
        )}>
          {code}
        </span>
        {selected && (
          <span className="rounded-full bg-emerald-600 px-2.5 py-1 text-xs font-extrabold text-white">✓ 채택</span>
        )}
        <h3 className="text-base font-bold">{title}</h3>
      </div>
      <p className="border-b border-border bg-white px-4 py-2 text-xs text-muted-foreground">{note}</p>
      <div>{children}</div>
    </article>
  );
}
