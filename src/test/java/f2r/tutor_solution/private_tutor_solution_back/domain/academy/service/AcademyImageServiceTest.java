package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos.EditImageRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos.EditOperation;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyImageAsset;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyImageAssetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 이미지 편집: 슬롯 이미지 재사용 · 원본 등록 · 편집본 별도 키 저장 · 작업본 불변 · 실패 시 무변경. */
class AcademyImageServiceTest {

  AcademyImageAssetRepository assets = mock(AcademyImageAssetRepository.class);
  AcademyService academyService = mock(AcademyService.class);
  AcademyWorkingCopyService workingCopy = mock(AcademyWorkingCopyService.class);
  FileStorage storage = mock(FileStorage.class);
  PhotoCraftImageService photoCraft = mock(PhotoCraftImageService.class);
  AcademyImageService service;
  AuthenticatedPrincipal tutor = mock(AuthenticatedPrincipal.class);
  Academy academy = mock(Academy.class);

  EditImageRequest brighten = new EditImageRequest(
      null, "principal", java.util.List.of(new EditOperation("brightness", Map.of("value", 30))), true);

  @BeforeEach
  void setUp() throws Exception {
    service = new AcademyImageService(assets, academyService, workingCopy, storage, photoCraft);
    User user = mock(User.class);
    when(user.getUserId()).thenReturn(7L);
    when(academy.getTutor()).thenReturn(user);
    when(academy.getAcademyId()).thenReturn(10L);
    when(academy.imageSlotKey(HeroMediaSlot.PRINCIPAL)).thenReturn("7/live.jpg");
    when(academyService.requireMyAcademy(tutor)).thenReturn(academy);
    when(workingCopy.stagedMedia(tutor)).thenReturn(Map.of());
    when(storage.readPage("7/live.jpg", "IMAGE", 1)).thenReturn(new byte[] {1, 2, 3});
    when(storage.saveImage(anyLong(), anyString(), any(), anyString())).thenReturn("7/edit.jpg");
    when(assets.save(any())).thenAnswer(i -> {
      AcademyImageAsset a = i.getArgument(0);
      if (a.getId() == null) ReflectionTestUtils.setField(a, "id", a.getVersion() * 100L);
      return a;
    });
    when(assets.findFirstByAcademyIdAndSlotKeyAndEditedPathOrderByVersionDesc(anyLong(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(assets.findFirstByAcademyIdAndSlotKeyAndOriginalPathAndEditedPathIsNullOrderByVersionAsc(
        anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
    // PhotoCraft 성공: 출력 파일 생성
    when(photoCraft.editImage(anyString(), anyString(), anyList())).thenAnswer(i -> {
      Path out = Path.of((String) i.getArgument(1));
      Files.createDirectories(out.getParent());
      Files.write(out, new byte[] {9, 9});
      return PhotoCraftImageService.EditResult.success(out.toString());
    });
  }

  @Test
  void edit_usesCurrentSlotImage_registersOriginal_savesEditedUnderNewKey_andNeverTouchesWorkingCopy()
      throws Exception {
    // given: 슬롯에 공개 이미지 7/live.jpg
    // when
    var res = service.editImage(tutor, brighten);
    // then
    assertThat(res.stagedForPreview()).isTrue();
    assertThat(res.imageUrl()).isEqualTo("/api/tutor/academy/images/flow/" + res.assetId() + "/edited");
    ArgumentCaptor<AcademyImageAsset> saved = ArgumentCaptor.forClass(AcademyImageAsset.class);
    verify(assets, org.mockito.Mockito.times(2)).save(saved.capture());
    AcademyImageAsset edited = saved.getAllValues().get(1);
    assertThat(edited.getOriginalPath()).isEqualTo("7/live.jpg");
    assertThat(edited.getEditedPath()).isEqualTo("7/edit.jpg");
    verify(storage).saveImage(eq(7L), eq(HeroMediaSlot.PRINCIPAL.filenameHint() + "-edit"), eq(new byte[] {9, 9}), eq("jpg"));
    verify(storage, never()).delete(anyString(), anyString());
    verify(workingCopy, never()).stageMedia(any(), anyLong(), anyString(), any());
  }

  @Test
  void edit_prefersStagedWorkingCopyImageOverLiveOne() throws Exception {
    // given
    when(workingCopy.stagedMedia(tutor)).thenReturn(Map.of("principal", "7/staged.jpg"));
    when(storage.readPage("7/staged.jpg", "IMAGE", 1)).thenReturn(new byte[] {4});
    // when
    service.editImage(tutor, brighten);
    // then
    verify(storage).readPage("7/staged.jpg", "IMAGE", 1);
    verify(storage, never()).readPage("7/live.jpg", "IMAGE", 1);
  }

  @Test
  void edit_whenSlotEmpty_isRejected() {
    // given
    when(academy.imageSlotKey(HeroMediaSlot.PRINCIPAL)).thenReturn(null);
    // when/then
    assertThatThrownBy(() -> service.editImage(tutor, brighten)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void edit_whenPhotoCraftFails_savesNothingAndKeepsOriginal() throws Exception {
    // given
    org.mockito.Mockito.doReturn(PhotoCraftImageService.EditResult.failure("boom"))
        .when(photoCraft).editImage(anyString(), anyString(), anyList());
    // when/then
    assertThatThrownBy(() -> service.editImage(tutor, brighten)).isInstanceOf(RuntimeException.class);
    verify(storage, never()).saveImage(anyLong(), anyString(), any(), anyString());
    verify(assets, never()).save(any());
    verify(storage, never()).delete(anyString(), anyString());
  }

  @Test
  void edit_whenDbSaveFails_deletesOrphanEditedFile() throws Exception {
    // given
    org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(assets).save(any());
    // when/then
    assertThatThrownBy(() -> service.editImage(tutor, brighten)).isInstanceOf(IllegalStateException.class);
    verify(storage).delete("7/edit.jpg", "IMAGE");
  }

  @Test
  void edit_animatedGif_isRejectedBeforeRunningPhotoCraft() {
    // given
    when(academy.imageSlotKey(HeroMediaSlot.PRINCIPAL)).thenReturn("7/anim.GIF");
    // when/then
    assertThatThrownBy(() -> service.editImage(tutor, brighten)).isInstanceOf(IllegalArgumentException.class);
    verify(photoCraft, never()).editImage(anyString(), anyString(), anyList());
  }

  @Test
  void edit_otherAcademyAsset_isBlocked() {
    // given: 학원 99 소유 자산
    AcademyImageAsset foreign = AcademyImageAsset.createOriginal(99L, "principal", "9/x.jpg");
    when(assets.findById(5L)).thenReturn(Optional.of(foreign));
    EditImageRequest req = new EditImageRequest(
        5L, "principal", java.util.List.of(new EditOperation("brightness", Map.of("value", 10))), true);
    // when/then
    assertThatThrownBy(() -> service.editImage(tutor, req)).isInstanceOf(SecurityException.class);
    verify(photoCraft, never()).editImage(anyString(), anyString(), anyList());
  }

  @Test
  void readAssetImage_otherAcademy_isBlocked_andOwnReturnsBytes() throws Exception {
    // given
    AcademyImageAsset own = AcademyImageAsset.createOriginal(10L, "principal", "7/live.jpg");
    AcademyImageAsset foreign = AcademyImageAsset.createOriginal(99L, "principal", "9/x.jpg");
    when(assets.findById(1L)).thenReturn(Optional.of(own));
    when(assets.findById(2L)).thenReturn(Optional.of(foreign));
    // when/then
    assertThat(service.readAssetImage(tutor, 1L, false).bytes()).containsExactly(1, 2, 3);
    assertThatThrownBy(() -> service.readAssetImage(tutor, 2L, true)).isInstanceOf(SecurityException.class);
  }

  @Test
  void hasSourceImage_reflectsStagedClearAndLive() {
    // given: 공개 이미지 있음
    assertThat(service.hasSourceImage(tutor, "principal")).isTrue();
    // given: 비우기 예약
    when(workingCopy.stagedMedia(tutor)).thenReturn(Map.of("principal", ""));
    // then
    assertThat(service.hasSourceImage(tutor, "principal")).isFalse();
  }
}
