import { Header } from '@/components/Header';
import { VideoHero } from '@/components/VideoHero';
import { SchoolCard } from '@/components/SchoolCard';
import { CTASection } from '@/components/CTASection';

function App() {
  return (
    <div className="min-h-screen bg-background text-foreground font-sans antialiased">
      <Header variant="home-b" onBack={() => window.location.href = '/design-lab'} />

      <main className="max-w-7xl mx-auto px-4 lg:px-6 py-8 lg:py-12">
        {/* HERO with VIDEO */}
        <VideoHero
          subtitle="학생의 실제 학교 학습자료와 오답 데이터를 기반으로 AI 분석과 전문가 피드백을 결합하여 학교별 맞춤 내신 영어 학습자료를 제공하는 교육 AX 서비스입니다."
          primaryCta={{
            label: '서비스 이용하기',
            onClick: () => alert('서비스 이용하기 클릭됨'),
          }}
          secondaryCta={{
            label: '샘플 리포트 보기',
            onClick: () => alert('샘플 리포트 보기 클릭됨'),
          }}
          videoUrl="https://www.youtube.com/embed/cLieSj8YxYA?si=5csXW-ahg_yXhILZ"
          videoTitle="F2R AI 기반 내신 영어 학습 플랫폼 소개 영상"
          caption={{
            title: '영상으로 먼저 보는 F2R',
            description: 'AI 분석과 전문가 피드백이 어떻게 연결되는지 확인해보세요.',
            externalLink: 'https://www.youtube.com/watch?v=cLieSj8YxYA',
          }}
          trustLogos={['압구정에듀홀릭', '신사중', '신구중', '압구정중', '현대고']}
        />

        {/* SCHOOLS SECTION */}
        <section aria-labelledby="schools-title" className="mt-16 lg:mt-24 animate-in stagger-5">
          <div className="mb-8 lg:mb-12">
            <p className="text-xs font-extrabold uppercase tracking-wider text-accent mb-3">Learning marketplace</p>
            <h2 id="schools-title" className="text-2xl lg:text-3xl xl:text-4xl font-extrabold leading-tight tracking-tight text-foreground text-balance">
              지금 바로 시작할 수 있는 학습 선택지
            </h2>
            <p className="mt-3 text-base lg:text-lg leading-relaxed text-muted-foreground max-w-2xl">
              학원 탐색, 시험 분석, 블로그, 커뮤니티를 한 화면에서 연결합니다.
            </p>
          </div>

          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <SchoolCard
              title="공개 학원 둘러보기"
              description="지역, 과목, 수업 상품을 기준으로 내게 맞는 Academy를 찾아보세요."
              tags={['학원', '수업']}
              href="/academies"
              coverLabel="Academy"
              coverColor="from-accent-soft to-blue-100"
            />
            <SchoolCard
              title="내신 전략 글 모아보기"
              description="최근 공개된 블로그 글을 통해 학교별 학습 방향을 빠르게 파악합니다."
              tags={['블로그', '추천']}
              href="/academy-posts"
              coverLabel="Blog"
              coverColor="from-emerald-50 to-emerald-100"
            />
            <SchoolCard
              title="학습 Q&A 참여하기"
              description="궁금한 학습 질문을 남기고 공개 답변을 통해 함께 학습합니다."
              tags={['Q&A', '공개']}
              href="/community"
              coverLabel="Community"
              coverColor="from-violet-50 to-violet-100"
            />
          </div>
        </section>

        {/* CTA SECTION */}
        <CTASection
          title="이제, 당신의 브랜드를 만들 시간입니다"
          description="Build It으로 학원 홈페이지부터 학생 모집까지 한 번에 해결하세요."
          primaryCta={{
            label: 'Build It 시작하기',
            href: '/builder',
          }}
        />

        {/* Footer notice */}
        <footer className="mt-20 lg:mt-28 pt-12 border-t border-border">
          <p className="text-center text-sm text-muted-foreground">
            BuildIt Design Lab · TYPE B (React + Tailwind + shadcn/ui + 21st.dev)
            <br />
            이 페이지는 실험용이며 Production 서비스와 완전히 분리되어 있습니다.
          </p>
        </footer>
      </main>
    </div>
  );
}

export default App;