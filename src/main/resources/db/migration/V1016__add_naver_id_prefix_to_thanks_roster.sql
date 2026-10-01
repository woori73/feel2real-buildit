-- ============================================================
-- V1016: newstart_thanks_roster 에 네이버 ID 접두부(보조 식별정보) 추가
--   · 기존 V1015 / RAW 503 / roster 는 수정하지 않는다 — 컬럼 추가 + 값 반영만 한다.
--   · naver_id_prefix 는 Cafe Studio 화면에서 마스킹되어 노출되는 네이버 ID 접두부다
--     (maskedMemberId 에서 후행 '*' 제거). 전체 네이버 ID 가 아니며, 네이버 OAuth subject 와도 다르다.
--   · 이 값만으로 THANKS_MEMBER 를 자동 부여하지 않는다 — 본인확인 보조정보로만 쓴다.
--     같은 접두부가 여러 네이버 ID 에 존재할 수 있어 단독 확정 식별자가 아니다.
--   · 적재는 기존 row 를 (snapshot_date, cafe_member_tag) 로 찾아 갱신하므로 idempotent 하다.
--   · source: data/newstart-cafe/thanks-members/naver-id-prefix-2026-10-01/naver-id-prefix.json
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `newstart_thanks_roster`
    ADD COLUMN `naver_id_prefix` VARCHAR(64) NULL COMMENT '네이버 ID 접두부(maskedMemberId 에서 후행 * 제거). 전체 ID 아님 — 본인확인 보조정보' AFTER `cafe_member_tag`;

UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ssgw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gn5wjzdu86bk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wlgp'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '484xqzb11bb3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yang'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vuj03vgheeij';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'to'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ivleileuw6iz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hanr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '97dhp2b4soha';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hwam'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mvfjqsrliwng';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gksm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hapmwjb269f4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'king'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'nit6pysrq1el';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hodo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'foy9suc0eaww';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'k4'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xuclbhyh6ske';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ican'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'f2mgfud4a18a';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dmsw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'yq1ms0jpscdj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rmad'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'tl18wurxc9j8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ssou'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'q8n9wgvacn5f';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dmc2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'g86baykxh48b';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'suny'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7p61xr5qd4er';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kmro'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5bcs1yyz4yvx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sej2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fbdap7jqcx2s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mba0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7s08rpv4d848';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'akdl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n8owpf39fgei';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jung'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kv1iftblskk1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ljy9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'j7clsx74azd6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'vlaw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gfor8d5ypk7i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'seoy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gd0njqvod93t';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'heji'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'd6fhr32yih8i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'po'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jlwoxrgas03q';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'zeey'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'um6tyy7b3rdn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ensi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fr4j6miytt4k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ch19'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'tstz4dds1vb7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mari'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 's8v9kudl7zmc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'knjs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'lgebu67p9v8k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tmdg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1xk9s99szy6z';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lsh9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'yfjb397knbrh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mamu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'o4ucf4h3ignk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'inlo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'v88014m831pa';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rnru'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xa9sf4qp74m3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hyee'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9lynwnrevq9d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'los5'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5hbm8zmifotd';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jso1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'eejwsevzk58x';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'aka_'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3pufk8w3q51o';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'seul'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bu440pndz24i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nylo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'lakabfcajrkb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dmst'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'j3h2ha6ni3mk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'smkc'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 's98pbntepuck';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'khnn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vc1m0qf2omv2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kevi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'dh8mz8z9xgk1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hjjw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7rwi7uk4yngu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'qhal'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gm15isnmozwj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'salt'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6bqu6e4kvo4m';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'myso'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'v980n9aaghc5';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'soen'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'i1sgvqr5vrgz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'self'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mbhiv42cjt2z';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'al'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3kv6q4erx1ap';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'a8ho'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'grs5q2uq1kqt';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ccag'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'o143da3y5n2n';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'iyoo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wdvcbhvsm3gq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'you0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'k73dexcmb7vo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'km'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rkiwacmiesrz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ye'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ibgx28887gss';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sunm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3p8k3i37kraa';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'me'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'a65v1eukxqv1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sodd'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8dskgp5wflqs';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sms3'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1n190977642i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yong'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2v0svup9k8sh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'crom'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5eppjs4ldw4s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ly'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kbp9x0zfbrs2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pool'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hpe4u7lwg2qg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'zmre'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gxfgcseik7mb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'psj8'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n8mnm7ffh5mj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'com7'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'x7sitvi4pxfh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'test'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'u17n9xv3c3cw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'espu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'spj55zhqrybv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ason'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ya65pvxom3vn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bibi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8o0q0lt2dr8m';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tour'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0b1xyjd063du';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'guru'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'dvjl24xt3itu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'amie'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5efubem3t8uy';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gp'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'edz1lx79ovz0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jjjm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kmk7ptluzx40';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kimj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'uw4xxfu5gact';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'eu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'tiodu91lokih';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5ybk89aky6m9';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kco0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'oz8u08r33q39';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hwan'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'nisau4ccncnw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ukit'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'pecvkpir06vv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'betm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fnzkdd8r44gn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dist'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'cvfbvwxwbp92';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jp6z94j7ek7q';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jjin'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'po0ecjnn7t9k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ss78'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 're9dz4y8f6fi';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'isfp'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'izm51i8cstki';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sos9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4lirzcjsy80a';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dear'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '798cog3dzaqg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sung'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'opncl2lg5bzl';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hsy6'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jy1db7s0171s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yoro'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 't4ztlj7e96fq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rlat'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ze0ve6x06k4x';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'holy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'cjqg0vptplxb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hkc7'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7tnbe8c5nn4n';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cnal'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wymuixadvi68';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mich'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hds480yn300h';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'xodi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fnbe2yqsa22d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yub1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '62otyvd3fg4x';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'chaw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bsxno3xvtpvx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ehdr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'tq7fj27bhqyy';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sseu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7nou0dpcl10e';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'valu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'h9gi793gus9s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'shin'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1yovqz5opioe';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '82'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2pvy8h22zyct';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jkll'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '11g8nuav2pbq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'plum'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vq1fhfpb1grj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ldh0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rxyek8o6ig83';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mmmh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ucdasna5nydl';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'coco'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wdmka2vxz80g';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '8642'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z0fnkk5q8puv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'arte'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'b6ip6ld8vzkz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'leee'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bdx53xyvjv3t';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hihi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'v5qlvgg6jz06';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mrsh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '88tafo4fweh7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'elle'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2binmwnii6ih';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'chol'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fx6dswrc4m3g';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tjeh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 't2xtm674v7a3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hoya'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'lw3zrqbh9hv9';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'fore'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xbhzhbp4djef';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wldu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'q2ikqw28pjqz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'top5'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'trvy9xfc4a0n';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ms'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'eh1iaeesw86w';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '213p'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '21qhabd5wae2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gala'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'x1sa9dtcp24s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'minh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'oqn16daexnhg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kis7'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '69y3hkr4j1l3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'i24f'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'drjf05jvr4e1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'me'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '527pd009qj7k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n6o2lqgcnh8q';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'face'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'de7whcw6znmj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'it'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '73ccgcuwu64e';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'beee'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0btqz3g3nddo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hunj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'npnl62no7tjx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'oroh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8p3pjtdw5ccz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wefks269pgmp';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '0486'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fw956bem268c';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'love'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'y7hmzgchabvg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'az'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '790wax4meihs';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'doji'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ya4r7t0jyg2i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'shin'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xl4pc0apfe9m';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tmdg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5h00tduk2ap5';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ka'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gk7akc9y15ne';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nice'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7xqt59g17ase';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kimk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'g677fsvd1mnf';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kjjd'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ha16wr3h4j6y';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kang'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'df212346lm30';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lchh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6cmr38nd9of1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jek1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '80xxwicqa9op';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wlsw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm7kbwspmlr25';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'show'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jj1jsca8zxjg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'leeh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xq2rgoi1j6d3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jsw1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'inatnypkhoyy';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'x1asqeezyijw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rlat'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'q6zwj0k8g8qo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mrho'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hsvmqxsnz4fw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jinh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'atpv8war8jvl';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'zeus'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n7bl5hhyo7gc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ky'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'k2ufp2786i0s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sumi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mktl5kd60n0j';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'spac'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c2aqrrymugpa';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jyj3'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mnuze9j2yofq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'flyg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'uzl6pz8vhbr7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ls'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fnwzf3i54udb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tera'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c4805k453yis';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kh16'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vcvgktduk6qx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ones'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hbb2ejgxwpp1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kfun'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'l2eb66y7y29d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'judi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2lbfwlpfin8a';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cki2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ltk1sv40gswp';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jhc4'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'eor06kz8ypcf';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'papa'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'aapeqoqjlrxw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hsej'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c4qvajvc4kfe';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'harh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2iicjm7whirb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dood'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'zt2b2r47a2de';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'unjo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2hz7sn2h8p7r';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'modi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '57zbgrdudnsz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '2486'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rq3t52gz3syr';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'love'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'r8v53eep3epa';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rid2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qze433de3f2t';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kwh5'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vpaphj2k2mwo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ssy2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 't95we5ut8w3p';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ck'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z9ku0ktdw471';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sjle'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'a4n0ymhg5e3s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'toma'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'd2vti0tki1pt';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dalg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'waz4ml2j2cto';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'khs0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'yn0t4bn3sv5y';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bloo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9zpglgrv3jqv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '6248'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'g6t6gu8auxaz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dubu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qwb9ly9zsjy6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'khei'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '04yscchc8nrk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xzejo2uc7xhb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'brad'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rukpo3pcvk3s';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pian'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rrbruti80ex0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'knw1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '73k0vj44nrkj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'erid'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'chvwcrgfo9mu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hesl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '728ytgroa9b9';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '777b'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xijq77fnhy1c';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jull'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'x4g0o0df53c8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jenn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5u3ygevi6m30';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'momo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'casdolxsfjbr';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'para'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n0tabeofitz5';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'apas'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0xv34fqso5oo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jiho'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rhn1h1ksnv75';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'suny'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'td5xr2tngg4r';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wiwi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2eitizxzufez';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nara'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1xyujv1m3t4j';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'fu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8r7p172vyqbd';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '3487'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wmaqn9ftgvyc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'abck'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8m9bgarridfx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jel9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ajygsz0q94hw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kkj9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'd7xc6hcv5kr8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jiho'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'h1cwe9jvr1bv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'chil'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '71v79rku0vs7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cute'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qkvf7zrrkrz6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rg9vxmdkj4tt';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'todn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qbogzrrlwi2a';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'c4'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hl76k4lfzw2u';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rlfg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'i5iteydbbzs2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hale'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '39kci69vehtv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kiki'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'p6ttxg4jh0pb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'misu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'o7sksw0pfhp2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '37e55jfiny23';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'leeg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0tbrgi2i5gea';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'saga'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wcrlwmt5qg0q';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kjh2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2cb7m6nxallt';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kinb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ozm8qefkiwp0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'c3'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'yd7tdoibpqym';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wsyb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'htr6dp2vahfm';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lion'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wyxll4f7tit7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kimj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4ujysxungyls';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cgpi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ejnqkpgfczor';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ks'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'l753pkffkfl1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5yz40u3gd86v';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kisa'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5nawwkxuxu6x';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '0928'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'w8l51gp5xuhz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kbj2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6ptf9dexh8g3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'djxk8e9edmvj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rlad'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fqc0p4c6d93l';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'supe'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fkkbpjakcgak';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sung'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c9aalvhp2tll';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'baba'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '98wzcgxnkv6i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tkdw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm02fiez03mg6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mark'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4cz6fuzf2r3r';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ttop'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8x7vk3b0rqfy';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'samd'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3akdnhltepbn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lott'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '87zvy5sey9cs';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jieu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm6xg2mwbsxxu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'conh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'h3nmhygvzvyk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ibbc'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '147g1mgsfttk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'fr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wnfk19523m2c';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kjhs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'oz82eg8ayhz3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sung'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'i6p4gfokzmmx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kywo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'nxyu6tg57bpd';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kiopzumr3zcf';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'skfk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3ypvnev4ez4u';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'medi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vri738g2lktn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jinl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bn61oz4knsjn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'uphi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'sym7hr0ycu1k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gorg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vcld45lf77uu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wndy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mwwl5djba1tc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'silv'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'iq5x627ovwvp';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bunn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '87ndlhwoy1pk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wool'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7a3tmh3qhefg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hoho'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xczwwo8d9wap';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'roda'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '37hh4p869t1o';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rhrn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'l8sxqmyrosv9';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hica'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rp7ej5xxuuib';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'spdl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'add8nxa4v0z0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'oc'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4savq3wbhyd2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kzxy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z0flhg0n619b';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'b3vhgfdzx07f';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dain'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'd0ihtpz86oc6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'swee'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9j6vugzw3m48';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'frui'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kor3v5kpxv2w';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pt12'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8bcgxchxx375';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'k521'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hm4j1b0eof1r';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tngk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7zgf8sk53jxn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'july'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8hicjri7p2ro';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jbs1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'iem8izj85rmw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wowp'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3aqsccyyvuh2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm3dpd65n1zo7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'good'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'yspnghy1s0hq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dlqh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0kgb895ybzjj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'diso'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z980m7r6ln3q';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kyoj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'h535fg66fsip';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rain'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'puon8d6f83s4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2mitno9v43pz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ok'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3vpw3su6rzrg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'shr7'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qg3v6kxy5sca';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wans'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'iscg3yqmgkd5';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tina'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c7g0f7jqtuni';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nbiz'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bsvbje0t4ij0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'with'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'aswqxw46cugd';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'coco'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'awztne9tnwx1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'at'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ltnpf4zeu0p3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sele'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'sbvp9g86f0dq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'modo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ht22k0c9zlhw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'iqiq'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'trd1wkg9cxqy';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ss'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'v2d5pls2qyws';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'chj2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2x5xnw4o88fq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dltm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'uoaoi3kqr3vo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dbst'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jsihojp7egvw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'foxl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'g5d5tdsadizc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'baby'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rrj6aa3l6zua';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wlsu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8fsp0tlpjxn3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'simp'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'w86bkoha2pnz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'psy9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0l5ux81eudfh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cybe'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'senqciy520d2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'qudg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2vt1mtnlexfw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cjh5'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bqfr5cauk38n';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'toma'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mgi5n9mknz54';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'euis'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'cupfqqrlwc7d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'euns'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'pprzgftnzn1j';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'love'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'o7mscs6rkpic';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'post'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'everdgbo4ca6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kuro'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'rpcla4g31tkw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yooj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'q23ih0i1q26n';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mine'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mfh0pr8xdsos';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sms2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'opo1jx3ov2ho';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lolo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '260mrqv5g14h';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ziwo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vrc3w0ozhozs';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'csy7'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm23rj9yog7je';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yshs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'up9y2hcijlz8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'susu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ykmrigg8qfys';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'aker'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'oehge49gjsab';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wood'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fh2dagtjheiu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fo03ffvy2vmg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'fok2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'swuefsp1kp1u';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jisu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1es2vc71m327';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kkaa'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gv9mh73o2tzf';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'miny'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fvxef1vtk5po';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'eh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'wyd8jjzii4ve';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'we'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n2sajjdoewlw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'midl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5fbtimcecfhm';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'inga'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n715t8rcfm30';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'youn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'l8ihu74nyi65';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kmj8'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6jw9ds87rv5d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rotc'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '69sh38bfyn7k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'coc0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '136yegiojboj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rani'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '83yfcxm1f7j3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jihy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'dk7mdnkkpme8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'desi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'f7kmf9ata9mm';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'fkdl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4iccv6vwdre4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'soo4'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'emjzix5eiimh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mych'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'aa2vd9i5d360';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'qkd1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8ntncyqp9jyk';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'xjbvgbdrktwn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wkda'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'dp573cedy6gv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'love'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hsmlg6ign46b';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'door'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ow7do0i4ck0i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'woju'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jbtj775lcsf4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'skal'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2jvhmhd7y7cc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gara'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'k1dv1w5c1xqq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'drea'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6c520nkv43nx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sk'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5gab4td9j0fn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'musu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm7luh53gcv7d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wy'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ghivzl27vb22';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wona'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'n3n76pf2z02v';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'leeb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6hn2gkvb1zwb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ik'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3v15zevt2l0c';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pete'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7kyrjopn12ue';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'limm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'r2nfu9i8xgh8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sc_s'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'sj9d2waz1jzx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'puff'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0mfrv4n43z88';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kkbs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1vmkps97to8w';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'reus'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'uq8zsg3bmcgv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'good'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hv9t6ticv3ke';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hooo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'cghoptjpb2ep';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wjg1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fgfx56hbbb1y';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rnjs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1qufb8ilmcyx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'myla'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '1fsamhcb7kbj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'adad'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0pzeolyfiqsm';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yang'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5da3up210mjf';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'zg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'edezwtpmhtc9';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dakb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'lzk85j1dy6is';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sunt'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '24iztldqsvxb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'soni'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z27llcocvyzz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jdy9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'zaw58znbdu1h';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ju'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'opwkd7xpreln';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ba'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3o8b0wmcw3n5';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jina'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'o09p0nmh0mj2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vwu7scmv9ir8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wlq5'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'r4ldls2gamf1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '80bo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c52nrett27v8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'uiui'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'pg2dvjx7qsak';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kej4'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2eiyeudxj1lv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ys'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gub9d1snvxmu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ydhs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jqrxub2qksd3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'adre'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ordcwtgien8t';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ball'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '76rnwokbd2ae';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'youn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'sstvms72gpbg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'heun'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6tswweej8wge';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hoon'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'q5u09xxb88de';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'okha'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z2hqrz16qqny';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'a174'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3ijvwd0e5k6k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jung'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qkbu1m59mvha';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mybe'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6ujloq6kjbqx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tks1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'moxetbhhuugd';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pose'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'eloj3xtq8ke4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'club'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kp78tejgg2l4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'them'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'tlxrtpf784mh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'king'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9czcb7rbu3cz';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'moat'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '6i959pqsey2t';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jk08'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vpyfomertc80';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nada'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'sqhgc570sacy';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'naam'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '70actpuc6b2d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nana'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'unwr9qjngjrx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rand'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'dhnu8s6v2049';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gn7qz6v7t7ki';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'l9zifqvwoi4y';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cheo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'pxscskevbb3o';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kdw6'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jmxjdqgzw4yl';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ukch'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'r0kfso9iv1fb';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dmsg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'f0n29a10074u';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'rha0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'a98o99kbe1jc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'scan'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'a35cp4aslauw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dkfl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 't95rxmtdnlck';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'vndr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4ti7envla2y5';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ones'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'qtpulh0ifjva';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ey'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bquojzt0kjky';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2802t2yd69rh';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bear'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '89shnk2ibnaj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'duds'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9h1rgushfsqa';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mega'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9m3mqiu4x4zw';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yyan'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '2u7nxd0t9zv7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'aji1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bw48loj3n2v2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hgs2'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'f09oue11sd8r';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kj70'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vkfre073p7jq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'be'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'dm2269gedusq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hybr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jzfq2rqxzw12';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dtf1'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'h639oy1beyqa';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'huhu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7nhpvs14q9wn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bett'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'jnnn2txh4ew1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'foot'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'j32xmu5zjg1a';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0gvys3v72yj8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'jose'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c9jrggrxvo9k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wlsd'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'crfh7r4vqdz2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ppps'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ix82419cbsjs';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'bjoo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'uikufpn6kp90';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lsw6'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'iz4zq5kw7lgp';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 're'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'poda7su5oag1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mooo'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'hfqcma92sbq4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'suns'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ro0zuhbuuu2v';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dnjs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '5zvnxka1k4wn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'chbs'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'lmtbvi9pwjcm';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'zoon'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'szhufucbnxuq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hsg9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '4fba0mp8db5t';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '6630'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '718bk68l6fje';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kiss'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'ecrzhs4oexn1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'yh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'y5s8r117i9b8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'eoru'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'oprzlyg30lnv';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'grat'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'gzxruyl1hs56';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'leei'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kg8vqthvevey';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'mnmb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'd5si8bm9fhx1';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'nakn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3gj17u5q6r0e';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hidi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vv465t2wcwhr';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ti'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '7klu35ooedbe';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wogu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'fb5cqkfni8mg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'recm'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'cvi40w6y6l9x';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'areu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vfdq3i9srdq8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'love'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '00dxqda47c8m';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ley0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'q8mj0aougpl4';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'who0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'lo1pfwu72qst';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sang'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'mg46pltq5cml';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'junw'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'cy3pdwnso6a2';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hana'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'chf2vdh8ial6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dg'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z9zjx1160h18';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'cj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'nxr6d0hqiztq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'btg9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z81ghdqktwxn';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kmin'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 's8dl98jqtwjq';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'engl'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '9cb46fycnbqx';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'brew'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '92u693cfkh3d';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hyun'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'crdkj2zauwf6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'ksn9'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 't84vry181mmi';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'haeb'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'zelx5ict2rfc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 's4'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'yc7teh2b9v4i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'swki'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'c8damz6fn68i';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wkdr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'aax8ivdtyxkj';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kh'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3u371e0abuy0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'paru'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '0f4ktlqqp007';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'wtop'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kot9m3iwtko3';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'misi'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '69f4ltvfexyu';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'sass'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'sq28xi95a4s8';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'lik0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '3rl6dnf187we';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'oliv'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '8gz2vtrjs70q';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'tkdr'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'o6z2lvd4e0oc';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'dkdu'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'vncadjzzk25k';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'me'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = '961mhq90ezxo';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kn'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'tq4xhgcpl4l6';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'alse'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kra8agbi0uft';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'etet'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'm2k9kcvqdbjg';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'kosp'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'g2mizxkag9e0';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = '0303'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bpvoz6qwr57j';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'blue'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'bjrckfgz1m8g';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'hyoj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'z65q07iyu3jr';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'gowj'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'a0jud71ry2x7';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'virt'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'usz08nvwi779';
UPDATE `newstart_thanks_roster`
    SET `naver_id_prefix` = 'pys0'
    WHERE `snapshot_date` = '2026-10-01' AND `cafe_member_tag` = 'kxd25pyuzt03';
