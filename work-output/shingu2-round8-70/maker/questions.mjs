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
    prompt: "clue의 영어 뜻풀이는 'a sign or some information that helps you to find the answer to a problem or question'이다. 다음 중 이 뜻풀이에 맞지 않게 사용된 문장은?",
    choices: [
      "The guide told us to look closely at the eyes because they would give us a clue.",
      "The dots in STIK's painting are small, but they are a clue to the answer.",
      "The police found a clue that helped them understand the case.",
      "His white hair was a clue that he was the old doctor.",
      "The artist left a clue of green paint on the floor after painting the wall.",
    ],
    answer: 5,
  },
  {
    num: 5,
    groupLabel: "[5~7] 다음 글을 읽고, 물음에 답하시오.",
    prompt: "주어진 문장이 들어가기에 가장 적절한 곳은?",
    passage: {
      kind: "box",
      html:
        "Modern street art comes in many different forms, and it makes cities colorful and lively. <span class='mm'>ⓐ</span> Today, we are going to see some well-known street artworks in London. <span class='mm'>ⓑ</span> The place that we are going to visit is Shoreditch. <span class='cc'>ⓒ</span> Poor workers used to live in this area, but now it is one of the hippest areas in London. <span class='mm'>ⓓ</span> We can find so many great pieces of street art here. <span class='mm'>ⓔ</span> Do you see the painting over there? It is by the artist STIK. He is famous for using only lines and dots in his paintings of human bodies. According to STIK, the three figures represent the past, present, and future of Shoreditch. Which figure represents which? Well, look closely at the eyes of the three figures. They are just dots, but they will give you a clue.",
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
        "a. The three figures stand for three different times of Shoreditch.<br/>b. STIK draws human bodies with only lines and dots.<br/>c. Poor workers still live in Shoreditch, so it is now a poor area.<br/>d. The way the eyes are drawn shows which figure is which.<br/>e. The guide tells visitors the exact year the painting was made.",
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
    groupLabel: "[8~10] 다음 글을 읽고, 물음에 답하시오.",
    prompt: "윗글을 한 문장으로 요약한 것으로 가장 적절한 것은?",
    passage: {
      kind: "box",
      html:
        "We are now in Finsbury Park to see a painting by Banksy, an internationally famous street artist. Banksy's artworks often carry social messages, and the green tree you see over there is a good example. As you can see, he created a green tree by spraying green paint on a wall behind a leafless tree. Next to the tree, there is a person who is holding a sprayer. What message does Banksy want to give us through this painting? In many people's opinion, he is telling us that nature is having a hard time. They think he wants us to take better care of <u>it</u>.",
    },
    choices: [
      "A leafless tree in Finsbury Park was damaged by a man with a sprayer.",
      "Banksy is a famous artist who draws human bodies with lines and dots.",
      "Visitors should take better care of the real trees in Finsbury Park.",
      "Banksy shows how green paint can be removed from an old wall.",
      "Banksy made a green tree on a wall to make people think about nature in trouble.",
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
    groupLabel: "[11~13] 다음 글을 읽고, 물음에 답하시오.",
    prompt: "이 글의 주된 목적으로 가장 적절한 것은?",
    passage: {
      kind: "box",
      html:
        "The final stop of our tour today is Muswell Hill. Can't see any street artworks? Well, <u>look down and look closely</u>. Do you see the little chewing gum paintings? They are by the artist Ben Wilson. Wilson is well known for painting pictures on gum that people drop on the street. His gum paintings often carry personal messages from people he meets on the street. For example, the one over here is a happy birthday wish for a loved one. Wilson's gum paintings are tiny, but they give great pleasure to many people.",
    },
    choices: [
      "거리에 껌을 함부로 버리지 않도록 관람객을 계도하기 위한 것이다.",
      "Wilson의 그림 값을 매겨 보고 판매를 홍보하기 위한 것이다.",
      "런던에서 만날 수 있는 또 다른 형태의 거리 예술을 소개하기 위한 것이다.",
      "Banksy와 Wilson의 작품성 우열을 비교하여 판단하기 위한 것이다.",
      "껌 그림 만드는 방법을 단계별로 가르치기 위한 것이다.",
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
    prompt: "다음 우리말을 <조건>을 모두 만족하도록 관계대명사를 사용하여 영작한 것을 <보기>에서 모두 고른 것은?",
    cond: {
      title: "조건",
      html: "(a) 관계대명사를 사용할 것　(b) '공원에서'를 나타내는 전치사구를 포함할 것　(c) 주절에 주어와 동사를 모두 갖출 것",
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
        "(a) We used to walk to school. → We are used to walking to school.<br/>(b) She used to live alone. → She is used to live alone.<br/>(c) He used to drive at night. → He used to driving at night.<br/>(d) They used to eat bread for breakfast. → They are used to eating bread for breakfast.",
    },
    choices: ["1개", "2개", "3개", "4개", "0개"],
    answer: 2,
  },
  {
    num: 19,
    prompt: "다음 글의 빈칸 (A), (B)에 들어갈 말로 바르게 짝지어진 것은?",
    passage: {
      kind: "box",
      html: "(A)<span class='blank'></span> she <span class='blank'></span> get up early when she was a child?<br/>(B) Yes, but now she doesn't. She is a night worker.",
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
        ["A", "The book (that) you gave me was interesting.", "목적격 관계대명사 / 생략 가능"],
        ["B", "the people (whom) I invited to my party", "목적격 관계대명사 / 생략 가능"],
        ["C", "Poor workers used to live in this area.", "과거의 상태 / 지금은 그렇지 않음"],
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
      html: "(a) used to를 사용할 것　(b) '학교 근처 카페'가 나타나도록 할 것　(c) '지금은 그렇지 않다'는 내용을 포함할 것",
    },
    given: "우리 반 학생들은 예전에는 학교 근처 카페에서 자주 만났지만, 지금은 그렇지 않다.",
    cond2: {
      title: "보기",
      html:
        "a. Our classmates used to meet often at the café near our school, but now they don't.<br/>b. Our classmates are used to meeting often at the café near our school, but now they don't.<br/>c. Our classmates used to meet often at the café, but now they don't.<br/>d. Our classmates used to met often at the café near our school, but now they don't.<br/>e. Our classmates used to meet often near our school at the café, but now they don't.",
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
      "확인할 사항: 호선과 하차 역 / 목적: 지하철로 가는 방법을 알려 준다",
      "확인할 사항: 경기장 이름 / 목적: 지하철 요금이 가장 싸다고 설득한다",
      "확인할 사항: 환승역 / 목적: 버스를 타도록 권유한다",
      "확인할 사항: 요금 / 목적: 경기장의 위치를 묻는다",
      "확인할 사항: 출발 시각 / 목적: 택시를 이용하도록 조언한다",
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
        "a. The museum is about 500 meters away.<br/>b. B is told to turn left after going straight.<br/>c. Walking there takes about 15 minutes.<br/>d. G advises B to take a bus because it is far.",
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
        "(a) He sent the pictures <span class='blank'></span> foreign newspapers.<br/>(b) Can you take a picture <span class='blank'></span> me?<br/>(c) She explained the meaning <span class='blank'></span> us.<br/>(d) He showed his photos <span class='blank'></span> us.<br/>(e) Please pass the sprayer <span class='blank'></span> me.",
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
];
