package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.MaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadedFileIngestor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProblemGeneratorMaterialServiceTest {

  @Mock ProblemGeneratorMaterialRepository repository;
  @Mock UserRepository userRepository;
  @Mock UploadedFileIngestor ingestor;
  @Mock FileStorage fileStorage;

  @Test
  @DisplayName("given 이미지 자료 when 개인 업로드 then PRIVATE row를 저장한다")
  void upload_private_material() {
    // given
    ProblemGeneratorMaterialService service = service();
    User student = user(1L, Role.STUDENT);
    MockMultipartFile file = new MockMultipartFile("files", "school.png", "image/png", new byte[]{1, 2});
    when(userRepository.findById(1L)).thenReturn(Optional.of(student));
    when(ingestor.store(file, 1L)).thenReturn(new UploadedFileIngestor.StoredFile("k", "IMAGE", 123));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(fileStorage.pageCount("k", "IMAGE")).thenReturn(1);

    // when
    List<MaterialResponse> responses = service.uploadPrivate(principal(1L, Role.STUDENT), List.of(file), "압구정중 기출");

    // then
    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).visibility()).isEqualTo(MaterialVisibility.PRIVATE);
    verify(repository).save(any(ProblemGeneratorMaterial.class));
  }

  @Test
  @DisplayName("given 학생 when PUBLIC 업로드 then 거부한다")
  void student_cannot_upload_public() {
    // given
    ProblemGeneratorMaterialService service = service();
    User student = user(1L, Role.STUDENT);
    MockMultipartFile file = new MockMultipartFile("files", "school.png", "image/png", new byte[]{1});
    when(userRepository.findById(1L)).thenReturn(Optional.of(student));

    // when / then
    assertThatThrownBy(() -> service.upload(principal(1L, Role.STUDENT), List.of(file), "공용", MaterialVisibility.PUBLIC))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 타인 PRIVATE 자료 when read then 403으로 막는다")
  void private_material_is_owner_only() {
    // given
    ProblemGeneratorMaterialService service = service();
    ProblemGeneratorMaterial material = material(user(2L, Role.STUDENT), MaterialVisibility.PRIVATE);
    when(repository.findById(10L)).thenReturn(Optional.of(material));

    // when / then
    assertThatThrownBy(() -> service.loadAccessible(principal(1L, Role.STUDENT), 10L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given PUBLIC 자료 when 타 사용자 read then 접근 가능하다")
  void public_material_is_accessible() {
    // given
    ProblemGeneratorMaterialService service = service();
    ProblemGeneratorMaterial material = material(user(2L, Role.ADMIN), MaterialVisibility.PUBLIC);
    when(repository.findById(10L)).thenReturn(Optional.of(material));

    // when / then
    assertThat(service.loadAccessible(principal(1L, Role.STUDENT), 10L)).isSameAs(material);
  }

  @Test
  @DisplayName("given 다중 파일 긴 제목 when upload then 파생 제목은 150자로 잘린다")
  void multi_file_derived_title_is_truncated() {
    // given
    ProblemGeneratorMaterialService service = service();
    User admin = user(9L, Role.ADMIN);
    MockMultipartFile f1 = new MockMultipartFile("files", "a-very-long-file-name.png", "image/png", new byte[]{1});
    MockMultipartFile f2 = new MockMultipartFile("files", "b.png", "image/png", new byte[]{1});
    String title = "가".repeat(150);
    when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
    when(ingestor.store(any(), eq(9L))).thenReturn(new UploadedFileIngestor.StoredFile("k", "IMAGE", 1));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // when
    List<MaterialResponse> responses = service.uploadAdminPublic(principal(9L, Role.ADMIN), List.of(f1, f2), title);

    // then
    assertThat(responses).hasSize(2);
    verify(repository, times(2)).save(argThat(material -> material.getTitle().length() <= 150));
  }

  private ProblemGeneratorMaterialService service() {
    return new ProblemGeneratorMaterialService(repository, userRepository, ingestor, fileStorage);
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal(Long id, Role role) {
    return new JwtTokenProvider.AuthenticatedPrincipal(id, role.name().toLowerCase(), role.name());
  }

  private User user(Long id, Role role) {
    User user = User.builder()
        .loginId(role.name().toLowerCase() + id)
        .password("pw")
        .name("사용자")
        .nickname("닉")
        .role(role)
        .build();
    ReflectionTestUtils.setField(user, "userId", id);
    return user;
  }

  private ProblemGeneratorMaterial material(User owner, MaterialVisibility visibility) {
    return ProblemGeneratorMaterial.builder()
        .uploadedBy(owner)
        .title("자료")
        .originalFileName("a.png")
        .storageKey("k")
        .fileType("IMAGE")
        .fileSize(1)
        .visibility(visibility)
        .build();
  }
}
