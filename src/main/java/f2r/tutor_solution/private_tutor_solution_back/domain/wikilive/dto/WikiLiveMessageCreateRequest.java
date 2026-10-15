package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto;

/** WIKI LIVE 한 줄 메시지 작성 요청. 실제 검증(빈 값·길이·escape)은 서버 서비스가 최종 수행한다. */
public record WikiLiveMessageCreateRequest(String message) {}
