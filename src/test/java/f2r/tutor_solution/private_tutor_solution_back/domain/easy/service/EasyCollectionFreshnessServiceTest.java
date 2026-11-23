package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService.Snapshot;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionFreshnessService.Status;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EasyCollectionFreshnessServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

  private Row row(List<Row> rows, String dataset) {
    return rows.stream().filter(r -> r.dataset().equals(dataset)).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("given 수집 기록이 없음 when 점검 then 세 자료 모두 NEVER이고 최근 30일 요청 문구가 나온다")
  void neverCollected() {
    // when
    List<Row> rows = EasyCollectionFreshnessService.evaluate(List.of(), TODAY);
    // then
    assertThat(rows).extracting(Row::dataset).containsExactly("BANK_ACCOUNT", "CARD_PURCHASE", "CARD_SALES");
    assertThat(rows).allMatch(r -> r.status() == Status.NEVER);
    Row bank = row(rows, "BANK_ACCOUNT");
    assertThat(bank.suggestFrom()).isEqualTo(TODAY.minusDays(30));
    assertThat(bank.suggestTo()).isEqualTo(TODAY);
    assertThat(bank.prompt()).isEqualTo("F2R 장부에 은행 입출금 2026-10-24~2026-11-23 거래를 수집해 줘. 로그인은 내가 직접 했어.");
    assertThat(bank.siteUrl()).isNull();
  }

  @Test
  @DisplayName("given 오늘·2일 전·5일 전 수집 when 점검 then 오늘 넣음·오늘 넣을 차례·늦음으로 나뉜다")
  void statusByDaysSince() {
    // given
    List<Snapshot> snaps = List.of(
        new Snapshot("BANK_ACCOUNT", "KB_BANK", TODAY, TODAY.minusDays(1)),
        new Snapshot("CARD_PURCHASE", "SHINHAN_CARD", TODAY.minusDays(2), TODAY.minusDays(3)),
        new Snapshot("CARD_SALES", "CREFIA_CARDSALES", TODAY.minusDays(5), null));
    // when
    List<Row> rows = EasyCollectionFreshnessService.evaluate(snaps, TODAY);
    // then
    assertThat(row(rows, "BANK_ACCOUNT").status()).isEqualTo(Status.DONE_TODAY);
    assertThat(row(rows, "CARD_PURCHASE").status()).isEqualTo(Status.DUE);
    assertThat(row(rows, "CARD_PURCHASE").statusText()).contains("2일 전");
    assertThat(row(rows, "CARD_SALES").status()).isEqualTo(Status.LATE);
    assertThat(row(rows, "CARD_SALES").daysSince()).isEqualTo(5);
  }

  @Test
  @DisplayName("given 마지막 거래일을 앎 when 점검 then 그날부터 오늘까지 다시 받으라고 하고 기관 이름·기업용 주소를 쓴다")
  void suggestsFromLatestTxnDateWithSiteName() {
    // given
    List<Snapshot> snaps = List.of(new Snapshot("BANK_ACCOUNT", "KB_BANK", TODAY.minusDays(4), TODAY.minusDays(6)));
    // when
    Row bank = row(EasyCollectionFreshnessService.evaluate(snaps, TODAY), "BANK_ACCOUNT");
    // then
    assertThat(bank.suggestFrom()).isEqualTo(TODAY.minusDays(6));
    assertThat(bank.siteName()).isEqualTo("KB국민은행");
    assertThat(bank.siteUrl()).isEqualTo("https://obiz.kbstar.com/");
    assertThat(bank.prompt()).startsWith("F2R 장부에 은행 입출금(KB국민은행) 2026-11-17~2026-11-23");
  }

  @Test
  @DisplayName("given 같은 자료를 두 계좌로 수집 when 점검 then 가장 최근에 넣은 쪽을 기준으로 한다")
  void picksMostRecentConnection() {
    // given
    List<Snapshot> snaps = List.of(
        new Snapshot("BANK_ACCOUNT", "SHINHAN_BANK", TODAY.minusDays(9), TODAY.minusDays(9)),
        new Snapshot("BANK_ACCOUNT", "KB_BANK", TODAY.minusDays(1), TODAY.minusDays(1)));
    // when
    Row bank = row(EasyCollectionFreshnessService.evaluate(snaps, TODAY), "BANK_ACCOUNT");
    // then
    assertThat(bank.siteName()).isEqualTo("KB국민은행");
    assertThat(bank.status()).isEqualTo(Status.DUE);
  }

  @Test
  @DisplayName("given 목록에 없는 기관 코드·거래일 없음 when 점검 then 기본 이름과 마지막 수집일부터를 쓴다")
  void unknownSiteFallsBack() {
    // given
    List<Snapshot> snaps = List.of(new Snapshot("CARD_PURCHASE", "OLD_CODE", TODAY.minusDays(3), null));
    // when
    Row card = row(EasyCollectionFreshnessService.evaluate(snaps, TODAY), "CARD_PURCHASE");
    // then
    assertThat(card.siteName()).isEqualTo("카드사");
    assertThat(card.siteUrl()).isNull();
    assertThat(card.suggestFrom()).isEqualTo(TODAY.minusDays(3));
  }

  @Test
  @DisplayName("given 원본 JSON when 기관·자료 읽기 then site·dataset을 읽고 깨진 값은 비어 있다")
  void readSiteAndDataset() {
    assertThat(EasyCollectionFreshnessService.readSiteAndDataset("{\"site\":\"KB_BANK\",\"dataset\":\"BANK_ACCOUNT\"}"))
        .get().satisfies(p -> assertThat(p).containsExactly("KB_BANK", "BANK_ACCOUNT"));
    assertThat(EasyCollectionFreshnessService.readSiteAndDataset("{\"site\":\"KB_BANK\"}")).isEmpty();
    assertThat(EasyCollectionFreshnessService.readSiteAndDataset("not json")).isEmpty();
    assertThat(EasyCollectionFreshnessService.readSiteAndDataset(null)).isEmpty();
  }

  @Test
  @DisplayName("given 직접 로그인 수집 연결과 다른 방식 연결 when check then 직접 로그인 수집만 반영하고 수집 시각은 한국 날짜로 센다")
  void checkUsesOnlySiteCollectionRuns() {
    // given
    var connections = mock(EasyCollectionConnectionRepository.class);
    var runs = mock(EasyCollectionRunRepository.class);
    var raws = mock(EasyCollectionRawRecordRepository.class);
    var site = mock(EasyCollectionConnection.class);
    var csv = mock(EasyCollectionConnection.class);
    when(site.getConnectionId()).thenReturn(1L);
    when(csv.getConnectionId()).thenReturn(2L);
    when(site.getLastSuccessAt()).thenReturn(KstTime.fromKst(LocalDateTime.of(2026, 11, 22, 21, 0)));
    when(connections.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(7L)).thenReturn(List.of(site, csv));
    var siteRun = mock(EasyCollectionRun.class);
    when(siteRun.getAdapterCode()).thenReturn(EasyCollectionIngestService.SITE_ADAPTER_CODE);
    when(siteRun.getRunId()).thenReturn(11L);
    var csvRun = mock(EasyCollectionRun.class);
    when(csvRun.getAdapterCode()).thenReturn("CSV_FILE");
    when(runs.findFirstByConnection_ConnectionIdOrderByRunIdDesc(1L)).thenReturn(Optional.of(siteRun));
    when(runs.findFirstByConnection_ConnectionIdOrderByRunIdDesc(2L)).thenReturn(Optional.of(csvRun));
    var raw = mock(EasyCollectionRawRecord.class);
    when(raw.getRawPayload()).thenReturn("{\"site\":\"KB_BANK\",\"dataset\":\"BANK_ACCOUNT\"}");
    when(raws.findFirstByRun_RunIdOrderByRawIdAsc(11L)).thenReturn(Optional.of(raw));
    when(raws.findLatestOccurredOn(1L)).thenReturn(LocalDate.of(2026, 11, 21));
    var service = new EasyCollectionFreshnessService(connections, runs, raws);
    // when
    List<Row> rows = service.check(7L, TODAY);
    // then
    Row bank = row(rows, "BANK_ACCOUNT");
    assertThat(bank.collectedOn()).isEqualTo(LocalDate.of(2026, 11, 22));
    assertThat(bank.status()).isEqualTo(Status.DUE);
    assertThat(bank.latestTxnDate()).isEqualTo(LocalDate.of(2026, 11, 21));
    assertThat(row(rows, "CARD_PURCHASE").status()).isEqualTo(Status.NEVER);
    assertThatThrownBy(() -> service.check(null, TODAY)).isInstanceOf(IllegalArgumentException.class);
  }
}
