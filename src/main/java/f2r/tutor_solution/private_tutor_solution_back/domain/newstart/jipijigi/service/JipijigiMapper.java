package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.*;

import java.util.List;
import java.util.stream.Collectors;

/** entity → admin DTO 변환 모음. */
public final class JipijigiMapper {

    private JipijigiMapper() {
    }

    public static JipijigiQuestionResponse toDto(JipijigiQuestion q) {
        return JipijigiQuestionResponse.builder()
                .questionId(q.getQuestionId())
                .articleId(q.getArticleId())
                .clubId(q.getClubId())
                .menuId(q.getMenuId())
                .boardName(q.getBoardName())
                .title(q.getTitle())
                .url(q.getUrl())
                .writtenAt(q.getWrittenAt())
                .body(q.getBody())
                .bodyStatus(q.getBodyStatus() == null ? null : q.getBodyStatus().name())
                .commentCount(q.getCommentCount())
                .collectedAt(q.getCollectedAt())
                .build();
    }

    public static JipijigiCommandResponse toDto(JipijigiCommand c) {
        return JipijigiCommandResponse.builder()
                .commandId(c.getCommandId())
                .instruction(c.getInstruction())
                .commandType(c.getCommandType() == null ? null : c.getCommandType().name())
                .status(c.getStatus() == null ? null : c.getStatus().name())
                .resultSummary(c.getResultSummary())
                .errorMessage(c.getErrorMessage())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }

    public static JipijigiDraftResponse toDto(JipijigiDraft d) {
        return toDto(d, null);
    }

    public static JipijigiDraftResponse toDto(JipijigiDraft d, String targetBoardName) {
        return JipijigiDraftResponse.builder()
                .draftId(d.getDraftId())
                .commandId(d.getCommandId())
                .questionId(d.getQuestionId())
                .draftType(d.getDraftType() == null ? null : d.getDraftType().name())
                .title(d.getTitle())
                .body(d.getBody())
                .status(d.getStatus() == null ? null : d.getStatus().name())
                .targetBoardId(d.getTargetBoardId())
                .targetBoardName(targetBoardName)
                .publishedUrl(d.getPublishedUrl())
                .rejectReason(d.getRejectReason())
                .createdBy(d.getCreatedBy())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    public static JipijigiResearchTraceResponse toTraceDto(JipijigiResearchTrace t) {
        return JipijigiResearchTraceResponse.builder()
                .traceId(t.getTraceId())
                .stepNo(t.getStepNo())
                .sourceClass(t.getSourceClass() == null ? null : t.getSourceClass().name())
                .refType(t.getRefType() == null ? null : t.getRefType().name())
                .refId(t.getRefId())
                .refUrl(t.getRefUrl())
                .excerpt(t.getExcerpt())
                .note(t.getNote())
                .searchedAt(t.getSearchedAt())
                .build();
    }

    public static List<JipijigiResearchTraceResponse> toTraceDtos(List<JipijigiResearchTrace> traces) {
        return traces.stream().map(JipijigiMapper::toTraceDto).collect(Collectors.toList());
    }

    public static JipijigiStatusLogResponse toDto(JipijigiDraftStatusLog l) {
        return JipijigiStatusLogResponse.builder()
                .logId(l.getLogId())
                .fromStatus(l.getFromStatus())
                .toStatus(l.getToStatus())
                .actor(l.getActor())
                .reason(l.getReason())
                .createdAt(l.getCreatedAt())
                .build();
    }

    public static JipijigiTargetBoardResponse toDto(JipijigiTargetBoard b) {
        return JipijigiTargetBoardResponse.builder()
                .boardRowId(b.getBoardRowId())
                .clubId(b.getClubId())
                .menuId(b.getMenuId())
                .boardName(b.getBoardName())
                .cafeName(b.getCafeName())
                .enabled(b.isEnabled())
                .postable(b.isPostable())
                .memo(b.getMemo())
                .build();
    }
}
