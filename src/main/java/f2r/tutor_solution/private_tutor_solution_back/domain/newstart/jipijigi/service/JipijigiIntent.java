package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

/**
 * 지피지기 질문 의도. RAG이 "무엇을 아느냐"가 아니라 "지금 무엇을 원하느냐"로 근거를 고르게 하기 위한 분기점이다.
 *
 * <ul>
 *   <li>{@link #POLICY} — 제도·기준·기간 같은 사실 문의. 공식 자료가 주제다.</li>
 *   <li>{@link #EXPERIENCE} — 같은 상황의 실제 경험이 문의다.</li>
 *   <li>{@link #ACTION} — 행동 방법 문의가 주이고 본인이 겪은 사건을 보고하지 않았다.</li>
 *   <li>{@link #EXPERIENCE_ACTION} — 본인이 겪은 사건(통보·연체·민원 등)을 보고하고 지금 무엇을 할지 묻는 형태.
 *       회원 화면의 대표 질문이 이 유형이다.</li>
 *   <li>{@link #MIXED} — 제도 사실과 경험/행동이 섞여 있고 본인 사건 보고는 없다. 공식 우선 유지(보수적 기본값).</li>
 * </ul>
 */
public enum JipijigiIntent {
    POLICY,
    EXPERIENCE,
    ACTION,
    EXPERIENCE_ACTION,
    MIXED;

    /**
     * 사례·경험 근거를 공식 자료보다 앞에 배치할 의도인지. {@link #POLICY}와 {@link #MIXED}는
     * 기존 공식 우선 순서를 유지한다(기존 계약 테스트 포함).
     */
    public boolean experienceFirst() {
        return this == EXPERIENCE || this == ACTION || this == EXPERIENCE_ACTION;
    }
}
