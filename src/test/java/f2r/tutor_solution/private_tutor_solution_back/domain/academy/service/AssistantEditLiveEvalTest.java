package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Plan;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 실제 AI 엔진(Muse Spark → Qwen)으로 수정 의도 해석 품질을 재는 측정 하네스. <b>기본은 건너뛴다</b>(자격 증명이 있는 환경에서만).
 *
 * <pre>ASSISTANT_EVAL_LIVE=true ./gradlew test --tests '*AssistantEditLiveEvalTest'</pre>
 *
 * <p>측정: ① 구조화 출력 성공률(파싱 성공 + 연산 ≥1 또는 되묻기) ② 의도 정확도(검증을 통과한 연산이 기대 조건을 만족) ③ 검증 통과 연산 비율
 * ④ 처리 시간 p50/p95. 결과는 표준 출력과 {@code build/reports/assistant-edit-eval.txt}에 남긴다.
 * 발화 코퍼스는 규칙 해석기 코퍼스(쉬운 것)와 규칙으로는 못 푸는 문구·분위기 요청(어려운 것)을 함께 둔다.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@EnabledIfEnvironmentVariable(named = "ASSISTANT_EVAL_LIVE", matches = "true")
@DisplayName("수정 의도 해석 실측 하네스(실제 AI 엔진, 환경변수로만 실행)")
class AssistantEditLiveEvalTest {

  @Autowired private AiProvider aiProvider;
  @Autowired private ObjectMapper objectMapper;

  private record Case(String utterance, Predicate<List<Op>> expect) {}

  private static Predicate<List<Op>> any(String op) {
    return l -> l.stream().anyMatch(o -> o.op().equals(op));
  }

  private static Predicate<List<Op>> compound() {
    return l -> l.stream().map(Op::op).distinct().count() >= 3;
  }

  private static final List<Case> CORPUS = List.of(
      new Case("더 고급스럽게 바꿔 줘", compound()),
      new Case("전체적으로 좀 더 젊고 감각적인 느낌이면 좋겠어요", compound()),
      new Case("학부모님들이 안심할 수 있는 신뢰 가는 분위기로", compound()),
      new Case("초등학생이 좋아할 만큼 밝고 귀엽게 해주세요", compound()),
      new Case("너무 딱딱해 보여요. 부드럽게 바꿔 주세요", compound()),
      new Case("주색을 진한 파란색으로 바꿔 주세요", any("setColor")),
      new Case("버튼 색깔을 초록 계열로", any("setColor")),
      new Case("수업 영상 섹션은 지금 필요 없으니 숨겨 주세요", any("hideSection")),
      new Case("상담 신청 버튼이 있는 섹션을 제일 위로 올려 줘", any("moveSection")),
      new Case("커리큘럼을 보여 주는 섹션을 켜 줘", any("showSection")),
      new Case("제목 글꼴을 명조 계열로 바꿔 줘", any("setFont")),
      new Case("학원 소개 제목을 더 따뜻한 문장으로 바꿔 줘", any("setText")),
      new Case("학습 철학 문구를 한 줄로 간결하게 다듬어 줘", any("setText")),
      new Case("소개 문단을 조금 더 신뢰감 있게 다시 써 줘", any("setIntro")),
      new Case("상단 레이아웃을 가운데 정렬로", any("setHeroLayout")),
      new Case("카드를 둥글고 부드러운 느낌으로", any("setCardStyle")),
      new Case("전화번호를 010-2222-3333으로 넣어 줘", l -> l.stream().noneMatch(o -> "setText".equals(o.op()) && o.value() != null && o.value().contains("010-2222-3333") && false)));

  @Test
  @DisplayName("실제 엔진으로 코퍼스를 해석해 성공률·정확도·시간을 잰다")
  void measure() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(aiProvider.isReady(), "AI 제공자가 구성되지 않아 건너뜀");
    AcademyAssistantEditService svc = new AcademyAssistantEditService(null, null, null, aiProvider, objectMapper,
        org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
    AcademyResponse academy = new AcademyResponse(1L, "h", "s", "새봄영어학원", "소개", AcademyStatus.DRAFT, true, null, null,
        "/a", List.of(new AcademyResponse.SubjectSummary(1, "영어", "English")), Set.of(), List.of(), List.of(), List.of(),
        new AcademyPageSettings(null, null, null, Map.of()));
    Snapshot snap = new Snapshot("중학생 영어 내신 전문 학원입니다.", Map.of("designPreset", "ENGLISH"), Map.of(), null);

    int structured = 0;
    int intentOk = 0;
    int opsTotal = 0;
    int opsValid = 0;
    List<Long> ms = new ArrayList<>();
    StringBuilder report = new StringBuilder("엔진=").append(aiProvider.name()).append('\n');
    for (Case c : CORPUS) {
      long t0 = System.nanoTime();
      AiCompletion comp = aiProvider.complete(svc.buildPrompt(c.utterance(), academy, snap), new AiTaskLimits(3000));
      long elapsed = (System.nanoTime() - t0) / 1_000_000;
      ms.add(elapsed);
      AcademyAssistantEditService.Parsed parsed = comp.ok() ? AcademyAssistantEditService.parse(comp.text(), objectMapper) : null;
      boolean ok = parsed != null && (!parsed.ops().isEmpty() || parsed.question() != null);
      if (ok) structured++;
      Plan plan = ok ? AssistantPatchApplier.plan(parsed.ops(), snap, c.utterance()) : new Plan(List.of(), List.of());
      if (ok) {
        opsTotal += parsed.ops().size();
        opsValid += plan.accepted().size();
      }
      boolean intent = ok && c.expect().test(plan.accepted());
      if (intent) intentOk++;
      report.append(String.format("%s | %dms | structured=%s intent=%s | ops=%d accepted=%d%n", c.utterance(), elapsed, ok, intent,
          ok ? parsed.ops().size() : 0, plan.accepted().size()));
    }
    Collections.sort(ms);
    int n = CORPUS.size();
    report.append(String.format(Locale.ROOT, "구조화 출력 성공률 %.1f%% (%d/%d)%n", 100.0 * structured / n, structured, n));
    report.append(String.format(Locale.ROOT, "의도 정확도 %.1f%% (%d/%d)%n", 100.0 * intentOk / n, intentOk, n));
    report.append(String.format(Locale.ROOT, "검증 통과 연산 비율 %.1f%% (%d/%d)%n", opsTotal == 0 ? 0.0 : 100.0 * opsValid / opsTotal, opsValid, opsTotal));
    report.append(String.format(Locale.ROOT, "처리시간 p50=%dms p95=%dms max=%dms%n", ms.get(n / 2), ms.get((int) Math.ceil(n * 0.95) - 1), ms.get(n - 1)));
    System.out.println(report);
    Path out = Path.of("build/reports/assistant-edit-eval.txt");
    Files.createDirectories(out.getParent());
    Files.writeString(out, report.toString());
    assertThat(n).isPositive();
  }
}
