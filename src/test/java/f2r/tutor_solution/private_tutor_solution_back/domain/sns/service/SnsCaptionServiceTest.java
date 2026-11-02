package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnsCaptionServiceTest {

  NewStartLlmTransport llm = mock(NewStartLlmTransport.class);
  SnsCaptionService service = new SnsCaptionService(llm);

  @Test
  @DisplayName("given 정상 JSON 응답 when generate then 본문 아래 고정 문구가 정확히 붙고 해시태그는 고정 먼저")
  void generate_appendsFixedTextAndHashtags() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success(
        "```json\n{\"topic\":\"관계대명사 what\",\"body\":\"오늘의 포인트\\n예문: What I need is time.\","
            + "\"hashtags\":[\"#영어공부\",\"Feel2Real\",\"#내신 대비\"],\"image_prompt_en\":\"a man with a tablet\"}\n```",
        "meta-muse-spark", "muse"));
    // when
    SnsCaptionService.Generated g = service.generate(rule(), brand(SnsBrandPreset.FixedTextPosition.BOTTOM), List.of("동명사"));
    // then
    assertThat(g.caption()).isEqualTo("오늘의 포인트\n예문: What I need is time.\n\n📌 고정\n2주 160문항 · 20,000원");
    assertThat(g.hashtags()).isEqualTo("#Feel2Real #영어내신 #영어공부");
    assertThat(g.topic()).isEqualTo("관계대명사 what");
    assertThat(g.imagePromptEn()).isEqualTo("a man with a tablet");
    assertThat(g.prompt()).contains("동명사").contains("쓰면 안 되는 표현: 최강");
  }

  @Test
  @DisplayName("given 본문에 가격 when generate then 거절")
  void generate_rejectsPriceInBody() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success(
        "{\"topic\":\"t\",\"body\":\"지금 15,000원에 신청\",\"hashtags\":[],\"image_prompt_en\":\"x\"}", "p", "m"));
    // when / then
    assertThatThrownBy(() -> service.generate(rule(), brand(SnsBrandPreset.FixedTextPosition.BOTTOM), List.of()))
        .isInstanceOf(SnsCaptionService.CaptionRejected.class).hasMessageContaining("가격");
  }

  @Test
  @DisplayName("given 금지 표현 when generate then 거절")
  void generate_rejectsBannedPhrase() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success(
        "{\"topic\":\"t\",\"body\":\"최강 문법 팁\",\"hashtags\":[],\"image_prompt_en\":\"x\"}", "p", "m"));
    // when / then
    assertThatThrownBy(() -> service.generate(rule(), brand(SnsBrandPreset.FixedTextPosition.BOTTOM), List.of()))
        .isInstanceOf(SnsCaptionService.CaptionRejected.class).hasMessageContaining("최강");
  }

  @Test
  @DisplayName("given LLM 실패 when generate then 거절(사유 포함)")
  void generate_llmFailure() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.failed("연결 실패", true));
    // when / then
    assertThatThrownBy(() -> service.generate(rule(), brand(SnsBrandPreset.FixedTextPosition.BOTTOM), List.of()))
        .isInstanceOf(SnsCaptionService.CaptionRejected.class).hasMessageContaining("연결 실패");
  }

  @Test
  @DisplayName("given 고정 문구 위치 TOP when composeCaption then 고정 문구가 먼저")
  void composeCaption_top() {
    // given
    SnsBrandPreset b = brand(SnsBrandPreset.FixedTextPosition.TOP);
    // when
    String c = SnsCaptionService.composeCaption("본문", b);
    // then
    assertThat(c).startsWith("📌 고정").endsWith("\n\n본문");
  }

  @Test
  @DisplayName("given 해시태그 40개 when composeHashtags then AI는 10개까지, 중복·잘못된 태그 제거")
  void composeHashtags_limits() {
    // given
    List<String> ai = new java.util.ArrayList<>(List.of("#a", "#a", "not a tag!", "#b"));
    for (int i = 0; i < 40; i++) ai.add("#t" + i);
    // when
    String tags = SnsCaptionService.composeHashtags(List.of("#Fixed"), ai);
    // then
    assertThat(tags.split(" ")).hasSize(11).startsWith("#Fixed", "#a", "#b");
  }

  static SnsAutomationRule rule() {
    SnsAutomationRule r = new SnsAutomationRule();
    r.setTitle("영어");
    r.setImagePrompt("세븐 사진");
    r.setFixedHashtags("#Feel2Real 영어내신");
    r.setAiHashtags(true);
    return r;
  }

  static SnsBrandPreset brand(SnsBrandPreset.FixedTextPosition pos) {
    SnsBrandPreset b = new SnsBrandPreset();
    b.setName("F2R 영어학습");
    b.setPurpose("목적");
    b.setFixedText("📌 고정\n2주 160문항 · 20,000원");
    b.setFixedTextPosition(pos);
    b.setBannedPhrases("최강\n무조건");
    return b;
  }
}
