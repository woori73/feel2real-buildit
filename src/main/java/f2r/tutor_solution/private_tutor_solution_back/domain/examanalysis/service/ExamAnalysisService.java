package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto.ExamAnalysisResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto.ExamAnalysisSearchCondition;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.entity.ExamAnalysis;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.repository.ExamAnalysisRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExamAnalysisService {

  private static final String RAW_FILE_TYPE = "UNKNOWN";
  private static final long STORAGE_OWNER_ID = 0L;

  private final ExamAnalysisRepository examAnalysisRepository;
  private final SubjectRepository subjectRepository;
  private final FileStorage fileStorage;
  private final UploadSizeGuard uploadSizeGuard;

  @Transactional(readOnly = true)
  public List<ExamAnalysisResponse> searchPublic(ExamAnalysisSearchCondition condition) {
    return examAnalysisRepository.searchPublic(
            blankToNull(condition.keyword()),
            blankToNull(condition.schoolName()),
            condition.grade(),
            blankToNull(condition.semester()),
            blankToNull(condition.examType()),
            condition.subjectId())
        .stream()
        .map(ExamAnalysisResponse::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ExamAnalysisResponse> latestPublic(int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 12));
    return examAnalysisRepository.findLatestPublic(PageRequest.of(0, safeLimit)).stream()
        .map(ExamAnalysisResponse::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public ExamAnalysisResponse getPublic(Long id) {
    return ExamAnalysisResponse.of(getPublicEntity(id));
  }

  @Transactional(readOnly = true)
  public String getPublicHtmlContent(Long id) {
    ExamAnalysis analysis = getPublicEntity(id);
    try {
      byte[] bytes = fileStorage.readPage(analysis.getStorageKey(), RAW_FILE_TYPE, 1);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 파일을 찾을 수 없습니다.", e);
    }
  }

  @Transactional(readOnly = true)
  public List<ExamAnalysisResponse> listAdmin() {
    return examAnalysisRepository.findAdminList().stream()
        .map(ExamAnalysisResponse::of)
        .toList();
  }

  @Transactional
  public ExamAnalysisResponse create(String title, String schoolName, Integer grade, String semester, String examType,
      Integer subjectId, boolean visible, MultipartFile file) {
    validateFile(file, true);
    Subject subject = getSubject(subjectId);
    String storageKey = saveFile(file);
    ExamAnalysis analysis = ExamAnalysis.builder()
        .title(required(title, "제목"))
        .schoolName(required(schoolName, "학교명"))
        .grade(validGrade(grade))
        .semester(required(semester, "학기"))
        .examType(required(examType, "시험 종류"))
        .subject(subject)
        .originalFileName(originalFileName(file))
        .storageKey(storageKey)
        .fileSize(file.getSize())
        .visible(visible)
        .build();
    return ExamAnalysisResponse.of(examAnalysisRepository.save(analysis));
  }

  @Transactional
  public ExamAnalysisResponse update(Long id, String title, String schoolName, Integer grade, String semester,
      String examType, Integer subjectId, boolean visible, MultipartFile file) {
    ExamAnalysis analysis = getAdminEntity(id);
    Subject subject = getSubject(subjectId);
    analysis.updateMetadata(
        required(title, "제목"),
        required(schoolName, "학교명"),
        validGrade(grade),
        required(semester, "학기"),
        required(examType, "시험 종류"),
        subject,
        visible);
    if (file != null && !file.isEmpty()) {
      validateFile(file, true);
      String newStorageKey = saveFile(file);
      String oldStorageKey = analysis.replaceFile(originalFileName(file), newStorageKey, file.getSize());
      fileStorage.delete(oldStorageKey, RAW_FILE_TYPE);
    }
    return ExamAnalysisResponse.of(analysis);
  }

  @Transactional
  public ExamAnalysisResponse updateVisibility(Long id, boolean visible) {
    ExamAnalysis analysis = getAdminEntity(id);
    analysis.setVisible(visible);
    return ExamAnalysisResponse.of(analysis);
  }

  @Transactional
  public void softDelete(Long id) {
    ExamAnalysis analysis = getAdminEntity(id);
    analysis.softDelete();
  }

  @Transactional
  public void hardDelete(Long id) {
    ExamAnalysis analysis = getAdminEntity(id);
    String storageKey = analysis.getStorageKey();
    examAnalysisRepository.delete(analysis);
    fileStorage.delete(storageKey, RAW_FILE_TYPE);
  }

  private ExamAnalysis getPublicEntity(Long id) {
    return examAnalysisRepository.findPublicById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 분석 자료를 찾을 수 없습니다."));
  }

  private ExamAnalysis getAdminEntity(Long id) {
    return examAnalysisRepository.findAdminById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 자료를 찾을 수 없습니다."));
  }

  private Subject getSubject(Integer subjectId) {
    if (subjectId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "과목을 선택해주세요.");
    }
    return subjectRepository.findById(subjectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 과목입니다."));
  }

  private String saveFile(MultipartFile file) {
    try {
      return fileStorage.saveRaw(STORAGE_OWNER_ID, originalFileName(file), file.getInputStream(), file.getSize());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "분석 파일 저장에 실패했습니다.", e);
    }
  }

  private void validateFile(MultipartFile file, boolean required) {
    if (file == null || file.isEmpty()) {
      if (required) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HTML 파일을 업로드해주세요.");
      return;
    }
    uploadSizeGuard.requireWithinDocumentLimit(file);
    String name = originalFileName(file).toLowerCase();
    if (!name.endsWith(".html") && !name.endsWith(".htm")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HTML 파일만 업로드할 수 있습니다.");
    }
  }

  private String originalFileName(MultipartFile file) {
    String filename = file.getOriginalFilename();
    return (filename == null || filename.isBlank()) ? "analysis.html" : filename;
  }

  private Integer validGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년은 1~12 사이 숫자로 입력해주세요.");
    }
    return grade;
  }

  private String required(String value, String label) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을(를) 입력해주세요.");
    }
    return trimmed;
  }

  private String blankToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
