package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactoryStage — 작업 상태와 기록만으로 공정 6단계를 판정한다")
class FactoryStageTest {

  final List<CoordJobEvent> history = new ArrayList<>();

  void add(String actor, String message) {
    history.add(new CoordJobEvent("job", LocalDateTime.of(2026, 11, 22, 10, history.size()), actor, "admin",
        CoordJob.RUNNING, CoordJob.RUNNING, message));
  }

  FactoryStage.Result running() {
    return FactoryStage.of(CoordJob.RUNNING, true, history);
  }

  @Test
  @DisplayName("given 공정 이름 when 읽기 then 대표가 정한 여섯 단계 순서다")
  void sixStages() {
    // given & when & then
    assertThat(FactoryStage.LABEL.values())
        .containsExactly("기출 분석", "시험범위 자료", "변형·유사 문제 제작", "검수", "검수 후 수정", "게시");
  }

  @Test
  @DisplayName("given 대기 중 when 판정 then ① 기출 분석 앞에서 기다림")
  void queuedWaitsAtFirstStage() {
    // given & when
    FactoryStage.Result r = FactoryStage.of(CoordJob.QUEUED, true, history);

    // then
    assertThat(r.stage()).isEqualTo("ANALYSIS");
    assertThat(r.state()).isEqualTo("WAITING");
    assertThat(r.no()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 가져간 뒤 단계 보고 SCOPE → DRAFT → REVIEW when 판정 then ① → ② → ③ → ④(검수 0/3)로 넘어간다")
  void makerReportsMoveStages() {
    // given & when & then
    assertThat(running().stage()).isEqualTo("ANALYSIS");
    add(FactoryStage.STAGE_ACTOR, "SCOPE · 기출 3회분 정리");
    assertThat(running().no()).isEqualTo(2);
    add(FactoryStage.STAGE_ACTOR, "DRAFT");
    assertThat(running().no()).isEqualTo(3);
    add(FactoryStage.STAGE_ACTOR, "REVIEW");
    assertThat(running().stage()).isEqualTo("REVIEW");
    assertThat(running().label()).isEqualTo("검수 0/3");
  }

  @Test
  @DisplayName("given 검수 PASS 2곳 뒤 FIX when 판정 then ⑤ 검수 후 수정, 다시 맡기면 ④ 0/3, 3곳 PASS면 ⑥ 원장 확인 대기")
  void reviewFixAndPass() {
    // given
    add(CoordPcTaskService.REVIEW_ACTOR, "PASS · REVIEW-1");
    add(CoordPcTaskService.REVIEW_ACTOR, "PASS · REVIEW-2 · 좋음");
    assertThat(running().label()).isEqualTo("검수 2/3");

    // when
    add(CoordPcTaskService.REVIEW_ACTOR, "FIX · REVIEW-3 · 7번 정답 오류");

    // then
    assertThat(running().stage()).isEqualTo("REVISE");
    assertThat(running().no()).isEqualTo(5);
    add(FactoryStage.STAGE_ACTOR, "REVIEW · 7번 고침");
    assertThat(running().label()).isEqualTo("검수 0/3");
    for (String name : List.of("REVIEW-4", "REVIEW-5", "REVIEW-6")) add(CoordPcTaskService.REVIEW_ACTOR, "PASS · " + name);
    assertThat(running().stage()).isEqualTo("PUBLISH");
    assertThat(running().state()).isEqualTo("OWNER_CONFIRM");
  }

  @Test
  @DisplayName("given 완료된 작업의 게시 기록 when 판정 then ⑥에서 게시 대기 → 승인됨 → 게시됨, 내리면 다시 게시 대기")
  void publishStates() {
    // given & when & then
    assertThat(FactoryStage.of(CoordJob.COMPLETED, true, history).state()).isEqualTo("READY");
    add(FactoryStage.PUBLISH_ACTOR, "APPROVED");
    assertThat(FactoryStage.of(CoordJob.COMPLETED, true, history).state()).isEqualTo("APPROVED");
    add(FactoryStage.PUBLISH_ACTOR, "PUBLISHED · /naesintest/school/singu-m2");
    FactoryStage.Result published = FactoryStage.of(CoordJob.COMPLETED, true, history);
    assertThat(published.state()).isEqualTo("PUBLISHED");
    assertThat(published.no()).isEqualTo(6);
    add(FactoryStage.PUBLISH_ACTOR, "WITHDRAWN · 오탈자");
    assertThat(FactoryStage.publishState(history)).isEqualTo("READY");
  }

  @Test
  @DisplayName("given 취소·실패 when 판정 then 공정 밖(단계 없음, 0번)")
  void closedJobsHaveNoStage() {
    // given & when
    FactoryStage.Result cancelled = FactoryStage.of(CoordJob.CANCELLED, true, history);
    FactoryStage.Result failed = FactoryStage.of(CoordJob.FAILED, true, history);

    // then
    assertThat(cancelled.stage()).isNull();
    assertThat(cancelled.no()).isZero();
    assertThat(cancelled.label()).isEqualTo("취소됨");
    assertThat(failed.label()).isEqualTo("실패");
  }

  @Test
  @DisplayName("given 기록 글 when head·tail then 「머리 · 나머지」로 나눈다(나머지가 없으면 빈 글)")
  void headAndTail() {
    // given
    add(FactoryStage.STAGE_ACTOR, "SCOPE · 교과서 · 프린트");
    add(FactoryStage.STAGE_ACTOR, "DRAFT");

    // when & then
    assertThat(FactoryStage.head(history.get(0))).isEqualTo("SCOPE");
    assertThat(FactoryStage.tail(history.get(0))).isEqualTo("교과서 · 프린트");
    assertThat(FactoryStage.tail(history.get(1))).isEmpty();
  }
}
