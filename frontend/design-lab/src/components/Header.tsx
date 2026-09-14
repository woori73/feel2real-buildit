import { cn } from '@/lib/utils';
import { ChevronLeft } from 'lucide-react';

interface HeaderProps {
  variant: 'home-a' | 'home-b' | 'home-c';
  onBack?: () => void;
}

export function Header({ variant, onBack }: HeaderProps) {
  const variantLabels = {
    'home-a': 'TYPE A',
    'home-b': 'TYPE B',
    'home-c': 'TYPE C',
  };

  const variantNames = {
    'home-a': 'Vanilla HTML/CSS/JS',
    'home-b': 'React + Tailwind + 21st.dev',
    'home-c': 'Best Hybrid',
  };

  return (
    <header className={cn(
      'sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-border',
      'transition-all duration-200'
    )}>
      <div className="max-w-7xl mx-auto px-4 lg:px-6">
        <div className="flex items-center justify-between gap-4 h-14 lg:h-16">
          {/* Back Link */}
          <button
            onClick={onBack}
            className="flex items-center gap-2 text-sm font-semibold text-muted-foreground hover:text-foreground transition-colors px-3 py-2 rounded-lg hover:bg-muted"
            aria-label="Design Lab 인덱스로 돌아가기"
          >
            <ChevronLeft className="w-4 h-4" aria-hidden="true" />
            <span className="hidden sm:inline">Design Lab</span>
          </button>

          {/* Brand */}
          <div className="flex flex-col items-center gap-1 flex-1">
            <span className="text-xs font-extrabold uppercase tracking-wider text-accent bg-accent-soft px-2.5 py-1 rounded-full">
              {variantLabels[variant]}
            </span>
            <span className="text-xs font-medium text-muted-foreground">
              {variantNames[variant]}
            </span>
          </div>

          {/* Account Menu Placeholder */}
          <div className="w-10" aria-hidden="true" />
        </div>
      </div>
    </header>
  );
}