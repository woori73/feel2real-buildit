/**
 * Eduholic integrated preview — MASTER + 2 approved OPTIONs only.
 * MASTER (HERO HOLD — untouched): SiteNav, HeroCurrent (faithful hero:
 *   principal photo/video, copy, counsel/class links, stats), DirectorIntro,
 *   AiSteps, ClassVideos, Curriculum, VisitFooter, MidtermPopup.
 * [2] 내신 LIVE → LiveOptionB (21st real code: @ruixen.ui/coverflow-carousel
 *   id 23997, MIT; typographic cards per yesterday's approval, real school
 *   data + Wiki/analysis links synced below the drag surface).
 * [3] 학습 철학 → PhilOptionB (TEMP step implementation; 21st real code not
 *   yet retrieved — quota 0/2, no paid usage. TODO: replace with real 21st
 *   stepper component when free quota allows).
 * No other replacements. Production untouched.
 */
import { HeroCurrent } from '@/zones/ZoneHero';
import { LiveOptionB } from '@/zones/ZoneLive';
import { PhilOptionB } from '@/zones/ZonePhil';
import { AiSteps, ClassVideos, Curriculum, DirectorIntro, MidtermPopup, SiteNav, VisitFooter } from './sections';

export function PreviewApp() {
  return (
    <div className="min-h-screen bg-paper text-ink">
      <MidtermPopup />
      <SiteNav />
      <main>
        <HeroCurrent />
        <DirectorIntro />
        <LiveOptionB />
        <PhilOptionB />
        <AiSteps />
        <ClassVideos />
        <Curriculum />
      </main>
      <VisitFooter />
    </div>
  );
}
