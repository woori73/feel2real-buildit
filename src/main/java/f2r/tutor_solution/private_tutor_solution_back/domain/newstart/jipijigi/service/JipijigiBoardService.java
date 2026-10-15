package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.JipijigiTargetBoardRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiTargetBoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 게시 allowlist 관리. Agent가 게시할 수 있는 게시판은 이 테이블의 enabled 행뿐이고,
 * menuid를 NAVER에서 실제 확인하지 않은 행(menu_id NULL)은 게시 대상에서 제외된다.
 */
@Service
@RequiredArgsConstructor
public class JipijigiBoardService {

    /** 지피지기 Agent 전용 게시판 이름. 실 clubid/menuid는 운영자 확인 후 입력한다. */
    public static final String AGENT_BOARD_NAME = "지피지기 agent";

    private final JipijigiTargetBoardRepository boardRepository;

    @Transactional(readOnly = true)
    public List<JipijigiTargetBoard> all() {
        return boardRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<JipijigiTargetBoard> postableAgentBoard() {
        return boardRepository.findByEnabledTrue().stream()
                .filter(b -> AGENT_BOARD_NAME.equalsIgnoreCase(b.getBoardName()))
                .filter(JipijigiTargetBoard::isPostable)
                .findFirst();
    }

    /** 초안의 기본 대상 게시판(enabled면 menuid 확인 여부 무관하게 참조용으로 붙인다). */
    @Transactional(readOnly = true)
    public Optional<JipijigiTargetBoard> enabledAgentBoard() {
        return boardRepository.findByEnabledTrue().stream()
                .filter(b -> AGENT_BOARD_NAME.equalsIgnoreCase(b.getBoardName()))
                .findFirst();
    }

    @Transactional
    public JipijigiTargetBoard save(JipijigiTargetBoardRequest req, String actor) {
        if (req.getClubId() == null) throw new IllegalArgumentException("clubId 필수");
        if (req.getBoardName() == null || req.getBoardName().isBlank()) throw new IllegalArgumentException("boardName 필수");
        if (req.getMenuId() != null && req.getMenuId() <= 0) {
            throw new IllegalArgumentException("menuId는 NAVER에서 확인한 실제 값(양수)만 저장할 수 있습니다. 미확인이면 비워 두세요.");
        }
        JipijigiTargetBoard board = (req.getMenuId() != null
                ? boardRepository.findByClubIdAndMenuId(req.getClubId(), req.getMenuId())
                : Optional.<JipijigiTargetBoard>empty())
                .orElseGet(JipijigiTargetBoard::new);
        boolean isNew = board.getBoardRowId() == null;
        if (!isNew && req.getMenuId() == null) {
            throw new IllegalArgumentException("기존 allowlist 행의 menuId를 지우는 수정은 지원하지 않습니다.");
        }
        board.setClubId(req.getClubId());
        board.setMenuId(req.getMenuId());
        board.setBoardName(req.getBoardName());
        board.setCafeName(req.getCafeName() == null ? "새출발기금 부실차주 라이브러리" : req.getCafeName());
        board.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
        board.setMemo(req.getMemo());
        board.setUpdatedBy(actor);
        if (isNew) board.setCreatedBy(actor);
        return boardRepository.save(board);
    }

    @Transactional
    public JipijigiTargetBoard setEnabled(Long boardRowId, boolean enabled, String actor) {
        JipijigiTargetBoard board = boardRepository.findById(boardRowId)
                .orElseThrow(() -> new IllegalArgumentException("allowlist 행 없음: " + boardRowId));
        board.setEnabled(enabled);
        board.setUpdatedBy(actor);
        return boardRepository.save(board);
    }
}
