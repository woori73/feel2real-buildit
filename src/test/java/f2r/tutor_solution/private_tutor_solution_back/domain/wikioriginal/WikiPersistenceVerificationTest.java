package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostAttachment;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostAttachmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginalDocType;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Entity 매핑 + JPQL 검증(임베디드 H2, Flyway 비활성 · Hibernate 스키마 생성).
 * 운영은 MySQL + Flyway(V956). 이 테스트는 로컬에 MySQL이 없어도 다음을 보장한다.
 *   · WikiExamOriginal 매핑/저장/soft-delete/grade·visible 필터
 *   · community_posts scope 분리 — wiki 게시판 글이 커뮤니티 목록에 새지 않는다
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:wikiverify;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class WikiPersistenceVerificationTest {

  @Autowired WikiExamOriginalRepository originalRepository;
  @Autowired WikiAnalysisMaterialRepository analysisRepository;
  @Autowired CommunityPostRepository postRepository;
  @Autowired CommunityPostAttachmentRepository attachmentRepository;
  @Autowired TestEntityManager em;

  private static final List<BoardType> COMMUNITY = List.of(BoardType.FREE, BoardType.QNA, BoardType.SUBJECT);
  private static final List<BoardType> WIKI = List.of(BoardType.EXAM_REVIEW, BoardType.VARIANT, BoardType.MATERIAL);

  @Test
  @DisplayName("WikiExamOriginal: 저장 후 grade·visible·deleted 필터가 동작한다")
  void wikiExamOriginal_mapsAndFilters() {
    originalRepository.save(original("고3 문제지", 3, WikiExamOriginalDocType.QUESTION, true));
    originalRepository.save(original("고2 문제지", 2, WikiExamOriginalDocType.QUESTION, true));
    WikiExamOriginal hidden = originalRepository.save(original("숨김", 3, WikiExamOriginalDocType.ANSWER, false));
    WikiExamOriginal deleted = original("삭제", 3, WikiExamOriginalDocType.ANSWER, true);
    deleted.softDelete();
    originalRepository.save(deleted);
    em.flush();
    em.clear();

    assertThat(originalRepository.findPublicByGrade(3)).extracting(WikiExamOriginal::getTitle)
        .containsExactly("고3 문제지");                    // 숨김·삭제 제외, grade 필터
    assertThat(originalRepository.findPublicByGrade(null)).hasSize(2);   // 전체 공개
    assertThat(originalRepository.findAdminList()).hasSize(3);           // deleted만 제외
    assertThat(originalRepository.findPublicById(hidden.getWikiExamOriginalId())).isEmpty();
  }

  @Test
  @DisplayName("community_posts scope: wiki 게시판 글은 커뮤니티 목록(FREE/QNA/SUBJECT)에서 제외된다")
  void communityPosts_scopeIsolation() {
    User author = em.persist(User.builder()
        .loginId("verif-user").password("x").name("검증").nickname("검증").role(Role.STUDENT).build());
    postRepository.save(post(author, BoardType.FREE, "자유글"));
    postRepository.save(post(author, BoardType.QNA, "질문글"));
    postRepository.save(post(author, BoardType.EXAM_REVIEW, "9평 후기"));
    postRepository.save(post(author, BoardType.VARIANT, "변형문제 세트"));
    postRepository.save(post(author, BoardType.MATERIAL, "어휘 정리 공유"));
    em.flush();
    em.clear();

    // scope=community (기존 /community 전체 탭): wiki 게시판 글이 보이지 않는다
    assertThat(postRepository.searchLatest(COMMUNITY, null, null, null))
        .extracting(CommunityPost::getTitle)
        .containsExactlyInAnyOrder("자유글", "질문글");

    // scope=wiki: wiki 게시판 글만
    assertThat(postRepository.searchLatest(WIKI, null, null, null))
        .extracting(CommunityPost::getTitle)
        .containsExactlyInAnyOrder("9평 후기", "변형문제 세트", "어휘 정리 공유");

    // scope=wiki + boardType 지정: 그 게시판만
    assertThat(postRepository.searchLatest(WIKI, BoardType.EXAM_REVIEW, null, null))
        .extracting(CommunityPost::getTitle)
        .containsExactly("9평 후기");
  }

  @Test
  @DisplayName("WikiAnalysisMaterial: 저장 후 grade·visible·deleted 필터가 동작한다")
  void wikiAnalysisMaterial_mapsAndFilters() {
    analysisRepository.save(analysis("고3 분석", 3, true));
    analysisRepository.save(analysis("고2 분석", 2, true));
    WikiAnalysisMaterial hidden = analysisRepository.save(analysis("숨김", 3, false));
    WikiAnalysisMaterial deleted = analysis("삭제", 3, true);
    deleted.softDelete();
    analysisRepository.save(deleted);
    em.flush();
    em.clear();

    assertThat(analysisRepository.findPublicByGrade(3)).extracting(WikiAnalysisMaterial::getTitle)
        .containsExactly("고3 분석");
    assertThat(analysisRepository.findPublicByGrade(null)).hasSize(2);
    assertThat(analysisRepository.findAdminList()).hasSize(3);
    assertThat(analysisRepository.findPublicById(hidden.getWikiAnalysisMaterialId())).isEmpty();
  }

  @Test
  @DisplayName("CommunityPostAttachment: 게시글 첨부 매핑 + post별·visible 필터")
  void communityPostAttachment_mapsAndFilters() {
    User author = em.persist(User.builder()
        .loginId("att-user").password("x").name("첨부").nickname("첨부").role(Role.STUDENT).build());
    CommunityPost post = postRepository.save(post(author, BoardType.VARIANT, "변형문제"));
    attachmentRepository.save(attachment(post, author, "k1", true));
    attachmentRepository.save(attachment(post, author, "k2", true));
    attachmentRepository.save(attachment(post, author, "k3", false));
    em.flush();
    em.clear();

    assertThat(attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(post.getPostId()))
        .hasSize(2);
    assertThat(attachmentRepository.countByPost_PostIdAndVisibleTrue(post.getPostId())).isEqualTo(2);
  }

  private WikiAnalysisMaterial analysis(String title, int grade, boolean visible) {
    return WikiAnalysisMaterial.builder()
        .title(title).grade(grade).year(2026).semester("1학기").examType("기말").subject("영어")
        .contentType("application/pdf").originalFileName("f.pdf").storageKey("k/" + title)
        .fileSize(10L).visible(visible).build();
  }

  private CommunityPostAttachment attachment(CommunityPost post, User uploader, String key, boolean visible) {
    CommunityPostAttachment a = CommunityPostAttachment.builder()
        .post(post).uploader(uploader).originalFileName("f.pdf").contentType("application/pdf")
        .fileSize(4L).storageKey(key).build();
    if (!visible) a.hide();
    return a;
  }

  private WikiExamOriginal original(String title, int grade, WikiExamOriginalDocType type, boolean visible) {
    return WikiExamOriginal.builder()
        .title(title).grade(grade).examLabel("2026 9월 모의고사").docType(type)
        .originalFileName("f.pdf").storageKey("k/" + title).fileSize(10L).visible(visible)
        .build();
  }

  private CommunityPost post(User author, BoardType board, String title) {
    return CommunityPost.builder()
        .author(author).boardType(board).subject(null).title(title)
        .contentJson("{}").contentHtml("<p>x</p>").build();
  }
}
