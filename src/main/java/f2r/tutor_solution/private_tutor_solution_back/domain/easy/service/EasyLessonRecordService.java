package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyGradingPhoto;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyGradingRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLessonRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLessonRecord.HomeworkCheck;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingPhotoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyLessonRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.TransactionalFileDeletion;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 학생부: 학생별 수업 기록(수업 범위·숙제 확인·낸 숙제·메모)과 채점 기록(이미 채점된 답지 사진 + 맞은 수·틀린 문항·피드백).
 * 관리자 전용(/api/admin/**, MCP coord:draft + ADMIN). 사용자 결정(2026-11-19): 수정·삭제 가능, 답지 사진도 저장.
 *
 * <p>채점은 F2R 서버가 하지 않는다 — 휴대폰 Claude(사용자 앱)가 사진을 보고 틀린 문항·피드백을 만들고, 여기에는 결과만 기록한다.
 * 오답 기록은 채점 기록의 틀린 문항(분야·유형별)을 모아 보여 준다.
 */
@Service
@Transactional
public class EasyLessonRecordService {

  public static final int MAX_PHOTOS = 5;
  public static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
  static final int MAX_WRONG_ITEMS = 200;
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  private final EasyLessonRecordRepository lessons;
  private final EasyGradingRecordRepository gradings;
  private final EasyGradingPhotoRepository photos;
  private final EasyStudentRepository students;
  private final EasyBusinessRepository businesses;
  private final EasyClassRepository classes;
  private final EasyAttendanceService attendance;
  private final FileStorage storage;

  public EasyLessonRecordService(EasyLessonRecordRepository lessons, EasyGradingRecordRepository gradings,
      EasyGradingPhotoRepository photos, EasyStudentRepository students, EasyBusinessRepository businesses,
      EasyClassRepository classes, EasyAttendanceService attendance, FileStorage storage) {
    this.lessons = lessons;
    this.gradings = gradings;
    this.photos = photos;
    this.students = students;
    this.businesses = businesses;
    this.classes = classes;
    this.attendance = attendance;
    this.storage = storage;
  }

  /* ---------------- 요청·응답 ---------------- */

  public record LessonForm(Long classId, String lessonDate, String lessonRange, String homeworkCheck,
      String homeworkDoneNote, String nextHomework, String teacherNote) {}

  public record WrongItem(String no, String topic, String reason) {}

  public record GradingForm(Long lessonRecordId, String gradedOn, String subjectArea, String material,
      Integer totalCount, Integer correctCount, List<WrongItem> wrongItems, String feedback) {}

  public record LessonView(Long lessonRecordId, Long studentId, Long classId, String className, String lessonDate,
      String lessonRange, String homeworkCheck, String homeworkCheckLabel, String homeworkDoneNote,
      String nextHomework, String teacherNote, String source) {}

  public record PhotoView(Long photoId, String contentType, long sizeBytes) {}

  public record GradingView(Long gradingId, Long studentId, Long lessonRecordId, String gradedOn, String subjectArea,
      String material, Integer totalCount, Integer correctCount, List<WrongItem> wrongItems, String feedback,
      String source, List<PhotoView> photos) {}

  public record TopicCount(String topic, int count, String lastDate) {}

  public record PhotoFile(byte[] bytes, String contentType) {}

  /* ---------------- 수업 기록 ---------------- */

  public LessonView createLesson(Long businessId, Long studentId, LessonForm f, String source) {
    EasyBusiness b = requireBusiness(businessId);
    EasyStudent s = requireStudent(studentId);
    EasyLessonRecord r = lessons.save(EasyLessonRecord.builder().business(b).student(s)
        .easyClass(classOf(businessId, f.classId())).lessonDate(date(f.lessonDate()))
        .lessonRange(clip(f.lessonRange(), 300)).homeworkCheck(check(f.homeworkCheck()))
        .homeworkDoneNote(clip(f.homeworkDoneNote(), 500)).nextHomework(clip(f.nextHomework(), 500))
        .teacherNote(clip(f.teacherNote(), 1000)).source(source).build());
    requireSomething(r);
    return view(r);
  }

  public LessonView updateLesson(Long businessId, Long lessonRecordId, LessonForm f) {
    EasyLessonRecord r = ownLesson(businessId, lessonRecordId);
    r.update(classOf(businessId, f.classId()), date(f.lessonDate()), clip(f.lessonRange(), 300),
        check(f.homeworkCheck()), clip(f.homeworkDoneNote(), 500), clip(f.nextHomework(), 500),
        clip(f.teacherNote(), 1000));
    requireSomething(r);
    return view(r);
  }

  /** 수업 기록 삭제. 연결된 채점 기록은 남기고 연결만 끊는다. */
  public void deleteLesson(Long businessId, Long lessonRecordId) {
    EasyLessonRecord r = ownLesson(businessId, lessonRecordId);
    gradings.findByLessonRecord_LessonRecordId(r.getLessonRecordId()).forEach(EasyGradingRecord::detachLesson);
    lessons.delete(r);
  }

  @Transactional(readOnly = true)
  public LessonView lesson(Long businessId, Long lessonRecordId) {
    return view(ownLesson(businessId, lessonRecordId));
  }

  /* ---------------- 채점 기록 ---------------- */

  public GradingView createGrading(Long businessId, Long studentId, GradingForm f, String source) {
    EasyBusiness b = requireBusiness(businessId);
    EasyStudent s = requireStudent(studentId);
    counts(f);
    EasyGradingRecord g = gradings.save(EasyGradingRecord.builder().business(b).student(s)
        .lessonRecord(linkedLesson(businessId, studentId, f.lessonRecordId())).gradedOn(date(f.gradedOn()))
        .subjectArea(clip(f.subjectArea(), 100)).material(clip(f.material(), 200)).totalCount(f.totalCount())
        .correctCount(f.correctCount()).wrongItems(wrongJson(f.wrongItems())).feedback(clip(f.feedback(), 2000))
        .source(source).build());
    return view(g);
  }

  public GradingView updateGrading(Long businessId, Long gradingId, GradingForm f) {
    EasyGradingRecord g = ownGrading(businessId, gradingId);
    counts(f);
    g.update(linkedLesson(businessId, g.getStudent().getStudentId(), f.lessonRecordId()), date(f.gradedOn()),
        clip(f.subjectArea(), 100), clip(f.material(), 200), f.totalCount(), f.correctCount(),
        wrongJson(f.wrongItems()), clip(f.feedback(), 2000));
    return view(g);
  }

  /** 채점 기록 삭제. 사진 행을 지우고, 파일은 커밋 뒤에 지운다. */
  public void deleteGrading(Long businessId, Long gradingId) {
    EasyGradingRecord g = ownGrading(businessId, gradingId);
    for (EasyGradingPhoto p : photos.findByGrading_GradingIdOrderByPhotoIdAsc(gradingId)) {
      String key = p.getStorageKey();
      photos.delete(p);
      TransactionalFileDeletion.afterCommit(() -> storage.delete(key, "IMAGE"));
    }
    gradings.delete(g);
  }

  @Transactional(readOnly = true)
  public GradingView grading(Long businessId, Long gradingId) {
    return view(ownGrading(businessId, gradingId));
  }

  /* ---------------- 답지 사진 ---------------- */

  /** 이미 채점된 답지 사진 1장(JPEG·PNG·WebP, 10MB 이하, 채점 1건당 5장까지). */
  public PhotoView addPhoto(Long businessId, Long gradingId, byte[] bytes, Long userId) throws IOException {
    EasyGradingRecord g = ownGrading(businessId, gradingId);
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("사진 파일이 비어 있습니다.");
    if (bytes.length > MAX_PHOTO_BYTES) throw new IllegalArgumentException("사진은 10MB 이하만 올릴 수 있습니다.");
    String ext = imageExtension(bytes);
    if (ext == null) throw new IllegalArgumentException("JPEG·PNG·WebP 사진만 올릴 수 있습니다.");
    if (photos.findByGrading_GradingIdOrderByPhotoIdAsc(gradingId).size() >= MAX_PHOTOS) {
      throw new IllegalArgumentException("채점 1건에 사진은 " + MAX_PHOTOS + "장까지입니다.");
    }
    String key = storage.saveImage(userId, "grading-" + gradingId, bytes, ext);
    TransactionalFileDeletion.onRollback(() -> storage.delete(key, "IMAGE"));
    EasyGradingPhoto p = photos.save(new EasyGradingPhoto(g, key, contentType(ext), bytes.length));
    return new PhotoView(p.getPhotoId(), p.getContentType(), p.getSizeBytes());
  }

  @Transactional(readOnly = true)
  public PhotoFile readPhoto(Long businessId, Long photoId) throws IOException {
    EasyGradingPhoto p = ownPhoto(businessId, photoId);
    return new PhotoFile(storage.readSingleLimited(p.getStorageKey(), "IMAGE", MAX_PHOTO_BYTES), p.getContentType());
  }

  public void deletePhoto(Long businessId, Long photoId) {
    EasyGradingPhoto p = ownPhoto(businessId, photoId);
    String key = p.getStorageKey();
    photos.delete(p);
    TransactionalFileDeletion.afterCommit(() -> storage.delete(key, "IMAGE"));
  }

  /* ---------------- 학생부(한 화면) ---------------- */

  /** 학생 한 명의 학생부: 최근 출결 + 수업 기록 + 채점 기록 + 오답 기록(분야·유형별 횟수). */
  @Transactional(readOnly = true)
  public Map<String, Object> studentBook(Long businessId, Long studentId) {
    requireBusiness(businessId);
    EasyStudent s = requireStudent(studentId);
    List<LessonView> lessonViews = lessons
        .findByBusiness_BusinessIdAndStudent_StudentIdOrderByLessonDateDescLessonRecordIdDesc(businessId, studentId)
        .stream().map(this::view).toList();
    List<GradingView> gradingViews = gradings
        .findByBusiness_BusinessIdAndStudent_StudentIdOrderByGradedOnDescGradingIdDesc(businessId, studentId)
        .stream().map(this::view).toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("studentId", s.getStudentId());
    out.put("name", s.getName());
    out.put("school", s.getSchool());
    out.put("grade", s.getGrade() == null ? null : s.getGrade().intValue());
    out.put("attendance", attendance.studentHistory(businessId, studentId).stream().limit(30).toList());
    out.put("lessons", lessonViews);
    out.put("gradings", gradingViews);
    out.put("wrongTopics", wrongTopics(gradingViews));
    return out;
  }

  /** 틀린 문항을 분야·유형(topic)별로 모은다. topic이 없으면 채점 분야, 그것도 없으면 "분류 없음". 많이 틀린 순. */
  public static List<TopicCount> wrongTopics(List<GradingView> list) {
    Map<String, int[]> count = new LinkedHashMap<>();
    Map<String, String> last = new LinkedHashMap<>();
    for (GradingView g : list) {
      for (WrongItem w : g.wrongItems()) {
        String t = notBlank(w.topic()) ? w.topic().strip() : notBlank(g.subjectArea()) ? g.subjectArea().strip() : "분류 없음";
        count.computeIfAbsent(t, k -> new int[1])[0]++;
        last.merge(t, g.gradedOn(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
      }
    }
    return count.entrySet().stream().map(e -> new TopicCount(e.getKey(), e.getValue()[0], last.get(e.getKey())))
        .sorted(Comparator.comparingInt(TopicCount::count).reversed().thenComparing(TopicCount::topic)).toList();
  }

  /* ---------------- 내부 ---------------- */

  private LessonView view(EasyLessonRecord r) {
    EasyClass c = r.getEasyClass();
    HomeworkCheck h = r.getHomeworkCheck();
    return new LessonView(r.getLessonRecordId(), r.getStudent().getStudentId(), c == null ? null : c.getClassId(),
        c == null ? null : c.getName(), r.getLessonDate().toString(), r.getLessonRange(), h == null ? null : h.name(),
        h == null ? null : h.label(), r.getHomeworkDoneNote(), r.getNextHomework(), r.getTeacherNote(), r.getSource());
  }

  private GradingView view(EasyGradingRecord g) {
    List<PhotoView> ps = g.getGradingId() == null ? List.of() : photos.findByGrading_GradingIdOrderByPhotoIdAsc(g.getGradingId())
        .stream().map(p -> new PhotoView(p.getPhotoId(), p.getContentType(), p.getSizeBytes())).toList();
    return new GradingView(g.getGradingId(), g.getStudent().getStudentId(),
        g.getLessonRecord() == null ? null : g.getLessonRecord().getLessonRecordId(), g.getGradedOn().toString(),
        g.getSubjectArea(), g.getMaterial(), g.getTotalCount(), g.getCorrectCount(), parseWrong(g.getWrongItems()),
        g.getFeedback(), g.getSource(), ps);
  }

  private void requireSomething(EasyLessonRecord r) {
    if (!notBlank(r.getLessonRange()) && r.getHomeworkCheck() == null && !notBlank(r.getHomeworkDoneNote())
        && !notBlank(r.getNextHomework()) && !notBlank(r.getTeacherNote())) {
      throw new IllegalArgumentException("수업 범위·숙제 확인·해 온 숙제·낸 숙제·메모 중 하나는 적어 주세요.");
    }
  }

  private static void counts(GradingForm f) {
    Integer t = f.totalCount(), c = f.correctCount();
    if (t != null && (t < 0 || t > 1000)) throw new IllegalArgumentException("전체 문항 수는 0~1000 사이입니다.");
    if (c != null && c < 0) throw new IllegalArgumentException("맞은 수는 0 이상입니다.");
    if (t != null && c != null && c > t) throw new IllegalArgumentException("맞은 수가 전체 문항 수보다 많습니다.");
    int wrong = f.wrongItems() == null ? 0 : f.wrongItems().size();
    if (wrong > MAX_WRONG_ITEMS) throw new IllegalArgumentException("틀린 문항은 " + MAX_WRONG_ITEMS + "개까지 적을 수 있습니다.");
    if (t == null && wrong == 0 && !notBlank(f.feedback())) {
      throw new IllegalArgumentException("전체 문항 수·틀린 문항·피드백 중 하나는 적어 주세요.");
    }
  }

  static String wrongJson(List<WrongItem> items) {
    if (items == null || items.isEmpty()) return "[]";
    List<WrongItem> clean = new ArrayList<>();
    for (WrongItem w : items) {
      if (w == null) continue;
      clean.add(new WrongItem(clip(w.no(), 20), clip(w.topic(), 100), clip(w.reason(), 300)));
    }
    return JSON.writeValueAsString(clean);
  }

  static List<WrongItem> parseWrong(String json) {
    if (json == null || json.isBlank()) return List.of();
    return JSON.readValue(json, new TypeReference<List<WrongItem>>() {});
  }

  private EasyLessonRecord linkedLesson(Long businessId, Long studentId, Long lessonRecordId) {
    if (lessonRecordId == null) return null;
    EasyLessonRecord r = ownLesson(businessId, lessonRecordId);
    if (!r.getStudent().getStudentId().equals(studentId)) throw new IllegalArgumentException("다른 학생의 수업 기록입니다.");
    return r;
  }

  private EasyLessonRecord ownLesson(Long businessId, Long id) {
    EasyLessonRecord r = lessons.findById(id == null ? -1L : id)
        .orElseThrow(() -> new IllegalArgumentException("수업 기록을 찾을 수 없습니다."));
    if (!r.getBusiness().getBusinessId().equals(businessId)) throw new IllegalArgumentException("다른 사업체의 수업 기록입니다.");
    return r;
  }

  private EasyGradingRecord ownGrading(Long businessId, Long id) {
    EasyGradingRecord g = gradings.findById(id == null ? -1L : id)
        .orElseThrow(() -> new IllegalArgumentException("채점 기록을 찾을 수 없습니다."));
    if (!g.getBusiness().getBusinessId().equals(businessId)) throw new IllegalArgumentException("다른 사업체의 채점 기록입니다.");
    return g;
  }

  private EasyGradingPhoto ownPhoto(Long businessId, Long id) {
    EasyGradingPhoto p = photos.findById(id == null ? -1L : id)
        .orElseThrow(() -> new IllegalArgumentException("사진을 찾을 수 없습니다."));
    if (!p.getGrading().getBusiness().getBusinessId().equals(businessId)) throw new IllegalArgumentException("다른 사업체의 사진입니다.");
    return p;
  }

  private EasyClass classOf(Long businessId, Long classId) {
    if (classId == null) return null;
    EasyClass c = classes.findById(classId).orElseThrow(() -> new IllegalArgumentException("반을 찾을 수 없습니다."));
    if (!c.getBusiness().getBusinessId().equals(businessId)) throw new IllegalArgumentException("다른 사업체의 반입니다.");
    return c;
  }

  private EasyBusiness requireBusiness(Long businessId) {
    return businesses.findById(businessId == null ? -1L : businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
  }

  private EasyStudent requireStudent(Long studentId) {
    return students.findById(studentId == null ? -1L : studentId)
        .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
  }

  private static HomeworkCheck check(String v) {
    if (!notBlank(v)) return null;
    try {
      return HomeworkCheck.valueOf(v.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("숙제 확인은 DONE(다 해 옴)·PARTIAL(일부)·NOT_DONE(안 해 옴) 중 하나입니다.");
    }
  }

  private static LocalDate date(String v) {
    if (!notBlank(v)) throw new IllegalArgumentException("날짜(YYYY-MM-DD)가 필요합니다.");
    try {
      return LocalDate.parse(v.strip());
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("날짜는 YYYY-MM-DD 형식입니다.");
    }
  }

  /** 앞 바이트로 이미지 종류를 판단한다(확장자·Content-Type은 믿지 않는다). */
  static String imageExtension(byte[] b) {
    if (startsWith(b, JPEG)) return "jpg";
    if (startsWith(b, PNG)) return "png";
    if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
    return null;
  }

  private static boolean startsWith(byte[] b, byte[] magic) {
    return b.length >= magic.length && Arrays.equals(Arrays.copyOf(b, magic.length), magic);
  }

  private static String contentType(String ext) {
    return switch (ext) {
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      default -> "image/jpeg";
    };
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String clip(String s, int max) {
    if (s == null) return null;
    String t = s.strip();
    if (t.isEmpty()) return null;
    return t.length() > max ? t.substring(0, max) : t;
  }
}
