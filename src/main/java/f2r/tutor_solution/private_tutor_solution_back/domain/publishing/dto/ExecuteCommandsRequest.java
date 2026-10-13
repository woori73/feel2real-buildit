package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import java.util.List;

/** 편집 명령 실행 요청. */
public record ExecuteCommandsRequest(
    List<PublishingCommand> commands
) {
}
