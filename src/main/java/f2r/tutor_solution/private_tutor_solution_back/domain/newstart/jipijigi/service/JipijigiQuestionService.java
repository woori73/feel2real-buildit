package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.JipijigiQuestionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 실제 수집한 카페 질문 저장. 질문을 만들어내지 않는다 — 수집된 값만 upsert하고,
 * 본문을 읽지 못한 항목은 bodyStatus=UNREADABLE로 그대로 보관한다(제목으로 내용을 추정하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class JipijigiQuestionService {

    /** 새출발기금 부실차주 라이브러리 clubid — 2026-09-23 카페 주소에서 실측 확인한 값. */
    public static final long BORROWER_CLUB_ID = 31312978L;
    /** 질문게시판 menuid — 수집된 article_json의 boardId 값으로 실측(2026-09-23). */
    public static final Long QUESTION_BOARD_MENU_ID = 1L;

    private final JipijigiQuestionRepository questionRepository;

    @Transactional
    public List<JipijigiQuestion> upsertAll(List<JipijigiQuestionRequest> requests) {
        List<JipijigiQuestion> out = new ArrayList<>();
        if (requests == null) return out;
        for (JipijigiQuestionRequest req : requests) {
            out.add(upsert(req));
        }
        return out;
    }

    @Transactional
    public JipijigiQuestion upsert(JipijigiQuestionRequest req) {
        if (req.getArticleId() == null || req.getArticleId().isBlank()) {
            throw new IllegalArgumentException("articleId 필수 — 실재 게시글만 저장할 수 있습니다.");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new IllegalArgumentException("title 필수");
        }
        long clubId = req.getClubId() != null ? req.getClubId() : BORROWER_CLUB_ID;
        JipijigiQuestion entity = questionRepository.findByClubIdAndArticleId(clubId, req.getArticleId())
                .orElseGet(JipijigiQuestion::new);
        boolean isNew = entity.getQuestionId() == null;

        List<String> nicknames = req.getNicknames();
        entity.setClubId(clubId);
        entity.setArticleId(req.getArticleId());
        entity.setMenuId(req.getMenuId() != null ? req.getMenuId() : QUESTION_BOARD_MENU_ID);
        entity.setBoardName(req.getBoardName());
        entity.setTitle(truncate(JipijigiSanitizer.mask(req.getTitle(), nicknames), 300));
        entity.setUrl(truncate(req.getUrl() == null ? cafeUrl(clubId, entity.getMenuId(), req.getArticleId()) : req.getUrl(), 500));
        entity.setWrittenAt(req.getWrittenAt());
        entity.setBody(JipijigiSanitizer.mask(req.getBody(), nicknames));
        entity.setBodyStatus(unreadable(req) ? JipijigiQuestion.BodyStatus.UNREADABLE : JipijigiQuestion.BodyStatus.READABLE);
        entity.setCommentCount(req.getCommentCount());
        entity.setCommentsJson(JipijigiSanitizer.mask(req.getCommentsJson(), nicknames));
        if (isNew || entity.getCollectedAt() == null) {
            entity.setCollectedAt(LocalDateTime.now());
        }
        return questionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<JipijigiQuestion> recent(int limit) {
        return questionRepository.findAllByOrderByWrittenAtDesc(PageRequest.of(0, Math.max(1, limit))).getContent();
    }

    @Transactional(readOnly = true)
    public List<JipijigiQuestion> all() {
        return questionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<JipijigiQuestion> byId(Long id) {
        return questionRepository.findById(id);
    }

    private boolean unreadable(JipijigiQuestionRequest req) {
        if ("UNREADABLE".equalsIgnoreCase(String.valueOf(req.getBodyStatus()))) return true;
        return req.getBody() == null || req.getBody().isBlank();
    }

    private static String cafeUrl(long clubId, Long menuId, String articleId) {
        long menu = menuId != null ? menuId : QUESTION_BOARD_MENU_ID;
        return "https://cafe.naver.com/" + clubId + "/" + articleId + "?menuId=" + menu;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
