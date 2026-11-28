package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
class CodiCalls {
  private final UnifiedAssistantService permissions;
  private final AcademyAssistantAdapter academy;
  private final CodiVoiceProvider provider;
  private final CodiCallRepository attempts;
  @Value("${codi.voice.enabled:false}") private boolean enabled;
  @Value("${codi.voice.test-numbers:}") private List<String> numbers;
  record Request(UUID requestId, Long academyId, String number, String task, boolean approved) {}
  record Result(String status, String summary) {}

  Result start(AuthenticatedPrincipal actor, Request request) {
    scope(actor, request.academyId());
    permissions.requireWrites();
    if (!request.approved() || request.requestId() == null || request.task() == null || request.task().isBlank()
        || request.task().length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수신 번호와 500자 이내의 통화 목적을 확인해 주세요.");
    String number = normalize(request.number());
    String task = request.task().strip();
    if (!numbers.contains(number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전화 연결 시험에 등록한 번호를 선택해 주세요.");
    String id = request.requestId().toString();
    String fingerprint = AssistantJson.hash(number + "\n" + task);
    try {
      attempts.claim(new CodiCallAttempt(id, actor.userId(), request.academyId(), fingerprint));
    } catch (DuplicateKeyException duplicate) {
      var existing = owned(id, actor.userId(), request.academyId());
      if (!fingerprint.equals(existing.getFingerprint())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 통화에 사용한 요청입니다.");
      return result(existing);
    }
    CodiVoiceProvider.Start call;
    try {
      call = provider.start(number, task);
    } catch (IOException | IllegalArgumentException e) {
      return new Result("unconfirmed", "");
    }
    attempts.acknowledge(id, call.conversation(), call.status());
    return new Result(call.status(), "");
  }

  Result status(AuthenticatedPrincipal actor, Long academyId, UUID id) {
    scope(actor, academyId);
    return result(owned(id.toString(), actor.userId(), academyId));
  }
  private CodiCallAttempt owned(String id, Long user, Long academyId) {
    return attempts.find(id, user, academyId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
  private Result result(CodiCallAttempt attempt) {
    if (attempt.getConversationId() == null) return new Result(attempt.getStatus(), "");
    try {
      return provider.status(attempt.getConversationId());
    } catch (IOException | IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "통화 상태를 조회할 수 없습니다.");
    }
  }
  private void scope(AuthenticatedPrincipal actor, Long academyId) {
    permissions.actor(actor);
    if (academyId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학원을 선택해 주세요.");
    academy.owner(actor, academyId);
    if (!enabled || !provider.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이 환경에는 AI 전화 회선이 연결되어 있지 않습니다.");
  }
  static String normalize(String value) {
    String number = Objects.toString(value, "").replaceAll("[ ()-]", "");
    if (number.startsWith("0")) number = "+82" + number.substring(1);
    if (!number.matches("\\+82(?:10[0-9]{8}|2[0-9]{7,8}|[3-6][1-5][0-9]{7,8}|70[0-9]{8})"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "국내 휴대폰 또는 유선 번호를 확인해 주세요.");
    return number;
  }
}
