/*
 * BuildIt 시각 편집기 — GrapesJS 한국어 메시지.
 * GrapesJS 공식 ko 로케일은 스타일 속성 이름(Float, Display …)과 상태 문구를 번역하지 않아
 * 편집 화면이 영어와 한국어로 섞여 보였다. 편집기가 쓰는 항목을 한국어로 채운다.
 * 사용: grapesjs.init({ i18n: { locale: 'ko', detectLocale: false, messages: { ko: window.F2RVisualEditorI18nKo } } })
 */
window.F2RVisualEditorI18nKo = {
  assetManager: {
    addButton: '이미지 추가',
    inputPlh: 'http://path/to/the/image.jpg',
    modalTitle: '이미지 선택',
    uploadTitle: '이미지를 여기에 놓거나, 눌러서 파일을 선택하세요'
  },
  domComponents: {
    names: {
      '': '상자',
      wrapper: '페이지',
      text: '텍스트',
      comment: '주석',
      image: '이미지',
      video: '동영상',
      label: '라벨',
      link: '링크',
      map: '지도',
      tfoot: '표 바닥글',
      tbody: '표 본문',
      thead: '표 머리글',
      table: '표',
      row: '표 행',
      cell: '표 칸'
    }
  },
  deviceManager: {
    device: '기기',
    devices: { desktop: 'PC', tablet: '태블릿', mobileLandscape: '모바일(가로)', mobilePortrait: '모바일' }
  },
  selectorManager: {
    label: '클래스',
    selected: '선택됨',
    emptyState: '- 상태 -',
    states: { hover: '마우스 올림', active: '클릭', 'nth-of-type(2n)': '짝수/홀수' }
  },
  styleManager: {
    empty: '스타일을 바꾸려면 먼저 캔버스에서 요소를 선택하세요',
    layer: '레이어',
    fileButton: '이미지',
    sectors: { general: '일반', layout: '레이아웃', typography: '글꼴', decorations: '꾸미기', extra: '기타', flex: 'Flex', dimension: '크기·위치' },
    properties: {
      float: '배치', display: '표시 방식', position: '위치 방식', top: '위', right: '오른쪽', bottom: '아래', left: '왼쪽',
      width: '너비', height: '높이', 'max-width': '최대 너비', 'min-height': '최소 높이', margin: '바깥 여백', padding: '안쪽 여백',
      'font-family': '글꼴', 'font-size': '글자 크기', 'font-weight': '글자 굵기', 'letter-spacing': '글자 간격', color: '글자 색',
      'line-height': '줄 간격', 'text-align': '정렬', 'text-shadow': '글자 그림자', 'border-radius': '모서리 둥글기', border: '테두리',
      'box-shadow': '그림자', background: '배경', 'background-color': '배경색', 'background-image': '배경 이미지'
    }
  },
  traitManager: {
    empty: '설정을 바꾸려면 먼저 캔버스에서 요소를 선택하세요',
    label: '요소 설정',
    traits: {
      labels: { id: '아이디', alt: '대체 텍스트', title: '툴팁', href: '링크 주소', target: '열기 방식' },
      attributes: {
        id: { placeholder: '예: intro' },
        alt: { placeholder: '이미지 설명' },
        title: { placeholder: '마우스를 올리면 보이는 설명' },
        href: { placeholder: '예: https://example.com' }
      },
      options: { target: { false: '현재 창', _blank: '새 창' } }
    }
  }
};
