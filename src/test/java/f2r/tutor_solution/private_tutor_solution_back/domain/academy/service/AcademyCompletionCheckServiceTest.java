package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyBusinessInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoPurpose;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AcademyCompletionCheckService — deterministic 완성도 검사")
class AcademyCompletionCheckServiceTest {

  private AcademyService academyService;
  private LessonProductRepository lessonProductRepository;
  private AcademyVideoRepository academyVideoRepository;
  private AcademyCompletionCheckService service;
  private Academy academy;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    lessonProductRepository = mock(LessonProductRepository.class);
    academyVideoRepository = mock(AcademyVideoRepository.class);
    service = new AcademyCompletionCheckService(academyService, lessonProductRepository, academyVideoRepository);

    User tutor = User.builder().loginId("tutor").password("pw").name("강사").phone("010").build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    academy = Academy.builder()
        .tutor(tutor).slug("my-academy").name("우리학원").logoStorageKey("logo").intro("").build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);
  }

  private Academy withPageSettings(Map<String, String> draft, AcademyBusinessInfo businessInfo) {
    ReflectionTestUtils.setField(academy, "pageSettings",
        new AcademyPageSettings(null, null, null, null, Map.of(), List.of(), draft, businessInfo));
    return academy;
  }

  @Test
  @DisplayName("given 아무 것도 채우지 않은 신규 Academy when 평가 then 0%에 가깝고 모든 항목이 미완료다")
  void empty_academy_scores_low() {
    AcademyCompletionResult result = service.evaluate(academy, List.of(), List.of());
    assertThat(result.scorePercent()).isZero();
    assertThat(result.items()).allMatch(item -> !item.done());
  }

  @Test
  @DisplayName("given 모든 조건을 충족 when 평가 then 100%다")
  void fully_complete_academy_scores_100() {
    withPageSettings(
        Map.of(
            "principalDesc", "원장 소개문",
            "principalVideoId", "1",
            "miniLectures", "[{\"v\":1,\"t\":\"제목\"}]"),
        new AcademyBusinessInfo("학원", "대표", "주소", null, null, null, "010-0000-0000", null));
    ReflectionTestUtils.setField(academy, "intro", "학원 소개");

    AcademyVideo directorVideo = AcademyVideo.builder()
        .academy(academy).storageKey("k1").thumbnailKey("t1").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.DIRECTOR).build();
    AcademyVideo lessonVideo = AcademyVideo.builder()
        .academy(academy).storageKey("k2").thumbnailKey("t2").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.LESSON).build();

    LessonProduct lesson = LessonProduct.builder()
        .academy(academy).name("수업").mediaImageKey("img-key").build();

    AcademyCompletionResult result = service.evaluate(
        academy, List.of(lesson), List.of(directorVideo, lessonVideo));

    assertThat(result.scorePercent()).isEqualTo(100);
    assertThat(result.items()).allMatch(AcademyCompletionResult.CompletionItem::done);
  }

  @Test
  @DisplayName("given DIRECTOR 영상만 있고 LESSON 영상은 없을 때 then 두 항목이 갈린다(taxonomy 반영)")
  void distinguishes_director_from_lesson_video() {
    AcademyVideo directorOnly = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY)
        .purpose(AcademyVideoPurpose.DIRECTOR).build();

    AcademyCompletionResult result = service.evaluate(academy, List.of(), List.of(directorOnly));

    boolean directorDone = itemDone(result, "directorVideo");
    boolean lessonVideoDone = itemDone(result, "lessonVideo");
    assertThat(directorDone).isTrue();
    assertThat(lessonVideoDone).isFalse();
  }

  @Test
  @DisplayName("given purpose가 NULL(용도 미지정)인 레거시 영상만 있을 때 then 어느 항목도 완료 처리하지 않는다")
  void unclassified_video_does_not_count_as_either() {
    AcademyVideo unclassified = AcademyVideo.builder()
        .academy(academy).storageKey("k").thumbnailKey("t").status(VideoStatus.READY)
        .build(); // purpose 미설정

    AcademyCompletionResult result = service.evaluate(academy, List.of(), List.of(unclassified));

    assertThat(itemDone(result, "directorVideo")).isFalse();
    assertThat(itemDone(result, "lessonVideo")).isFalse();
  }

  @Test
  @DisplayName("given miniLectures가 빈 배열 문자열 when 평가 then 미완료로 판정한다")
  void empty_mini_lectures_array_is_not_done() {
    withPageSettings(Map.of("miniLectures", "[]"), null);
    AcademyCompletionResult result = service.evaluate(academy, List.of(), List.of());
    assertThat(itemDone(result, "miniLectures")).isFalse();
  }

  @Test
  @DisplayName("given 대표 수업에 mediaVideoId만 있고 mediaImageKey는 없을 때 then 프로그램 미디어 완료로 본다")
  void program_media_counts_video_reference_too() {
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy).name("수업").mediaVideoId(5L).build();
    AcademyCompletionResult result = service.evaluate(academy, List.of(lesson), List.of());
    assertThat(itemDone(result, "programMedia")).isTrue();
  }

  @Test
  @DisplayName("given businessInfo가 없을 때 when 평가 then CTA 항목은 미완료다")
  void missing_business_info_means_no_cta() {
    AcademyCompletionResult result = service.evaluate(academy, List.of(), List.of());
    assertThat(itemDone(result, "cta")).isFalse();
  }

  @Test
  @DisplayName("given 점수 산식 when 8개 중 4개 완료 then 정확히 50%다(임의 가중치 없음)")
  void score_is_plain_ratio_not_arbitrary_weights() {
    // 의도적으로 intro/principalDesc/directorVideo(principalVideoId)/lessons 4개만 채운다.
    // businessInfo·lessonVideo·miniLectures·programMedia는 비워 둔다.
    withPageSettings(Map.of("principalDesc", "소개", "principalVideoId", "1"), null);
    ReflectionTestUtils.setField(academy, "intro", "학원 소개");
    LessonProduct lesson = LessonProduct.builder().academy(academy).name("수업").build();

    AcademyCompletionResult result = service.evaluate(academy, List.of(lesson), List.of());

    assertThat(result.items()).hasSize(8);
    assertThat(result.items().stream().filter(AcademyCompletionResult.CompletionItem::done).count())
        .isEqualTo(4);
    assertThat(result.scorePercent()).isEqualTo(50);
  }

  private static boolean itemDone(AcademyCompletionResult result, String key) {
    return result.items().stream()
        .filter(item -> item.key().equals(key))
        .findFirst()
        .orElseThrow()
        .done();
  }
}
