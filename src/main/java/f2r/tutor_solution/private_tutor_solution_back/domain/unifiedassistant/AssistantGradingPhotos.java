package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyGradingPhotoTarget;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class AssistantGradingPhotos {
  private static final int PAGE_SIZE = 50;
  private final UnifiedAssistantService permissions;
  private final EasyTutorAccessService access;
  private final EasyGradingRecordRepository gradings;
  private final EasyLessonRecordService records;

  public record Targets(List<EasyGradingPhotoTarget> targets, Long nextBefore) {}

  public Targets list(AuthenticatedPrincipal p, Long businessId, Long before) {
    permissions.actor(p);
    access.requireAllowedBusiness(p, businessId);
    if (before != null && before < 1) throw new IllegalArgumentException("채점 목록의 다음 페이지를 확인해 주세요.");
    var rows = gradings.photoTargets(businessId, before, PageRequest.of(0, PAGE_SIZE + 1));
    if (rows.size() <= PAGE_SIZE) return new Targets(rows, null);
    return new Targets(List.copyOf(rows.subList(0, PAGE_SIZE)), rows.get(PAGE_SIZE - 1).gradingId());
  }

  public EasyLessonRecordService.PhotoView attach(AuthenticatedPrincipal p, Long businessId, Long studentId,
                                                   Long gradingId, String kind, MultipartFile file) throws IOException {
    permissions.actor(p);
    access.requireAllowedBusiness(p, businessId);
    permissions.requireWrites();
    if (!gradings.existsByGradingIdAndBusiness_BusinessIdAndStudent_StudentId(gradingId, businessId, studentId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "선택한 학생의 채점 기록을 찾을 수 없습니다.");
    }
    if (file.isEmpty()) throw new IllegalArgumentException("사진 파일이 비어 있습니다.");
    if (file.getSize() > EasyLessonRecordService.MAX_PHOTO_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 10MB 이하만 올릴 수 있습니다.");
    }
    return records.addPhoto(businessId, gradingId, file.getBytes(), kind, p.userId());
  }
}
