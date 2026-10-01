package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * NewStart 오픈 전 감사회원 사전자격명부 — {@code newstart_thanks_roster}.
 *
 * <p>{@code newstart_member_grants}(실제 {@code THANKS_MEMBER} 실행 권한)와 완전히 분리된, "Cafe 에서
 * 감사멤버였음"이라는 사실만 보존하는 스냅샷이다. 오픈 전에는 대응하는 NewStart 계정이 없으므로
 * {@code matchStatus}는 {@code UNMATCHED}이고 {@code linkedUserId}/{@code linkedProviderUserId}는 NULL로
 * 남긴다 — 닉네임만으로 계정을 추측 매칭하지 않는다.
 *
 * <p>{@code cafeMemberTag}는 Cafe Studio 화면 식별자(멤버 태그)이며 네이버 OAuth
 * {@code provider_user_id} 와 동일하지 않다. {@code gradeChangedAt}은 "최근 등급변경일"이지 최초
 * 감사멤버 승인일이 아니다 — 신규 감사멤버 판정에 쓰지 않는다.
 */
@Entity
@Table(name = "newstart_thanks_roster",
    uniqueConstraints = @UniqueConstraint(name = "uk_newstart_thanks_roster_snapshot_tag",
        columnNames = {"snapshot_date", "cafe_member_tag"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartThanksRoster extends BaseEntity {

  public static final String SOURCE_NAVER_CAFE = "NAVER_CAFE";
  public static final String MATCH_UNMATCHED = "UNMATCHED";
  public static final String MATCH_MATCHED = "MATCHED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "roster_id")
  private Long rosterId;

  @Column(name = "cafe_member_tag", nullable = false, length = 32)
  private String cafeMemberTag;

  /**
   * Cafe Studio 화면에서 마스킹되어 노출되는 네이버 ID 접두부(예: {@code ssgw}). 전체 네이버 ID가
   * 아니며 네이버 OAuth {@code provider_user_id} 와도 다르다. 같은 접두부가 여러 네이버 ID에 존재할 수
   * 있으므로 단독 확정 식별자가 아니며, 이 값만으로 {@code THANKS_MEMBER} 를 자동 부여하지 않는다 —
   * 본인확인 보조정보로만 쓴다(V1016).
   */
  @Column(name = "naver_id_prefix", length = 64)
  private String naverIdPrefix;

  @Column(name = "nickname", nullable = false, length = 50)
  private String nickname;

  @Column(name = "grade", nullable = false, length = 30)
  private String grade;

  @Column(name = "grade_changed_at")
  private LocalDate gradeChangedAt;

  @Column(name = "member_level", nullable = false)
  private int memberLevel;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "snapshot_date", nullable = false)
  private LocalDate snapshotDate;

  @Column(name = "source_url", length = 512)
  private String sourceUrl;

  @Column(name = "source_file", length = 255)
  private String sourceFile;

  @Column(name = "match_status", nullable = false, length = 20)
  private String matchStatus;

  @Column(name = "linked_user_id")
  private Long linkedUserId;

  @Column(name = "linked_provider_user_id", length = 64)
  private String linkedProviderUserId;

  @Builder
  public NewStartThanksRoster(String cafeMemberTag, String naverIdPrefix, String nickname, String grade,
      LocalDate gradeChangedAt, int memberLevel, String source, LocalDate snapshotDate, String sourceUrl,
      String sourceFile, String matchStatus) {
    this.cafeMemberTag = cafeMemberTag;
    this.naverIdPrefix = naverIdPrefix;
    this.nickname = nickname;
    this.grade = grade;
    this.gradeChangedAt = gradeChangedAt;
    this.memberLevel = memberLevel;
    this.source = source;
    this.snapshotDate = snapshotDate;
    this.sourceUrl = sourceUrl;
    this.sourceFile = sourceFile;
    this.matchStatus = matchStatus;
  }

  /**
   * 운영자 승인을 거쳐 현재 네이버 OAuth 회원과 1:1 로 연결한다(V1017 UNIQUE가 DB에서도 1:1 을 강제).
   * 이미 연결된 행에는 쓰지 않는다 — 호출 전에 {@link #isLinked()} 로 확인한다.
   */
  public void link(Long userId, String providerUserId) {
    this.linkedUserId = userId;
    this.linkedProviderUserId = providerUserId;
    this.matchStatus = MATCH_MATCHED;
  }

  public boolean isLinked() {
    return linkedUserId != null || MATCH_MATCHED.equals(matchStatus);
  }
}
