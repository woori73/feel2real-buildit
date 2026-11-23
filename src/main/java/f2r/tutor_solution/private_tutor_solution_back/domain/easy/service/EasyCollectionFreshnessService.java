package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionCatalog.Site;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매일 자료 수집(반자동) 점검 — 은행 입출금·카드 사용·카드 매출이 언제까지 장부에 들어왔는지 계산한다.
 *
 * <p>은행·카드 사이트 로그인은 사람이 직접 해야 해서 무인 수집은 하지 않는다. 대신 화면을 열 때마다 오늘 날짜 기준으로
 * "아직 안 들어온 자료"를 알려 주고, 원장이 로그인한 뒤 Claude에게 그대로 붙여 넣을 요청 문구(빠진 기간 포함)를 만든다.
 * 읽기만 하며 장부·수집 기록을 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectionFreshnessService {

  /** 한 번도 수집하지 않았을 때 처음 가져올 기간(일). */
  static final int FIRST_DAYS = 30;
  /** 마지막 수집 뒤 이 일수 이상 지나면 "늦음"으로 빨갛게 보인다. */
  static final int LATE_DAYS = 3;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** DONE_TODAY=오늘 넣음, DUE=오늘 넣을 차례, LATE=여러 날 밀림, NEVER=한 번도 안 넣음. */
  public enum Status { DONE_TODAY, DUE, LATE, NEVER }

  /** 연결 하나의 수집 상태(직접 로그인 수집으로 들어온 연결만). */
  public record Snapshot(String dataset, String siteCode, LocalDate collectedOn, LocalDate latestTxnDate) {}

  /** 화면·Claude에 보여 줄 자료 종류별 한 줄. */
  public record Row(String dataset, String label, Status status, String statusText,
      LocalDate collectedOn, LocalDate latestTxnDate, Integer daysSince, String siteName,
      String siteUrl, LocalDate suggestFrom, LocalDate suggestTo, String prompt) {}

  /** 점검 대상 자료 종류(장부에 넣는 세 가지). */
  enum Kind {
    BANK_ACCOUNT("은행 입출금", "은행"),
    CARD_PURCHASE("카드 사용(비용)", "카드사"),
    CARD_SALES("카드 매출(단말기)", "여신금융협회 카드매출 조회");

    final String label;
    final String defaultSite;

    Kind(String label, String defaultSite) {
      this.label = label;
      this.defaultSite = defaultSite;
    }
  }

  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionRawRecordRepository rawRepository;

  /** 사업체의 자료 종류별 수집 상태. today는 한국 날짜. */
  public List<Row> check(Long businessId, LocalDate today) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    List<Snapshot> snapshots = new ArrayList<>();
    for (EasyCollectionConnection connection
        : connectionRepository.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(businessId)) {
      snapshotOf(connection).ifPresent(snapshots::add);
    }
    return evaluate(snapshots, today);
  }

  /** 오늘 한국 날짜 기준 점검. */
  public List<Row> checkToday(Long businessId) {
    return check(businessId, LocalDate.now(KstTime.ZONE));
  }

  private Optional<Snapshot> snapshotOf(EasyCollectionConnection connection) {
    Optional<EasyCollectionRun> latest =
        runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(connection.getConnectionId());
    if (latest.isEmpty()
        || !EasyCollectionIngestService.SITE_ADAPTER_CODE.equals(latest.get().getAdapterCode())) {
      return Optional.empty();
    }
    return rawRepository.findFirstByRun_RunIdOrderByRawIdAsc(latest.get().getRunId())
        .flatMap(raw -> readSiteAndDataset(raw.getRawPayload()))
        .map(pair -> new Snapshot(pair[1], pair[0],
            connection.getLastSuccessAt() == null ? null
                : KstTime.toKst(connection.getLastSuccessAt()).toLocalDate(),
            rawRepository.findLatestOccurredOn(connection.getConnectionId())));
  }

  /** 원본 JSON에서 [기관 코드, 자료 종류]를 읽는다. 읽을 수 없으면 비어 있다. */
  static Optional<String[]> readSiteAndDataset(String rawPayload) {
    if (rawPayload == null) {
      return Optional.empty();
    }
    try {
      JsonNode node = MAPPER.readTree(rawPayload);
      String site = node.path("site").asText("");
      String dataset = node.path("dataset").asText("");
      return site.isEmpty() || dataset.isEmpty() ? Optional.empty()
          : Optional.of(new String[] {site, dataset});
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /** 순수 계산 — 자료 종류마다 가장 최근에 수집한 연결을 기준으로 상태와 요청 문구를 만든다. */
  static List<Row> evaluate(List<Snapshot> snapshots, LocalDate today) {
    List<Row> rows = new ArrayList<>();
    for (Kind kind : Kind.values()) {
      Optional<Snapshot> best = snapshots.stream()
          .filter(s -> kind.name().equals(s.dataset()) && s.collectedOn() != null)
          .max(Comparator.comparing(Snapshot::collectedOn));
      rows.add(best.map(s -> rowFor(kind, s, today)).orElseGet(() -> neverRow(kind, today)));
    }
    return rows;
  }

  private static Row rowFor(Kind kind, Snapshot snapshot, LocalDate today) {
    Optional<Site> site = EasySiteCollectionCatalog.find(snapshot.siteCode());
    String siteName = site.map(Site::name).orElse(kind.defaultSite);
    String siteUrl = site.map(s -> s.businessUrl() != null ? s.businessUrl() : s.url()).orElse(null);
    int days = (int) Math.max(0, ChronoUnit.DAYS.between(snapshot.collectedOn(), today));
    Status status = days == 0 ? Status.DONE_TODAY : days < LATE_DAYS ? Status.DUE : Status.LATE;
    String text = switch (status) {
      case DONE_TODAY -> "오늘 넣었습니다";
      case DUE -> days + "일 전에 넣었습니다 — 오늘 자료를 넣어 주세요";
      default -> days + "일째 안 들어왔습니다";
    };
    // 마지막 거래일부터 다시 받는다(겹치는 거래는 장부에서 중복으로 걸러진다). 거래일을 모르면 마지막 수집일부터.
    LocalDate from = snapshot.latestTxnDate() != null ? snapshot.latestTxnDate() : snapshot.collectedOn();
    if (from.isAfter(today)) {
      from = today;
    }
    return new Row(kind.name(), kind.label, status, text, snapshot.collectedOn(),
        snapshot.latestTxnDate(), days, siteName, siteUrl, from, today,
        prompt(siteName, kind, from, today));
  }

  private static Row neverRow(Kind kind, LocalDate today) {
    LocalDate from = today.minusDays(FIRST_DAYS);
    return new Row(kind.name(), kind.label, Status.NEVER, "아직 한 번도 넣지 않았습니다", null, null,
        null, null, null, from, today, prompt(null, kind, from, today));
  }

  /** siteName이 없으면(처음 수집) 기관 이름 없이 만든다. */
  static String prompt(String siteName, Kind kind, LocalDate from, LocalDate to) {
    return "F2R 장부에 " + kind.label + (siteName == null ? "" : "(" + siteName + ")") + " " + from + "~" + to
        + " 거래를 수집해 줘. 로그인은 내가 직접 했어.";
  }
}
