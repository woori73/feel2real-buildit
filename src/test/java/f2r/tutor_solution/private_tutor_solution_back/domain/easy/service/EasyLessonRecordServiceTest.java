package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyGradingRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingPhotoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyLessonRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.WrongItem;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EasyLessonRecordService — 학생부 수업·채점 기록 검증·오답 집계·사진 형식")
class EasyLessonRecordServiceTest {

  EasyGradingRecordRepository gradings;
  EasyGradingPhotoRepository photos;
  FileStorage storage;
  EasyLessonRecordService service;
  EasyBusiness biz;

  @BeforeEach
  void setUp() {
    EasyLessonRecordRepository lessons = mock(EasyLessonRecordRepository.class);
    gradings = mock(EasyGradingRecordRepository.class);
    photos = mock(EasyGradingPhotoRepository.class);
    EasyStudentRepository students = mock(EasyStudentRepository.class);
    EasyBusinessRepository businesses = mock(EasyBusinessRepository.class);
    storage = mock(FileStorage.class);
    service = new EasyLessonRecordService(lessons, gradings, photos, students, businesses,
        mock(EasyClassRepository.class), mock(EasyAttendanceService.class), storage);
    biz = EasyBusiness.builder().code("A").name("학원").active(true).build();
    ReflectionTestUtils.setField(biz, "businessId", 3L);
    EasyStudent s = EasyStudent.builder().name("김민수").status(EasyStudentStatus.ENROLLED).build();
    ReflectionTestUtils.setField(s, "studentId", 11L);
    when(businesses.findById(3L)).thenReturn(Optional.of(biz));
    when(students.findById(11L)).thenReturn(Optional.of(s));
    when(lessons.save(any())).thenAnswer(i -> i.getArgument(0));
    when(gradings.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  @DisplayName("given 아무 내용 없는 수업 기록 when 저장 then 거절, 잘못된 숙제 확인·날짜도 거절")
  void lesson_requiresContent() {
    // given & when & then
    assertThatThrownBy(() -> service.createLesson(3L, 11L, new LessonForm(null, "2026-11-20", " ", null, null, null, null), "WEB"))
        .hasMessageContaining("하나는");
    assertThatThrownBy(() -> service.createLesson(3L, 11L, new LessonForm(null, "2026-11-20", "3과", "MAYBE", null, null, null), "WEB"))
        .hasMessageContaining("숙제 확인");
    assertThatThrownBy(() -> service.createLesson(3L, 11L, new LessonForm(null, "11/20", "3과", null, null, null, null), "WEB"))
        .hasMessageContaining("YYYY-MM-DD");
  }

  @Test
  @DisplayName("given 수업 범위·숙제 when 저장 then 날짜·내용·숙제 확인 라벨이 담긴다")
  void lesson_saves() {
    // given & when
    var v = service.createLesson(3L, 11L, new LessonForm(null, "2026-11-20", " 능률 3과 ", "partial", "p.30~35", "p.36", null), "MCP");

    // then
    assertThat(v.lessonDate()).isEqualTo("2026-11-20");
    assertThat(v.lessonRange()).isEqualTo("능률 3과");
    assertThat(v.homeworkCheckLabel()).isEqualTo("일부 해 옴");
    assertThat(v.source()).isEqualTo("MCP");
  }

  @Test
  @DisplayName("given 맞은 수가 전체보다 많음·내용 없음 when 채점 저장 then 거절")
  void grading_validatesCounts() {
    // given & when & then
    assertThatThrownBy(() -> service.createGrading(3L, 11L,
        new GradingForm(null, "2026-11-20", "문법", null, 10, 11, List.of(), null), "WEB")).hasMessageContaining("많습니다");
    assertThatThrownBy(() -> service.createGrading(3L, 11L,
        new GradingForm(null, "2026-11-20", "문법", null, null, null, List.of(), " "), "WEB")).hasMessageContaining("하나는");
  }

  @Test
  @DisplayName("given 틀린 문항 when 채점 저장 then JSON으로 저장되고 다시 읽힌다(긴 이유는 300자로 자름)")
  void grading_wrongItemsRoundTrip() {
    // given
    String longReason = "가".repeat(400);

    // when
    GradingView v = service.createGrading(3L, 11L, new GradingForm(null, "2026-11-20", "문법", "능률 3과", 20, 18,
        List.of(new WrongItem("3", "관계대명사", "what/that 혼동"), new WrongItem("7", null, longReason)), "관계대명사 복습"), "MCP");

    // then
    assertThat(v.wrongItems()).hasSize(2);
    assertThat(v.wrongItems().get(0)).isEqualTo(new WrongItem("3", "관계대명사", "what/that 혼동"));
    assertThat(v.wrongItems().get(1).reason()).hasSize(300);
  }

  @Test
  @DisplayName("given 여러 채점 when 오답 집계 then 유형별 횟수(유형 없으면 분야) 많은 순, 마지막 날짜")
  void wrongTopics() {
    // given
    var a = new GradingView(1L, 11L, null, "2026-11-18", "문법", null, 10, 8,
        List.of(new WrongItem("1", "관계대명사", ""), new WrongItem("2", "시제", "")), null, "WEB", List.of());
    var b = new GradingView(2L, 11L, null, "2026-11-20", "문법", null, 10, 8,
        List.of(new WrongItem("4", "관계대명사", ""), new WrongItem("5", null, "")), null, "WEB", List.of());

    // when
    var topics = EasyLessonRecordService.wrongTopics(List.of(b, a));

    // then
    assertThat(topics.get(0)).isEqualTo(new EasyLessonRecordService.TopicCount("관계대명사", 2, "2026-11-20"));
    assertThat(topics).extracting(EasyLessonRecordService.TopicCount::topic).containsExactly("관계대명사", "문법", "시제");
  }

  @Test
  @DisplayName("given 파일 앞부분 when 이미지 판별 then JPEG·PNG·WebP만, 나머지(PDF·실행 파일)는 거절")
  void imageExtension() {
    // given & when & then
    assertThat(EasyLessonRecordService.imageExtension(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("jpg");
    assertThat(EasyLessonRecordService.imageExtension(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo("png");
    assertThat(EasyLessonRecordService.imageExtension("RIFF0000WEBPVP8 ".getBytes())).isEqualTo("webp");
    assertThat(EasyLessonRecordService.imageExtension("%PDF-1.7".getBytes())).isNull();
    assertThat(EasyLessonRecordService.imageExtension(new byte[] {0x4D, 0x5A, 0, 0})).isNull();
  }

  @Test
  @DisplayName("given 다른 사업체 채점·PDF 파일 when 사진 올리기 then 거절하고 저장소에 쓰지 않는다")
  void addPhoto_rejects() throws Exception {
    // given
    EasyBusiness other = EasyBusiness.builder().code("B").name("다른 곳").active(true).build();
    ReflectionTestUtils.setField(other, "businessId", 9L);
    EasyGradingRecord g = EasyGradingRecord.builder().business(other).student(null).gradedOn(java.time.LocalDate.now()).build();
    ReflectionTestUtils.setField(g, "gradingId", 5L);
    EasyGradingRecord mine = EasyGradingRecord.builder().business(biz).student(null).gradedOn(java.time.LocalDate.now()).build();
    ReflectionTestUtils.setField(mine, "gradingId", 6L);
    when(gradings.findById(5L)).thenReturn(Optional.of(g));
    when(gradings.findById(6L)).thenReturn(Optional.of(mine));

    // when & then
    assertThatThrownBy(() -> service.addPhoto(3L, 5L, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 1L))
        .hasMessageContaining("다른 사업체");
    assertThatThrownBy(() -> service.addPhoto(3L, 6L, "%PDF-1.7".getBytes(), 1L)).hasMessageContaining("JPEG");
    verify(storage, never()).saveImage(any(), any(), any(), any());
  }
}
