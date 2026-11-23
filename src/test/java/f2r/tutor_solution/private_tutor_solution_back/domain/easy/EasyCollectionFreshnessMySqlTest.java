package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService.SiteRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService.SiteRowsRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 매일 수집 점검이 실제 MySQL의 수집 기록(직접 로그인 수집)을 읽는지 확인한다(롤백 트랜잭션, 거래는 지어낸 것). */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("매일 자료 수집 점검 — MySQL")
class EasyCollectionFreshnessMySqlTest {

  @Autowired EasySiteCollectionService siteCollection;
  @Autowired EasyCollectionFreshnessService freshness;
  @Autowired EasyBusinessRepository businesses;

  @Test
  @DisplayName("given 오늘 은행 거래 2건을 직접 로그인 수집으로 저장 when 점검 then 은행은 오늘 넣음·마지막 거래일이 보이고 카드는 아직 안 넣음")
  void bankSavedTodayIsFresh() {
    // given
    Long biz = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    LocalDate today = LocalDate.now(KstTime.ZONE);
    LocalDate from = today.minusDays(5);
    var rows = List.of(
        new SiteRow(from.plusDays(1).toString(), "IN", 300_000L, "합성 입금자", "타행이체", null, 1_300_000L, null),
        new SiteRow(from.plusDays(3).toString(), "OUT", 50_000L, "합성 거래처", "자동이체", null, 1_250_000L, null));
    siteCollection.save(new AuthenticatedPrincipal(1L, "admin", "ADMIN"), new SiteRowsRequest(biz, "KB_BANK",
        "BANK_ACCOUNT", "freshness-test", from.toString(), today.toString(), 2, 350_000L,
        "freshness-mysql-0001", rows));

    // when
    List<Row> result = freshness.check(biz, today);

    // then
    Row bank = result.stream().filter(r -> r.dataset().equals("BANK_ACCOUNT")).findFirst().orElseThrow();
    assertThat(bank.status()).isEqualTo(Status.DONE_TODAY);
    assertThat(bank.latestTxnDate()).isEqualTo(from.plusDays(3));
    assertThat(bank.siteName()).isEqualTo("KB국민은행");
    assertThat(result.stream().filter(r -> r.dataset().equals("CARD_PURCHASE")).findFirst().orElseThrow().status())
        .isEqualTo(Status.NEVER);
  }
}
