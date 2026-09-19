// 신구중2 8회 문제지 문항 데이터 — Q1~Q30 (L5)
// answer: 1~5 = ①~⑤. passage/cond/table/choices 는 FORMAT MASTER(4회) 클래스로 렌더.
export const QUESTIONS = [
  {
    num: 1,
    prompt: "다음은 L5 Word List에 실린 영어 뜻풀이이다. 빈칸에 들어갈 말로 가장 적절한 것은?",
    passage: {kind: "box", html: "n. something made by an artist, like a drawing, painting, or <span class='blank'></span>"},
    choices: ["ballet", "cinema", "sculpture", "canvas", "station"],
    answer: 3,
  },
  {
    num: 2,
    prompt: "다음은 L5 Word List에 실린 영어 뜻풀이이다. 빈칸에 들어갈 말로 가장 적절한 것은?",
    passage: {kind: "box", html: "v. to break food into smaller, softer <span class='blank'></span> with the teeth"},
    choices: ["grains", "dots", "figures", "drops", "pieces"],
    answer: 5,
  },
  {
    num: 3,
    prompt: "다음 중 단어의 품사 표기와 영어 뜻풀이가 <u>모두 옳게</u> 짝지어진 것은?",
    choices: [
      "dirty — a. having no leaves",
      "dot — n. a very small round mark",
      "pleasure — a. a state of feeling or being happy",
      "final — ad. being or happening at the end of a series of events",
      "cinema — n. a place where concerts are held",
    ],
    answer: 2,
  },
  {
    num: 4,
    prompt: "clue의 영어 뜻풀이는 'a sign or some information that helps you to find the answer to a problem or question'이다. 이 뜻풀이에 맞지 않게 사용된 문장은?",
    choices: [
      "The guide said the eyes would give us a clue.",
      "The dots in STIK's painting are a clue to the answer.",
      "The police found a clue to help them solve it.",
      "His white hair was a clue that he was the old doctor.",
      "The artist left a clue of green paint on the floor after painting the wall.",
    ],
    answer: 5,
  },
  {
    num: 5,
    groupLabel: "[5~7] 다음 글 A를 읽고, 물음에 답하시오.",
    prompt: "주어진 문장이 들어가기에 가장 적절한 곳은?",
    passage: {
      kind: "box",
      html:
        "Modern street art comes in many forms and makes cities lively. <span class='mm'>ⓐ</span> Today, we are going to see some well-known street artworks in London. <span class='mm'>ⓑ</span> The place we are going to visit is Shoreditch. <span class='cc'>ⓒ</span> Poor workers used to live here, but now it is one of the hippest areas in London. <span class='mm'>ⓓ</span> We can find great pieces of street art here. <span class='mm'>ⓔ</span> Do you see the painting over there? It is by the artist STIK, who is famous for using only lines and dots in his paintings of human bodies. The three figures represent the past, present, and future of Shoreditch. Which is which? Look closely at the eyes. They are just dots, but they will give you a clue.",
    },
    given: "The first place we visit tells us how a neighborhood can change over time.",
    choices: ["ⓐ", "ⓑ", "ⓒ", "ⓓ", "ⓔ"],
    answer: 2,
  },
  {
    num: 6,
    prompt: "윗글의 내용과 일치하는 것을 <보기>에서 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. The three figures stand for three times of Shoreditch.<br/>b. STIK draws human bodies with only lines and dots.<br/>c. Poor workers still live in Shoreditch, so it is poor.<br/>d. The way the eyes are drawn shows which figure is which.<br/>e. The guide tells the exact year the painting was made.",
    },
    choices: ["a, b, d", "a, c, d", "b, c, e", "b, d, e", "c, d, e"],
    answer: 1,
  },
  {
    num: 7,
    prompt: "안내자가 관람객에게 'look closely at the eyes'라고 말한 이유와, 그 말의 효과를 윗글에 근거하여 추론한 것으로 가장 적절한 것은?",
    choices: [
      "이유: 그림이 너무 작아 전체를 볼 수 없기 때문이다 / 효과: 작품의 크기를 직접 가늠하게 한다",
      "이유: 눈만 다른 화가가 그렸다고 소개하기 위해서이다 / 효과: 다른 작가와 비교하게 한다",
      "이유: 세 인물의 옷차림이 모두 다르기 때문이다 / 효과: 인물복장을 세밀하게 관찰하게 한다",
      "이유: 세 인물은 눈을 그리는 방식에서만 서로 다르기 때문이다 / 효과: 관람객이 답을 스스로 찾아내게 한다",
      "이유: 눈에 서명이 적혀 있기 때문이다 / 효과: 작품의 진위를 확인하게 한다",
    ],
    answer: 4,
    choiceLayout: "vertical",
  },
  {
    num: 8,
    groupLabel: "[8~10] 다음 글 B를 읽고, 물음에 답하시오.",
    prompt: "윗글을 한 문장으로 요약한 것으로 가장 적절한 것은?",
    passage: {
      kind: "box",
      html:
        "We are now in Finsbury Park to see a painting by Banksy, a famous street artist. His artworks often carry social messages, and the green tree over there is a good example. He created it by spraying green paint on a wall behind a leafless tree. Next to the tree is a person holding a sprayer. What message does Banksy want to give us? Many people think he is telling us that nature is having a hard time, and that we should take better care of <u>it</u>.",
    },
    choices: [
      "A leafless tree was damaged by a man with a sprayer.",
      "Banksy draws human bodies with only lines and dots.",
      "Visitors should care for the real trees in the park.",
      "Banksy shows how to clean green paint off a wall.",
      "Banksy made a green tree to make us think of nature.",
    ],
    answer: 5,
    choiceLayout: "vertical",
  },
  {
    num: 9,
    prompt: "밑줄 친 <u>it</u>이 가리키는 것을 <보기>에서 모두 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. nature<br/>b. the green tree on the wall<br/>c. the leafless tree<br/>d. the natural world around us<br/>e. Finsbury Park",
    },
    choices: ["a, b", "b, c", "a, d", "c, e", "d, e"],
    answer: 3,
  },
  {
    num: 10,
    prompt: " Banksy의 작품을 정리한 다음 글의 밑줄 친 부분 중 어법상 틀린 곳을 바르게 고친 것을 <보기>에서 모두 고른 것은?",
    passage: {
      kind: "box",
      html:
        "Banksy <u>(A) spray</u> green paint on a wall behind a tree that <u>(B) has</u> no leaves. His work <u>(C) carries</u> a social message, and many people think he <u>(D) wants</u> us <u>(E) take</u> better care of nature.",
    },
    cond: {
      title: "보기",
      html: "(A) spray → sprayed<br/>(B) has → have<br/>(C) carries → carry<br/>(D) wants → want<br/>(E) take → to take",
    },
    choices: ["A, B", "B, C", "C, D", "A, E", "D, E"],
    answer: 4,
  },
  {
    num: 11,
    groupLabel: "[11~13] 다음 글 C를 읽고, 물음에 답하시오.",
    prompt: "이 글의 주된 목적으로 가장 적절한 것은?",
    passage: {
      kind: "box",
      html:
        "The final stop of our tour is Muswell Hill. Can't see any street artworks? Well, <u>look down and look closely</u>. Do you see the little chewing gum paintings? They are by the artist Ben Wilson, who is well known for painting pictures on gum that people drop on the street. His gum paintings often carry personal messages from people he meets on the street. Wilson's paintings are tiny, but they give great pleasure to many people.",
    },
    choices: [
      "거리 예술의 문제점을 지적하기 위한 것이다",
      "껌 그림의 판매를 홍보하기 위한 것이다",
      "또 다른 거리 예술을 소개하기 위한 것이다",
      "두 화가의 작품 우열을 가리기 위한 것이다",
      "껌 그림 그리는 법을 가르치기 위한 것이다",
    ],
    answer: 3,
    choiceLayout: "vertical",
  },
  {
    num: 12,
    prompt: "글 A~C의 내용과 일치하는 것을 <보기>에서 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. The three figures of past, present and future were painted by Ben Wilson.<br/>b. Banksy created the green tree by spraying paint on a wall.<br/>c. Wilson's gum paintings carry messages from people he meets on the street.<br/>d. Shoreditch was a poor area in the past, but it is popular now.",
    },
    choices: ["a, b", "a, c, d", "b, c", "b, c, d", "a, b, c, d"],
    answer: 4,
  },
  {
    num: 13,
    prompt: "윗글에서 밑줄 친 'look down and look closely'의 의미로 가장 적절한 것은?",
    choices: [
      "소리를 낼까 봐 조심스럽게 걷다",
      "발밑을 내려다보며 자세히 살펴보다",
      "고개를 숙여 다른 관람객을 피하다",
      "아래를 보며 천천히 뒤로 물러나다",
      "눈을 감하고 상상력을 동원하다",
    ],
    answer: 2,
  },
  {
    num: 14,
    prompt: "다음 문장 중 밑줄 친 관계대명사의 쓰임이 어법상 <u>옳지 않은 것만을 모두</u> 고른 것은?",
    passage: {
      kind: "box",
      html:
        "ⓐ The place <u>that</u> we are going to visit is Shoreditch.<br/>ⓑ The man <u>which</u> I saw at the coffee shop was kind.<br/>ⓒ This is the restaurant <u>where</u> we had dinner last night.<br/>ⓓ The area <u>that</u> poor workers used to live is now popular.<br/>ⓔ The book <u>you</u> gave me was really interesting.",
    },
    choices: ["ⓑ, ⓓ", "ⓐ, ⓒ", "ⓑ, ⓔ", "ⓒ, ⓓ", "ⓓ, ⓔ"],
    answer: 1,
  },
  {
    num: 15,
    prompt: "다음 표의 분석으로 옳은 것만을 있는 대로 고른 것은?",
    table: {
      head: ["행", "문장", "선행사", "관계대명사의 격", "생략"],
      rows: [
        ["A", "I have a friend <u>that</u> I share everything with.", "사람", "목적격", "가능"],
        ["B", "The cat <u>that</u> followed us to school was black.", "사물", "주격", "불가능"],
        ["C", "Chris found the bike <u>which</u> he lost yesterday.", "사물", "주격", "가능"],
        ["D", "She is the girl <u>whom</u> I met at the concert.", "사람", "목적격", "가능"],
      ],
    },
    choices: ["A, B, C", "A, B, D", "A, C, D", "B, C, D", "C, D"],
    answer: 2,
  },
  {
    num: 16,
    prompt: "다음 중 어법상 옳은 문장의 개수는?",
    passage: {
      kind: "box",
      html:
        "(A) The boy which helped me is my brother.<br/>(B) The shirt which Sam is wearing is blue.<br/>(C) The money what she had was not enough.<br/>(D) This is the shop which my father bought it.<br/>(E) The man what spoke to us was a teacher.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 1,
  },
  {
    num: 17,
    prompt: "다음 우리말을 <조건>을 모두 만족하도록 영작한 것을 <보기>에서 모두 고른 것은?",
    cond: {
      title: "조건",
      html: "(a) 관계대명사 사용　(b) '공원에서'의 전치사구 포함　(c) 주절에 주어·동사 모두",
    },
    given: "내가 어제 공원에서 만난 소년은 나를 도와주었다.",
    condAfter: true,
    cond2: {
      title: "보기",
      html:
        "a. The boy who I met at the park yesterday helped me.<br/>b. The boy I met in the park yesterday helped me.<br/>c. The boy which I met at the park yesterday helped me.<br/>d. The boy that I met the park yesterday helped me.<br/>e. I met the boy in the park yesterday and he helped me.",
    },
    choices: ["a, c", "b, d", "c, d", "d, e", "a, b"],
    answer: 5,
  },
  {
    num: 18,
    prompt: "다음 문장을 「be used to + 동명사」 형태로 바꾸어 쓴 것 중 어법상 <u>틀린 것의 개수</u>는?",
    passage: {
      kind: "box",
      html:
        "(a) We used to walk. → We are used to walking.<br/>(b) She used to live alone. → She is used to live alone.<br/>(c) He used to drive. → He used to driving at night.<br/>(d) They used to eat out. → They are used to eating out.",
    },
    choices: ["1개", "2개", "3개", "4개", "0개"],
    answer: 2,
  },
  {
    num: 19,
    prompt: "다음 글의 빈칸 (A), (B)에 들어갈 말로 바르게 짝지어진 것은?",
    passage: {
      kind: "box",
      html: "(A) <span class='blank'></span> she <span class='blank'></span> get up early when she was a child?<br/>(B) Yes, but now she doesn't. She is a night worker.",
    },
    choices: [
      "Did / used to",
      "Does / use to",
      "Did / use to",
      "Was / used to",
      "Do / used to",
    ],
    answer: 3,
  },
  {
    num: 20,
    prompt: "다음 글의 빈칸 (A)~(C)에 들어갈 말로 바르게 짝지어진 것은?",
    passage: {
      kind: "box",
      html:
        "(A) There <span class='blank'></span> a small theater near the station, but it is a parking lot now.<br/>(B) My grandfather <span class='blank'></span> tell me old stories every evening when I was young.<br/>(C) I am used to <span class='blank'></span> late at night, so I am never tired in the morning.",
    },
    choices: [
      "used to be / used to / studying",
      "used to / used to be / study",
      "was used to / used to / to study",
      "used to be / use to / studied",
      "used to / used to be / studying",
    ],
    answer: 1,
  },
  {
    num: 21,
    prompt: "다음 표에서 어법상 옳지 <u>않은</u> 행을 고르면?",
    table: {
      head: ["행", "문장", "설명"],
      rows: [
        ["A", "the bike which she lost last year", "목적격 관계대명사 / 생략 가능"],
        ["B", "the people (whom) I invited to my party", "목적격 관계대명사 / 생략 가능"],
        ["C", "He used to get up early, but now he doesn't.", "과거의 습관 / 지금은 하지 않음"],
        ["D", "the man who is talking to my father", "주격 관계대명사 / 생략 가능"],
      ],
    },
    choices: ["A", "B", "C", "D", "옳지 않은 행이 없다"],
    answer: 4,
  },
  {
    num: 22,
    prompt: "'Poor workers lived in this area in the past, but they don't live there now.'와 의미가 같은 문장을 <보기>에서 모두 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. Poor workers used to live in this area.<br/>b. Poor workers are used to living in this area.<br/>c. Poor workers used to living in this area.<br/>d. In the past, poor workers lived in this area.<br/>e. Poor workers have lived in this area for a long time.",
    },
    choices: ["a, b", "c, d", "a, d", "b, e", "d, e"],
    answer: 3,
  },
  {
    num: 23,
    prompt: "다음 우리말을 <조건>을 모두 만족하도록 영작한 것을 <보기>에서 모두 고른 것은?",
    cond: {
      title: "조건",
      html: "(a) used to 사용　(b) '학교 근처 카페' 포함　(c) '지금은 그렇지 않다' 포함",
    },
    given: "우리 반 학생들은 예전에는 학교 근처 카페에서 자주 만났지만, 지금은 그렇지 않다.",
    cond2: {
      title: "보기",
      html:
        "a. Our classmates used to meet often at the café near school, but now they don't.<br/>b. Our classmates are used to meeting at the café.<br/>c. Our classmates used to meet often at the café.<br/>d. Our classmates used to met often at the café.<br/>e. Our classmates used to meet at the café near school, but now they don't.",
    },
    choices: ["a, b", "b, c", "c, d", "d, e", "a, e"],
    answer: 5,
  },
  {
    num: 24,
    prompt: "다음 문장에서 어법상 틀린 곳을 바르게 고친 것을 <보기>에서 모두 고른 것은?",
    passage: {kind: "box", html: "The man <u>which</u> owns the shop <u>used to are</u> a street artist."},
    cond: {
      title: "보기",
      html: "a. which → who<br/>b. used to are → used to be<br/>c. owns → own<br/>d. which → that<br/>e. used to are → is used to",
    },
    choices: ["a, c", "a, b, d", "b, d, e", "b, c, e", "d만"],
    answer: 2,
  },
  {
    num: 25,
    prompt: "다음 대화를 읽고, B가 확인해야 할 사항과 G의 말 목적으로 가장 적절한 것은?",
    passage: {
      kind: "dialog",
      html: "B: Excuse me, how can I get to the Star Stadium?<br/>G: You can get there by subway. Take line 4, the green line, and get off at Hangang Station.<br/>B: Line 4 and Hangang Station. Got it. How long will it take?<br/>G: A little less than 30 minutes.",
    },
    choices: [
      "확인: 호선과 하차 역 / 목적: 가는 법 안내",
      "확인: 경기장 이름 / 목적: 요금이 싸다",
      "확인: 환승역 / 목적: 버스 이용 권유",
      "확인: 요금 / 목적: 경기장 위치 확인",
      "확인: 출발 시각 / 목적: 택시 이용 조언",
    ],
    answer: 1,
    choiceLayout: "vertical",
  },
  {
    num: 26,
    prompt: "다음 (A)~(D)를 대화로서 자연스러운 순서대로 배열한 것은?",
    passage: {
      kind: "dialog",
      html:
        "G: Do you need help?<br/><br/>(A) B: OK. How long will it take by bus?<br/>(B) G: You can, but it's a little far. Why don't you go by bus? Take bus No. 12.<br/>(C) B: Yes. How can I get to the Dream Shopping Mall from here? Can I walk?<br/>(D) G: Not very long. It'll take less than 10 minutes.",
    },
    choices: ["(A)-(B)-(C)-(D)", "(C)-(A)-(B)-(D)", "(C)-(B)-(A)-(D)", "(B)-(C)-(D)-(A)", "(D)-(A)-(C)-(B)"],
    answer: 3,
  },
  {
    num: 27,
    prompt: "다음 대화를 읽고, 내용과 일치하는 것을 <보기>에서 있는 대로 고른 것은?",
    passage: {
      kind: "dialog",
      html:
        "B: Excuse me. I'm going to the National Art Museum. What's the best way to get there?<br/>G: You can walk there. Go straight for about 500 meters and turn left. It'll be on your right.<br/>B: I see. How long will it take?<br/>G: About 15 minutes.",
    },
    cond: {
      title: "보기",
      html:
        "a. G tells B that walking there is possible.<br/>b. B is told to turn left after going straight.<br/>c. Walking there takes about 15 minutes.<br/>d. G advises B to take a bus because it is far.",
    },
    choices: ["a, b, c", "a, b, d", "a, c, d", "b, c, d", "a, b, c, d"],
    answer: 1,
  },
  {
    num: 28,
    prompt: "다음 문장의 빈칸에 알맞은 전치사를 기준으로 나눈 것으로 옳은 것은?",
    passage: {
      kind: "box",
      html:
        "(a) Give a clue <span class='blank'></span> you.　(b) She made a cake <span class='blank'></span> me.<br/>(c) He sent it <span class='blank'></span> foreign newspapers.　(d) I bought a present <span class='blank'></span> her.<br/>(e) Please explain the rule <span class='blank'></span> the students.",
    },
    choices: [
      "(a)(b) ／ (c)(d)(e)",
      "(a)(c) ／ (b)(d)(e)",
      "(b)(d) ／ (a)(c)(e)",
      "(c)(e) ／ (a)(b)(d)",
      "(a)(c)(e) ／ (b)(d)",
    ],
    answer: 5,
  },
  {
    num: 29,
    prompt: "다음 문장의 빈칸에 <u>to</u>가 들어가야 하는 문장의 개수는?",
    passage: {
      kind: "box",
      html:
        "(a) He sent the pictures <span class='blank'></span> foreign newspapers.<br/>(b) Could you buy a stamp <span class='blank'></span> me?<br/>(c) She explained the meaning <span class='blank'></span> us.<br/>(d) He showed his photos <span class='blank'></span> us.<br/>(e) Please pass the sprayer <span class='blank'></span> me.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 4,
  },
  {
    num: 30,
    prompt: "다음 글의 빈칸 (A)~(C)에 들어갈 전치사로 바르게 짝지어진 것은?",
    passage: {
      kind: "box",
      html:
        "STIK is famous <u>(A)</u> using only lines and dots, and the painting is <u>(B)</u> him. <u>(C)</u> STIK, the three figures represent the past, present, and future of Shoreditch.",
    },
    choices: ["A: of ／ B: for ／ C: to", "A: for ／ B: to ／ C: of", "A: by ／ B: with ／ C: to", "A: for ／ B: by ／ C: of", "A: for ／ B: by ／ C: to"],
    answer: 5,
  },

  // ───────────────── L6 · 통합 (Q31~Q70) ─────────────────
  {
    num: 31,
    prompt: "다음은 L6 Word List에 실린 영어 뜻풀이이다. 빈칸에 들어갈 말로 가장 적절한 것은?",
    passage: {kind: "box", html: "n. a piece of <span class='blank'></span> on a particular subject in a newspaper or magazine"},
    choices: ["writing", "news", "meeting", "picture", "question"],
    answer: 1,
  },
  {
    num: 32,
    prompt: "다음은 L6 Word List에 실린 영어 뜻풀이이다. 빈칸에 들어갈 말로 가장 적절한 것은?",
    passage: {kind: "box", html: "v. to come together in a <span class='blank'></span> in one place"},
    choices: ["line", "circle", "queue", "group", "pair"],
    answer: 4,
  },
  {
    num: 33,
    prompt: "다음 <보기>에서 단어·품사·영어 뜻풀이가 <u>모두 옳게</u> 짝지어진 것만을 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. cemetery — n. a place where people put dead bodies in the ground<br/>" +
        "b. stress — n. an extra force used when pronouncing a particular word or syllable<br/>" +
        "c. independence — a. freedom from political control by other countries<br/>" +
        "d. medicine — n. the study and treatment of diseases and injuries<br/>" +
        "e. historic — n. important in history",
    },
    choices: ["a, b, c", "a, d, e", "b, c, d", "b, e", "a, b, d"],
    answer: 5,
  },
  {
    num: 34,
    prompt: "다음 표에서 영어 표현과 우리말 뜻이 바르게 연결되지 <u>않은</u> 것만을 고른 것은?",
    table: {
      head: ["행", "영어 표현", "우리말 뜻"],
      rows: [
        ["(a)", "at the invitation of the government", "정부의 초청으로"],
        ["(b)", "have the right to be independent", "독립할 권리가 있다"],
        ["(c)", "for the rest of his life", "평생의 절반 동안"],
        ["(d)", "sound similar to his real name", "본명과 의미가 비슷하다"],
        ["(e)", "under the watchful eye of the police", "경찰의 감시 아래에서"],
      ],
    },
    choices: ["(c), (d)", "(a), (b)", "(b), (e)", "(c), (e)", "(a), (d)"],
    answer: 1,
  },
  {
    num: 35,
    groupLabel: "[35~37] 다음 글을 읽고, 물음에 답하시오.",
    prompt: "윗글의 내용과 일치하는 것만을 <보기>에서 있는 대로 고른 것은?",
    passage: {
      kind: "box",
      html:
        "Dr. Schofield was a Canadian doctor who first came to Korea in 1916 to teach medicine. <span class='mm'>ⓐ</span> He loved his Korean name because it sounded similar to his real name. <span class='mm'>ⓑ</span> He wanted to teach in Korean, so he began to learn it right away. <span class='cc'>ⓒ</span> He studied so hard that he could teach in Korean within a few years. <span class='mm'>ⓓ</span> He believed every country has the right to be independent, so he helped the Korean independence movement. <span class='mm'>ⓔ</span> For the rest of his life, he worked to help Koreans, especially poor students.",
    },
    cond: {
      title: "보기",
      html:
        "a. He came to Korea in 1916 to teach medicine.<br/>" +
        "b. He learned Korean to teach his classes in Korean.<br/>" +
        "c. He kept teaching his classes in English.<br/>" +
        "d. He helped the Korean independence movement.<br/>" +
        "e. He was the first Canadian doctor in Korea.",
    },
    choices: ["a, b, c", "a, d, e", "b, c, e", "a, b, d", "c, d, e"],
    answer: 4,
  },
  {
    num: 36,
    prompt: "그가 한국어를 오래 준비한 뒤에가 아니라 '곧바로' 배우기 시작한 이유와, 그로부터 알 수 있는 그의 태도로 가장 적절한 것은?",
    choices: [
      "이유: 한국 이름을 쉽게 부르기 위해서이다 / 태도: 이름을 소중히 여긴다",
      "이유: 외국 신문사에 기사를 쓰기 위해서이다 / 태도: 글쓰기를 즐긴다",
      "이유: 학생들에게 한국어로 가르치기 위해서이다 / 태도: 필요한 일을 미루지 않고 바로 실행한다",
      "이유: 일본 경찰의 감시를 피하기 위해서이다 / 태도: 위험을 피하는 지혜가 있다",
      "이유: 독립 운동가들과 사귀기 위해서이다 / 태도: 성격이 외향적이다",
    ],
    answer: 3,
    choiceLayout: "vertical",
  },
  {
    num: 37,
    prompt: "주어진 문장이 들어가기에 가장 적절한 곳은?",
    given: "His Korean students could follow his classes without an interpreter.",
    choices: ["ⓐ", "ⓑ", "ⓒ", "ⓓ", "ⓔ"],
    answer: 4,
  },
  {
    num: 38,
    prompt: "다음 글에서 밑줄 친 <u>this important event</u>가 가리키는 것으로 가장 적절한 것은?",
    passage: {
      kind: "box",
      html: "Dr. Schofield was looking at the huge crowd that had gathered for the independence of Korea. He said to himself, “I hope the world will learn about <u>this important event</u> through my pictures.”",
    },
    choices: [
      "foreign newspapers' visit to Korea",
      "a new school opening in Seoul",
      "people's gathering for the independence of Korea",
      "a photography show in a park",
      "the doctor's return to Canada",
    ],
    answer: 3,
  },
  {
    num: 39,
    groupLabel: "[39~42] 다음 글을 읽고, 물음에 답하시오.",
    prompt: "윗글의 내용으로 옳은 것만을 <보기>에서 있는 대로 고른 것은?",
    passage: {
      kind: "box",
      html:
        "On February 28, 1919, a man came to Dr. Schofield's house and said, “I have a special favor to ask.” “Many people are going to gather tomorrow for the independence of Korea. Could you take pictures of the event and share them with the world?” On March 1, Dr. Schofield was hiding in a building and saw a large group of people gathering outside. He began to take pictures and, looking at the crowd, said, “<u>Unbelievable!</u>” He later wrote an article about the historic event and sent it to foreign newspapers with his pictures.",
    },
    cond: {
      title: "보기",
      html:
        "a. A man asked him to take pictures of the event.<br/>" +
        "b. The man said exactly how many people would come.<br/>" +
        "c. He watched the gathering from inside a building.<br/>" +
        "d. He wrote an article and sent it with his pictures.<br/>" +
        "e. He refused the favor because he was too busy.",
    },
    choices: ["a, b, e", "a, c, d", "b, c, e", "b, d", "c, d, e"],
    answer: 2,
  },
  {
    num: 40,
    prompt: "그가 행사장 한복판이 아니라 'was hiding in a building'이었다는 사실로부터 추론한 것으로 가장 적절한 것은?",
    choices: [
      "그는 사진 찍는 일을 부끄럽게 생각했다.",
      "그는 사람들이 몰려드는 것이 무서웠다.",
      "그는 카메라가 고장 날까 조심하고 있었다.",
      "그의 촬영을 막으려는 감시가 있어 스스로를 드러내지 않아야 했다.",
      "그는 참여보다 지켜 보는 편을 좋아했다.",
    ],
    answer: 4,
    choiceLayout: "vertical",
  },
  {
    num: 41,
    prompt: "윗글의 내용을 시간 순서대로 배열한 것으로 가장 적절한 것은?",
    table: {
      head: ["번호", "사건"],
      rows: [
        ["(a)", "기사를 써서 사진과 함께 외국 신문사에 보냈다."],
        ["(b)", "한 남자가 집을 찾아와 특별한 부탁을 했다."],
        ["(c)", "건물 안에 숨어 많은 사람이 모인 것을 보았다."],
        ["(d)", "사진기를 들고 촬영을 시작했다."],
      ],
    },
    choices: ["(b)-(c)-(d)-(a)", "(b)-(d)-(c)-(a)", "(c)-(b)-(d)-(a)", "(d)-(b)-(c)-(a)", "(c)-(d)-(b)-(a)"],
    answer: 1,
  },
  {
    num: 42,
    prompt: "윗글의 장면에서 Dr. Schofield이 “<u>Unbelievable!</u>”이라고 말한 것으로 미루어 알 수 있는 심정으로 가장 적절한 것은?",
    choices: ["화가", "놀람과 감탄", "지루함", "후회", "부끄러움"],
    answer: 2,
  },
  {
    num: 43,
    prompt: "다음은 Dr. Schofield의 생애를 요약한 글이다. 본문 내용과 맞지 <u>않은</u> 부분은?",
    passage: {
      kind: "box",
      html:
        "Dr. Schofield came to Korea in 1916 <u>(A)</u> to teach medicine. He learned Korean because he wanted to teach <u>(B)</u> in Korean. In 1919 he took pictures of the March 1st Movement and <u>(C)</u> sent them to foreign newspapers. He continued to write about the terrible situation in Korea for <u>(D)</u> rich students' families. In 1958 he returned to Korea <u>(E)</u> at the invitation of the Korean government and never left again.",
    },
    choices: ["(A)", "(B)", "(C)", "(D)", "(E)"],
    answer: 4,
  },
  {
    num: 44,
    prompt: "selfless의 영어 뜻풀이(caring more about other people's needs and interests than about your own)에 가장 잘 부합하는 Dr. Schofield의 모습으로 가장 적절한 것은?",
    choices: [
      "한국 이름을 좋아하며 자신의 본명과 비슷하게 느꼈다.",
      "외국 신문사에 기사를 보내 자신의 이름을 알렸다.",
      "캐나다로 돌아간 뒤에는 한국에 관해 말을 아꼈다.",
      "사진 속 세 인물이 나타내는 시대를 정확히 알고 싶어 했다.",
      "자신의 신변이 위태로운 상황에서도 한국인의 처지를 계속 알렸고, 가난한 학생을 돕는 일을 멈추지 않았다.",
    ],
    answer: 5,
    choiceLayout: "vertical",
  },
  {
    num: 45,
    prompt: "다음 요약문에서 밑줄 친 (A)~(E) 중 어법상 <u>틀린</u> 것의 개수는?",
    passage: {
      kind: "box",
      html:
        "In 1919, Dr. Schofield (A) <u>hiding</u> in a building and saw many people gathering. He (B) <u>took a pictures</u> of the event. He (C) <u>sent</u> the pictures to foreign newspapers. After he left Korea, he (D) <u>continued to write</u> about its terrible situation. He (E) <u>was buried</u> in Seoul National Cemetery in 1970.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 2,
  },
  {
    num: 46,
    prompt: "다음 표에서 빈칸 (A)·(B)·(C)에 들어갈 말이 모두 알맞은 행은?",
    table: {
      head: ["행", "(A) I saw my brother ___.", "(B) We felt the ground ___.", "(C) She heard the name ___."],
      rows: [
        ["A", "do", "shaking", "call"],
        ["B", "doing", "to shake", "calls"],
        ["C", "to do", "shook", "to call"],
        ["D", "did", "shaking", "to call"],
        ["E", "doing", "shaking", "called"],
      ],
    },
    choices: ["A", "B", "C", "D", "E"],
    answer: 5,
  },
  {
    num: 47,
    prompt: "다음 문장의 빈칸에 들어갈 말로 가장 적절한 것은?",
    passage: {
      kind: "box",
      html: "When the teacher read the list, my brother heard his name <span class='blank'></span> in the classroom.",
    },
    choices: ["called", "call", "to call", "calls", "calling"],
    answer: 1,
  },
  {
    num: 48,
    prompt: "다음 우리말을 <조건>에 맞추어 옮긴 문장으로 옳은 것의 개수는?",
    cond: {
      title: "조건",
      html: "(a) 지각동사 + 목적어 + 동사원형(또는 -ing)의 형식을 사용할 것<br/>(b) “길거리에서”를 on the street으로 나타낼 것",
    },
    given: "우리말: 나는 그녀가 길거리에서 노래 부르는 것을 보았다.",
    passage: {
      kind: "box",
      html:
        "(a) I saw her sing on the street.<br/>(b) I saw her singing on the street.<br/>(c) I saw her to sing on the street.<br/>(d) I saw her sing in street.<br/>(e) She was seen sing on the street.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 2,
  },
  {
    num: 49,
    prompt: "다음 문장 중 빈칸에 <u>동명사(-ing)</u>가 들어가야 하는 문장의 개수는?",
    passage: {
      kind: "box",
      html:
        "(a) She enjoys <span class='blank'></span> articles about famous people.<br/>(b) He decided <span class='blank'></span> medicine in Canada.<br/>(c) The reporter kept <span class='blank'></span> about the event for years.<br/>(d) We want <span class='blank'></span> the March 1st Movement Exhibition.<br/>(e) They finished <span class='blank'></span> the old photos last week.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 3,
  },
  {
    num: 50,
    prompt: "다음 표에서 동사와 뒤에 올 알맞은 형태, 예문이 바르게 짝지어지지 <u>않은</u> 것만을 고른 것은?",
    table: {
      head: ["행", "동사(표현)", "제시된 형태", "예문"],
      rows: [
        ["(a)", "would like", "동명사", "I would like helping you."],
        ["(b)", "continue", "to 부정사", "He continued to write it."],
        ["(c)", "be famous for", "동명사", "STIK is famous for using dots."],
        ["(d)", "plan", "동명사", "She plans opening a gallery."],
        ["(e)", "practice", "동명사", "He practices speaking Korean."],
      ],
    },
    choices: ["(a), (b)", "(a), (d)", "(b), (c)", "(b), (e)", "(c), (e)"],
    answer: 2,
  },
  {
    num: 51,
    prompt: "다음 문장에서 밑줄 친 부분에 들어간 형태로 어법상 <u>옳지 않은</u> 것은?",
    passage: {
      kind: "box",
      html:
        "(a) He continued <u>to study</u> Korean with his students.<br/>(b) He kept <u>studying</u> Korean every night.<br/>(c) She kept <u>writing</u> articles about Korea.<br/>(d) He continued <u>learn</u> Korean for three years.<br/>(e) They continued <u>to help</u> poor students.",
    },
    choices: ["(a)", "(b)", "(c)", "(d)", "(e)"],
    answer: 4,
  },
  {
    num: 52,
    prompt: "다음 표에서 동사와 뒤에 올 형태, 예문이 <u>모두 옳은</u> 행만을 고른 것은?",
    table: {
      head: ["행", "동사", "뒤에 오는 형태", "예문"],
      rows: [
        ["A", "want", "to 부정사", "He wanted to teach in Korean."],
        ["B", "finish", "to 부정사", "She finished to research it."],
        ["C", "enjoy", "동명사", "They enjoy reading his article."],
        ["D", "decide", "동명사", "He decided studying medicine."],
        ["E", "keep", "to 부정사", "He kept to write about Korea."],
      ],
    },
    choices: ["A, B", "B, C", "C, D", "D, E", "A, C"],
    answer: 5,
  },
  {
    num: 53,
    prompt: "다음 문장을 too ~ to 구문으로 바르게 바꾼 것은?",
    given: "The box is so heavy that I can't lift it.",
    choices: [
      "The box is too heavy to me lift it.",
      "The box is too heavy that I can't lift.",
      "The box is too heavy for me to lift.",
      "The box is heavy too for I to lift.",
      "The box is too heavy for me to lift it.",
    ],
    answer: 3,
    choiceLayout: "vertical",
  },
  {
    num: 54,
    prompt: "원문장을 enough to ... 또는 too ~ to로 바꾼 문장 중 <u>틀린</u> 것의 개수는?",
    cond: {
      title: "원문장 → 바꾼 문장",
      html:
        "(a) The water was so clear that we could see the fish. → It was clear enough for us to see the fish.<br/>" +
        "(b) He was so experienced that he explained it well. → He was experienced enough to explain it well.<br/>" +
        "(c) The homework was so difficult that he couldn't finish it. → It was difficult enough for him to finish it.<br/>" +
        "(d) I was so busy that I couldn't answer the phone. → I was not busy enough to answer it.<br/>" +
        "(e) The tea was so warm that we could drink it. → It was warm enough to drink.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 2,
  },
  {
    num: 55,
    prompt: "다음 문장과 뜻이 같은 것만을 <보기>에서 있는 대로 고른 것은?",
    passage: {
      kind: "box",
      html: "The movie is so boring that I can't watch it to the end.",
    },
    cond: {
      title: "보기",
      html:
        "a. It is too boring for me to watch to the end.<br/>" +
        "b. It is not interesting enough to watch to the end.<br/>" +
        "c. It is boring enough not to watch it to the end.<br/>" +
        "d. It is too boring for to watch to the end.<br/>" +
        "e. It is so boring that I can watch it to the end.",
    },
    choices: ["a, b", "a, d", "b, c", "c, e", "d, e"],
    answer: 1,
  },
  {
    num: 56,
    prompt: "위 대화의 빈칸에 들어갈 말로 가장 적절한 것은?",
    passage: {
      kind: "dialog",
      html:
        "G: What kinds of soft drinks do you usually have?<br/>B: I usually drink apple soda, but today I want something different. <span class='blank'></span><br/>G: Try a cherry soda. The cherry soda here is really good.<br/>B: OK. I'll give it a try then.",
    },
    choices: [
      "How much is the cherry soda?",
      "Where did you buy the soda?",
      "Do you like apple soda?",
      "Can you recommend some new soft drinks for me?",
      "Why are you so busy today?",
    ],
    answer: 4,
  },
  {
    num: 57,
    prompt: "위 대화에 대한 설명으로 옳은 것만을 <보기>에서 있는 대로 고른 것은?",
    passage: {
      kind: "dialog",
      html:
        "B: What kinds of magazines do you have?<br/>G: We have all kinds of magazines.<br/>B: I'm really interested in science. Can you recommend a science magazine for me?<br/>G: Sure. You'll love this one, Teen's Science.",
    },
    cond: {
      title: "보기",
      html:
        "a. The boy wants a science magazine.<br/>b. The shop sells only science magazines.<br/>c. The girl recommends <i>Teen's Science</i>.<br/>d. The boy has no interest in science.<br/>e. The girl is sure the boy will like it.",
    },
    choices: ["a, b, c", "b, c, d", "b, d, e", "c, d, e", "a, c, e"],
    answer: 5,
  },
  {
    num: 58,
    prompt: "위 대화의 밑줄 친 (A)~(E) 중 어법상 <u>틀린</u> 것의 개수는?",
    passage: {
      kind: "dialog",
      html:
        "B: I'm looking for books on famous people who helped others.<br/>" +
        "G: How about (A) <u>reading</u> about Audrey Hepburn?<br/>" +
        "B: I enjoy (B) <u>to read</u> about her work, but I want (C) <u>read</u> about a doctor.<br/>" +
        "G: Then I recommend (D) <u>to choose</u> Dr. Schofield. He did so much for (E) <u>children and students</u>.<br/>" +
        "B: OK. I'll give it a try.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 3,
  },
  {
    num: 59,
    prompt: "위 대화에서 밑줄 친 <u>it</u>이 가리키는 것으로 가장 적절한 것은?",
    passage: {
      kind: "dialog",
      html:
        "B: What kinds of movies do you like?<br/>G: I like action movies. <i>Spider-Man</i> is my favorite.<br/>B: Oh, I didn't see <u>it</u>.<br/>G: You should. It's really fun. I strongly recommend it.",
    },
    choices: ["액션을 다룬 영화라는 장르", "자주 가는 영화관", "영화 표 한 장", "영화 《스파이더맨》", "소년이 좋아하는 배우"],
    answer: 4,
  },
  {
    num: 60,
    prompt: "다음 표에서 문장과 그 설명이 <u>모두 옳은</u> 행만을 고른 것은?",
    table: {
      head: ["행", "문장", "설명"],
      rows: [
        ["A", "The book (that) you gave me was interesting.", "목적격 관계대명사 / 생략 가능"],
        ["B", "the girl who I met at the museum", "주격 관계대명사 / 생략 불가"],
        ["C", "Poor workers used to live in this area.", "지금도 살고 있다는 뜻"],
        ["D", "She is used to getting up early.", "be used to + 동명사(익숙하다)"],
        ["E", "There used to be a large gallery here.", "갤러리가 여럿 있었다는 뜻"],
      ],
    },
    choices: ["A, D", "A, E", "B, C", "B, D", "C, E"],
    answer: 1,
  },
  {
    num: 61,
    prompt: "원문장을 <조건>에 맞추어 바꾼 문장으로 옳은 것의 개수는?",
    cond: {
      title: "조건",
      html: "원문장의 뜻과 문법 관계를 그대로 유지하여 바꾼 문장일 것. (4형식→3형식 전환의 전치사, so ~ that → too ~ to / enough to 전환, 문말의 it 처리에 유의)",
    },
    passage: {
      kind: "box",
      html:
        "(a) Give me a clue. → Give a clue to me.<br/>" +
        "(b) They give many people great pleasure. → They give great pleasure to many people.<br/>" +
        "(c) The coffee was so hot that I couldn't drink it. → The coffee was too hot to drink it.<br/>" +
        "(d) You are so young that you can't see the movie. → You are young enough to see the movie.<br/>" +
        "(e) She heard her dog's name called. → I heard her dog's name call.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 2,
  },
  {
    num: 62,
    prompt: "다음 <보기>는 L6 Word List의 영어 뜻풀이이다. 옳은 것만을 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. bury: to put something into a hole and cover it<br/>" +
        "b. research: to collect information about something<br/>" +
        "c. government: the study and treatment of diseases<br/>" +
        "d. cemetery: a place where people put dead bodies<br/>" +
        "e. movement: an action people take to change society",
    },
    choices: ["a, b, c", "b, c, e", "a, b, d, e", "a, d, e", "b, c, d, e"],
    answer: 3,
  },
  {
    num: 63,
    prompt: "다음 (A), (B)의 영어 뜻풀이에 해당하는 단어를 바르게 짝지은 것은?",
    passage: {
      kind: "box",
      html:
        "(A) n. an action that a group of people take to change something in society<br/>(B) a. caring more about other people's needs and interests than about your own",
    },
    choices: ["medicine ／ gather", "stress ／ historic", "research ／ foreign", "invitation ／ unbelievable", "movement ／ selfless"],
    answer: 5,
  },
  {
    num: 64,
    prompt: "다음 우리말을 <조건>에 맞추어 옮긴 문장으로 옳은 것의 개수는?",
    cond: {
      title: "조건",
      html: "(a) 목적격 관계대명사를 사용할 것<br/>(b) 지각동사 + 목적어 + 동사원형(또는 -ing)의 형식을 사용할 것",
    },
    given: "우리말: 나는 그 사진을 찍은 남성이 벽 뒤에 숨는 것을 보았다.",
    passage: {
      kind: "box",
      html:
        "(a) I saw the man who took the pictures hide behind the wall.<br/>(b) I saw the man who took the pictures hiding behind the wall.<br/>(c) I saw the man what took the pictures to hide behind the wall.<br/>(d) I saw the man which took the pictures hid behind the wall.<br/>(e) The man who took the pictures I saw to hide behind the wall.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 2,
  },
  {
    num: 65,
    prompt: "“Banksy's artworks often carry social messages.”에서 밑줄 친 carry의 쓰임과 같은 의미를 나타내는 문장은?",
    passage: {
      kind: "box",
      html: "Banksy's artworks often <u>carry</u> social messages.",
    },
    choices: [
      "This bus carries visitors from the station to the park.",
      "He carried the heavy canvas up the stairs.",
      "This poster carries an important message about nature.",
      "She carried her little brother on her back.",
      "The workers carried out the plan in three days.",
    ],
    answer: 3,
    choiceLayout: "vertical",
  },
  {
    num: 66,
    prompt: "5·6과의 지문과 대화를 모두 근거로 할 때, <보기>의 진술 중 옳은 것만을 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. Wilson의 껌 그림은 개인적 메시지, Banksy의 작품은 사회적 메시지를 담는다.<br/>" +
        "b. 가난한 노동자는 지금도 Shoreditch에 산다.<br/>" +
        "c. Schofield은 3·1 운동 뒤에도 한국의 참혹상을 쓰기를 계속했다.<br/>" +
        "d. “They give great pleasure to many people.”는 “They give many people great pleasure.”와 같다.<br/>" +
        "e. STIK의 세 인물은 Schofield을 기념해 그린 것이다.",
    },
    choices: ["a, b, c", "b, d, e", "a, c, e", "a, c, d", "b, c, d"],
    answer: 4,
  },
  {
    num: 67,
    prompt: "다음 문장 중 어법상 옳은 것만을 <보기>에서 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. There used to be a small theater near the station.<br/>" +
        "b. She is used to get up early these days.<br/>" +
        "c. He continued to write articles about Korea.<br/>" +
        "d. I want studying medicine in the future.<br/>" +
        "e. The movie is so boring that I want to cry.",
    },
    choices: ["a, b, d", "b, c, d", "c, d, e", "a, b, e", "a, c, e"],
    answer: 5,
  },
  {
    num: 68,
    prompt: "다음 문장을 뜻이 같은 문장으로 바르게 바꿔 쓴 것은?",
    given: "Wilson's gum paintings are tiny, but they give great pleasure to many people.",
    choices: [
      "Although they are small, they make many people happy.",
      "They are tiny, so few people enjoy them.",
      "They are so big that they give people pleasure.",
      "They are tiny, so only a few enjoy them.",
      "Whether they are small or not, people are unhappy.",
    ],
    answer: 1,
    choiceLayout: "vertical",
  },
  {
    num: 69,
    prompt: "다음 우리말을 <조건>에 맞게 옮긴 문장으로 옳은 것의 개수는?",
    cond: {
      title: "조건",
      html: "(a) 우리말과 같은 뜻으로 바꾼 문장일 것<br/>(b) 시제를 과거로 유지할 것",
    },
    given: "우리말: 그는 너무 어렸기 때문에 그 영화를 볼 수 없었다.",
    passage: {
      kind: "box",
      html:
        "(a) He was too young to see the film.<br/>(b) He was not old enough to see the film.<br/>(c) He was so young that he couldn't see the film.<br/>(d) He is too young to see the film.<br/>(e) He was too young that he couldn't see the film.",
    },
    choices: ["1개", "2개", "3개", "4개", "5개"],
    answer: 3,
  },
  {
    num: 70,
    prompt: "다음 <보기>에서 문법 설명과 예문이 바르게 짝지어진 것만을 있는 대로 고른 것은?",
    cond: {
      title: "보기",
      html:
        "a. 「be famous for + 동명사」: STIK is famous for using dots.<br/>" +
        "b. 「give a clue의 전치사」: Give a clue for us.<br/>" +
        "c. 「과거에는 ~하곤 했다」: There used to be a gallery here.<br/>" +
        "d. 「지각동사+목적어+to 부정사」: I saw the artist to spray paint.<br/>" +
        "e. 「so ~ that의 전환」: The box was so heavy to lift.<br/>" +
        "f. 「주격 who는 생략 불가」: The man who came is famous.",
    },
    choices: ["a, c, f", "a, b, e", "b, d, e", "c, d, f", "b, e, f"],
    answer: 1,
  },
];
