package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Round;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store.Info;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 예비고1 디지털 수능 영어 모의고사 6부 — 고2 실전 독해 모의고사(18~45번 28문항, 45분).
 * 자료 형식·검증·응시 흐름은 2부와 같고 자료 폴더(classpath:prehigh-part6/rNN/)와 회차 수만 다르므로
 * 2부 저장소를 그대로 쓴다. 자료는 scripts/prehigh/build_round.py로 만든다.
 */
@Service
public class PreHighPart6Store {
  /** 실린 회차 수. 회차를 더 실으면 이 숫자만 올린다. */
  public static final int ROUND_COUNT = 1;

  private final PreHighPart2Store rounds;

  public PreHighPart6Store(ObjectMapper json) {
    this.rounds = new PreHighPart2Store(json, "prehigh-part6/", ROUND_COUNT, "6부");
  }

  public List<Info> list() {
    return rounds.list();
  }

  /** 회차 자료(그림은 base64). 없는 회차면 null. */
  public Round get(int number) {
    return rounds.get(number);
  }

  /** 내려받기용 문제지 PDF. 없는 회차면 null. */
  public byte[] paper(int number) {
    return rounds.paper(number);
  }
}
