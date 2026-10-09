package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * RAG 원본 내보내기 쿼리 회귀 테스트 — 실제 DB(H2) 대상.
 *
 * <p>2026-10-09: JPA Stream 반환 쿼리가 운영(Hibernate 7 + MySQL) 스트리밍 경로에서
 * 응답을 중간에 끊는 현상이 확인되어 페이지네이션으로 교체. 이 테스트는 교체된
 * 페이지 쿼리가 실제 DB에서 동작함을 보장한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:newstartexport;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("RAG 내보내기 페이지 쿼리")
class NewstartKnowledgeItemExportQueryTest {

    @Autowired
    private NewstartKnowledgeItemRepository repository;

    private NewstartKnowledgeItem item(long knowledgeId, SourceType type, KnowledgeStatus status) {
        return NewstartKnowledgeItem.builder()
                .knowledgeId(knowledgeId)
                .sourceType(type)
                .status(status)
                .category("cat")
                .title("title " + knowledgeId)
                .content("content " + knowledgeId)
                .version(1)
                .createdAt(LocalDateTime.of(2026, 10, 9, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 9, 0, 0))
                .build();
    }

    @Test
    @DisplayName("ACTIVE + sourceType 필터 + knowledgeId 오름차순 페이지네이션")
    void pageByStatusAndSourceType() {
        // given
        repository.save(item(3L, SourceType.CAFE_COMMENT, KnowledgeStatus.ACTIVE));
        repository.save(item(1L, SourceType.CAFE_COMMENT, KnowledgeStatus.ACTIVE));
        repository.save(item(2L, SourceType.CAFE_COMMENT, KnowledgeStatus.ACTIVE));
        repository.save(item(9L, SourceType.CAFE_COMMENT, KnowledgeStatus.DRAFT)); // 제외
        repository.save(item(8L, SourceType.CAFE_ARTICLE, KnowledgeStatus.ACTIVE)); // 제외

        // when — 페이지 크기 2로 2페이지 순회
        List<Long> ids = new ArrayList<>();
        int page = 0;
        while (true) {
            Page<NewstartKnowledgeItem> slice = repository.pageByStatusAndSourceType(
                    KnowledgeStatus.ACTIVE, SourceType.CAFE_COMMENT, PageRequest.of(page, 2));
            slice.getContent().forEach(i -> ids.add(i.getKnowledgeId()));
            if (!slice.hasNext()) {
                break;
            }
            page++;
        }

        // then — ACTIVE CAFE_COMMENT 3건이 knowledgeId 오름차순으로
        assertThat(ids).containsExactly(1L, 2L, 3L);
    }
}
