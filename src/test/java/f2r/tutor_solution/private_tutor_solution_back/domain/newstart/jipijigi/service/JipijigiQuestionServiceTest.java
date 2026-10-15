package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.JipijigiQuestionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 질문 수집 저장 검증. articleId 없는 항목은 거부(발명 방지)하고,
 * 본문 없는 수집 실패는 UNREADABLE로 남기며, 닉네임/연락처는 저장 전에 마스킹한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("지피지기 Agent 질문 수집/개인정보 마스킹")
class JipijigiQuestionServiceTest {

    @Mock
    private JipijigiQuestionRepository questionRepository;

    private JipijigiQuestionRequest req(String articleId, String title, String body) {
        return JipijigiQuestionRequest.builder()
                .articleId(articleId).title(title).body(body)
                .url("https://cafe.naver.com/31312978/" + articleId)
                .nicknames(List.of("통큰이", "김철수"))
                .build();
    }

    @Test
    @DisplayName("articleId가 없으면 저장하지 않는다")
    void requiresArticleId() {
        JipijigiQuestionService service = new JipijigiQuestionService(questionRepository);

        assertThatThrownBy(() -> service.upsert(req("  ", "제목", "본문")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("닉네임·연락처는 저장 전 마스킹되고 clubid/menuid 기본값을 채운다")
    void masksPersonalInfoOnIngest() {
        JipijigiQuestionService service = new JipijigiQuestionService(questionRepository);
        when(questionRepository.findByClubIdAndArticleId(31312978L, "6001")).thenReturn(Optional.empty());
        when(questionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsert(req("6001", "통큰이 님 질문입니다", "김철수입니다. 010-1234-5678으로 연락 주세요"));

        ArgumentCaptor<JipijigiQuestion> captor = ArgumentCaptor.forClass(JipijigiQuestion.class);
        verify(questionRepository).save(captor.capture());
        JipijigiQuestion saved = captor.getValue();
        assertThat(saved.getTitle()).doesNotContain("통큰이").contains("(작성자)");
        assertThat(saved.getBody()).doesNotContain("김철수").doesNotContain("010-1234-5678")
                .contains("(연락처 마스킹)");
        assertThat(saved.getClubId()).isEqualTo(31312978L);
        assertThat(saved.getMenuId()).isEqualTo(1L);
        assertThat(saved.getBodyStatus()).isEqualTo(JipijigiQuestion.BodyStatus.READABLE);
        assertThat(saved.getCollectedAt()).isNotNull();
    }

    @Test
    @DisplayName("본문을 읽지 못한 수집건은 UNREADABLE로 보관한다")
    void unreadableIsPreserved() {
        JipijigiQuestionService service = new JipijigiQuestionService(questionRepository);
        when(questionRepository.findByClubIdAndArticleId(31312978L, "6002")).thenReturn(Optional.empty());
        when(questionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsert(req("6002", "제목만 확인됨", null));

        ArgumentCaptor<JipijigiQuestion> captor = ArgumentCaptor.forClass(JipijigiQuestion.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getBodyStatus()).isEqualTo(JipijigiQuestion.BodyStatus.UNREADABLE);
    }

    @Test
    @DisplayName("같은 articleId 재수집은 새 행을 만들지 않는다 (upsert)")
    void upsertUpdatesExisting() {
        JipijigiQuestionService service = new JipijigiQuestionService(questionRepository);
        JipijigiQuestion existing = JipijigiQuestion.builder()
                .questionId(55L).clubId(31312978L).articleId("6003").title("옛 제목").body("옛 본문")
                .url("https://cafe.naver.com/31312978/6003")
                .bodyStatus(JipijigiQuestion.BodyStatus.READABLE).menuId(1L)
                .build();
        when(questionRepository.findByClubIdAndArticleId(31312978L, "6003")).thenReturn(Optional.of(existing));
        when(questionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsert(req("6003", "새 제목", "새 본문"));

        ArgumentCaptor<JipijigiQuestion> captor = ArgumentCaptor.forClass(JipijigiQuestion.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getQuestionId()).isEqualTo(55L);
        assertThat(captor.getValue().getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("댓글 JSON에도 닉네임이 남지 않는다")
    void commentsJsonMasked() {
        JipijigiQuestionService service = new JipijigiQuestionService(questionRepository);
        when(questionRepository.findByClubIdAndArticleId(31312978L, "6004")).thenReturn(Optional.empty());
        when(questionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        JipijigiQuestionRequest r = req("6004", "제목", "본문");
        r.setCommentsJson("[{\"nick\":\"통큰이\",\"text\":\"도와주세요\"}]");

        service.upsert(r);

        ArgumentCaptor<JipijigiQuestion> captor = ArgumentCaptor.forClass(JipijigiQuestion.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getCommentsJson()).doesNotContain("통큰이");
    }
}
