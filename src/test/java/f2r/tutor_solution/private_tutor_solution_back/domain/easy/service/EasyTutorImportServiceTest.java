package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class EasyTutorImportServiceTest {

  @Mock EasyTutorAccessService accessService;
  @Mock EasyCsvFileAdapter csvFileAdapter;
  @Mock EasyImportService importService;
  @Mock EasyImportBatchRepository batchRepository;
  @Mock EasyImportRowRepository rowRepository;
  @Mock EasyAccountRepository accountRepository;

  private EasyTutorImportService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    service = new EasyTutorImportService(accessService, csvFileAdapter, importService,
        batchRepository, rowRepository, accountRepository);
    principal = new JwtTokenProvider.AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
  }

  @Test
  void otherAcademyBusinessIsRejectedBeforeBatchLookup() {
    when(accessService.requireAllowedBusiness(principal, 2L))
        .thenThrow(new AccessDeniedException("연결되지 않은 사업체입니다."));

    assertThatThrownBy(() -> service.detail(principal, 2L, 99L))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(batchRepository, importService);
  }

  @Test
  void batchIdCannotCrossTheAllowedBusinessBoundary() {
    EasyBusiness business = EasyBusiness.builder()
        .code("A").name("A 학원").active(true).sortOrder(1).build();
    org.springframework.test.util.ReflectionTestUtils.setField(business, "businessId", 1L);
    when(accessService.requireAllowedBusiness(principal, 1L)).thenReturn(business);
    when(batchRepository.findByBatchIdAndBusiness_BusinessId(99L, 1L))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> service.detail(principal, 1L, 99L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("사업체에 속한 배치");
  }
}
