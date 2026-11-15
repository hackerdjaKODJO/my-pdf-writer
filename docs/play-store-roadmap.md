# Play Store 출시 로드맵

> My PDF Writer v0.1.0 → Google Play 정식 출시까지의 체크리스트와 단계별 계획
> 현재 상태: **릴리스 서명·R8·AAB·스토어 자산 완료**, Play Console 등록/테스트 미착수

---

## 0. 현재 위치

| 항목 | 상태 |
| --- | --- |
| 핵심 기능 구현 | ✅ |
| 에뮬레이터 스모크 테스트 | ✅ |
| 자동화 테스트 | ⚠️ JVM 단위 테스트 11개 통과, UI 계측 테스트 없음 |
| 릴리스 키스토어 | ✅ 로컬 생성·백업 (저장소에는 미커밋) |
| R8/리소스 축소 | ✅ APK 7.9 MB / AAB 10.5 MB |
| AAB 빌드 | ✅ 서명 검증 완료 |
| 개인정보 처리방침 | ✅ GitHub Pages 게시 |
| 스토어 등록 자산 | ✅ 1080×2160 스크린샷 7장, 피처 그래픽, 512px 아이콘 |
| 저장소 공개 | ✅ `hackerdjaKODJO/my-pdf-writer` (public) |
| Play Console 앱 생성 | ❌ |
| 비공개 테스트 | ❌ |

---

## 1. 출시 전 필수 작업

### 1-1. 서명과 빌드

- [x] 업로드 키스토어 생성 (RSA 2048, 25년 이상 유효)
  ```bash
  keytool -genkeypair -v -keystore upload-keystore.jks \
    -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
- [x] `keystore.properties` 작성 (git 커밋 금지, 로컬 백업 완료)
  ```properties
  storeFile=upload-keystore.jks
  storePassword=...
  keyAlias=upload
  keyPassword=...
  ```
- [x] `versionCode`/`versionName` 확정 (`0.1.0` → `1`/`0.1.0`)
- [x] `isMinifyEnabled = true`, `isShrinkResources = true`로 릴리스 최적화
  - PDFBox-Android `-dontwarn com.gemalto.jp2.**`, `-keep class com.tom_roush.pdfbox.**`
- [x] `./gradlew bundleRelease`로 AAB 생성
- [x] 릴리스 APK를 에뮬레이터에 설치해 열기·필기·검색·회전·내보내기 검증
- [x] 16 KB 페이지 크기 지원 확인
  - 네이티브 라이브러리 4개(`libandroidx.graphics.path.so`) 모두 16 KB 정렬
  - `zipalign -c -P 16 -v 4` 확인
- [ ] Play App Signing 활성화 (업로드 키는 Google에 보관)

### 1-2. 정책·신고

- [x] **데이터 안전성(Data safety)**: 답변 초안 준비 (`store/README.md`)
  - 파일은 기기에서만 처리, 서버 전송 없음
  - 광고/분석 SDK 없음
  - 향후 크래시 리포팅/분석을 추가하면 즉시 갱신
- [x] **광고 선언**: "앱에 광고가 없습니다" (초안)
- [x] **콘텐츠 등급**: 설문 답변 초안 (폭력/성/도박/약물 없음 → 전체 이용가 예상)
- [x] **타깃층**: 대학생 이상. 아동 대상 아님
- [x] **뉴스 앱/정부 앱/코로나 앱**: 해당 없음
- [x] **권한 선언**: `INTERNET` 없음, `MANAGE_EXTERNAL_STORAGE` 없음, `CAMERA` 없음
  - 릴리스 APK 권한 확인: 앱 자체 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`만 존재
  - SAF만 사용하므로 저장소 권한 정책 심사 부담이 낮음
- [x] **개인정보 처리방침 URL** 준비: <https://hackerdjakodjo.github.io/my-pdf-writer/privacy-policy.html>
  - GitHub Pages(`/docs`)로 게시
  - 앱 내 정보 다이얼로그에서도 링크 제공 검토
- [ ] 개발자 계정 정보·연락처 이메일·웹사이트 정비
- [ ] 개인 개발자 계정의 비공개 테스트 요건(테스터 수·기간)을 Play Console에서 확인
- [ ] Play Console에 데이터 안전성/광고/콘텐츠 등급 설문 실제 제출

### 1-3. 스토어 등록 자산

| 자산 | 규격 | 상태 |
| --- | --- | --- |
| 앱 이름 | 최대 30자 | ✅ 후보: `My PDF Writer – 전공 필기 PDF` |
| 짧은 설명 | 최대 80자 | ✅ "광고 없이 오프라인으로 강의자료 PDF에 필기하고 정리해 내보내세요." |
| 자세한 설명 | 최대 4,000자 | ✅ `store/README.md` 초안 |
| 앱 아이콘 | 512×512 PNG | ✅ `store/icon-512.png` |
| 피처 그래픽 | 1024×500 PNG/JPEG | ✅ `store/feature-graphic-1024x500.png` |
| 휴대폰 스크린샷 | 2~8장, 320~3,840px, **최대 2:1 비율** | ✅ `store/screenshots/` 1080×2160 7장 |
| 태블릿 스크린샷 | 선택 | v0.2 태블릿 레이아웃 이후 |
| 프로모션 동영상 | 선택 | — |

**스크린샷 메모**

- Play 스크린샷은 최대 2:1 비율을 권장합니다. 1080×2160(1:2)으로 제작했습니다.
- 첫 2장이 전환율에 가장 큰 영향을 줍니다.
  1. 홈 + "광고 없이, 완전 오프라인"
  2. 필기 화면 + "강의자료 위에 바로 필기"
- 3~7장: 페이지 편집, 회전, 검색, 메모, 내보낸 PDF.
- `python tools/make-store-assets.py`로 다시 생성할 수 있습니다.

### 1-4. 자세한 설명 초안

```
강의자료 위에 바로 필기하고, 정리해서 PDF로 내보내세요.

My PDF Writer는 대학생의 전공 공부를 위해 만든 오프라인 PDF 작업대입니다.
광고도, 계정도, 인터넷 권한도 없습니다. 문서와 필기는 기기 안에서만 처리됩니다.

■ 필기
- 펜, 형광펜, 텍스트, 사각형, 화살표, 지우개
- 8색 팔레트와 굵기/글자 크기 조절
- 실행 취소/다시 실행

■ 페이지 정리
- 왼쪽/오른쪽 회전, 위/아래 이동
- 페이지 삭제, 빈 페이지 추가
- 원본 구성으로 한 번에 초기화
- 회전해도 필기 위치와 방향이 함께 유지됩니다

■ 공부에 맞춘 기능
- 페이지별 메모와 메모 목록
- PDF 텍스트 검색, 페이지별 결과와 바로가기
- 최근 문서 목록

■ 내보내기
- 화면에서 본 그대로 주석이 새겨진 PDF로 저장
- 한국어 텍스트, 형광펜 투명도, 회전된 페이지까지 동일하게
- 주석이 없는 페이지는 원본 PDF 그대로 유지

■ 프라이버시
- INTERNET 권한을 선언하지 않습니다
- 광고·분석·계정 SDK가 없습니다
- 파일은 Storage Access Framework로 사용자가 직접 선택합니다

지원: Android 8.0 이상
```

---

## 2. 테스트 트랙과 출시 절차

```
내부 테스트 (Internal testing)
  │  본인/팀 기기, 빠른 피드백
  ▼
비공개 테스트 (Closed testing)
  │  지인 12~20명, 2주 이상
  │  크래시/ANR/내보내기 실패 집중
  ▼
공개 테스트 (Open testing, 선택)
  │  더 넓은 기기 다양성
  ▼
프로덕션 (Production)
  │  단계적 출시(Staged rollout) 10% → 50% → 100%
```

### 출시 전 QA 매트릭스

| 축 | 확인 항목 |
| --- | --- |
| OS | Android 8, 10, 12, 14, 15, 16 |
| 제조사 | Pixel, Samsung, Xiaomi |
| 폼팩터 | 폰, 태블릿, 폴더블 |
| 화면 | 다크 모드, 글꼴 크기 1.3x/2.0x, 가로/세로 |
| 파일 | 한글 파일명, 1MB/50MB/500MB PDF, 스캔 PDF, 암호화 PDF |
| SAF | Downloads, Google Drive, OneDrive, Samsung My Files |
| 작업 | 열기 → 필기 5종 → 지우개 → undo/redo → 회전 → 이동 → 삭제 → 빈 페이지 → 초기화 |
| 저장 | 메모, 검색, 내보내기, 내보낸 PDF 재열기 |
| 복원 | 프로세스 사망 후 재실행, 최근 문서에서 다시 열기 |
| 접근성 | TalkBack 주요 버튼, 색 대비, 터치 타깃 |

---

## 3. 단계별 로드맵

### Phase 0 — 출시 준비 (v0.1.x, 2~3주)

- [x] 릴리스 서명, R8, AAB
- [ ] 개인정보 처리방침, 데이터 안전성 신고 (답변 초안은 완료)
- [x] 스토어 스크린샷/피처 그래픽/아이콘
- [ ] 내부 테스트에서 크래시·ANR 0건 만들기
- [~] 자동화 테스트 최소 세트
  - [x] `PageSpec.normalizeRotation`, `PageTransforms`, `DocState.normalized` (11개)
  - [ ] 문서 열기 → 내보내기 계측 테스트 1개

**종료 기준**: 내부 테스트 1주, 치명 결함 0, AAB 설치 성공, 데이터 안전성 검토 완료.

### Phase 1 — 사용성 강화 (v0.2, 4~6주)

- 페이지 썸네일 그리드
- 텍스트 선택·복사 (PDF 텍스트 레이어)
- 이미지 삽입
- 자동 저장 표시/복구 안내
- 태블릿·폴더블 2단 레이아웃
- 접근성(TalkBack, 색 대비, 터치 타깃)
- 내보내기 옵션: 압축/해상도, 주석 페이지 JPEG
- 시작 시간 단축(Baseline Profile 검토)

**종료 기준**: 비공개 테스트 2주, 크래시 없는 세션 99.5% 이상, 내보내기 성공률 99% 이상.

### Phase 2 — 공부 기능 확장 (v0.3, 6~8주)

- 스캔 PDF OCR (ML Kit, 선택 다운로드)
- 암호화 PDF 열기
- PDF 폼 필드 보기/입력(범위 결정)
- 클라우드 연동은 SAF 기반으로 유지 (Drive/OneDrive)
- 영어/일본어 UI
- 온보딩(첫 실행 3단계)

**종료 기준**: OCR/암호화 PDF의 명확한 지원 범위 문서화, 공개 테스트 피드백 반영.

### Phase 3 — 정식 출시 (v1.0)

- 프로덕션 단계적 출시
- 스토어 A/B 테스트(아이콘/첫 스크린샷)
- 리뷰 대응 프로세스
- 분기별 유지보수

---

## 4. 수익화 검토

현재 v0.1은 **광고·결제·계정이 없습니다.** 이는 강력한 신뢰 메시지이므로 초기에는 유지합니다.

| 옵션 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| 광고 | 초기 수익 | 신뢰 메시지 훼손, 권한/SDK 증가 | ❌ v1.0까지 보류 |
| 일회성 Pro | 명확한 가치, 구독 피로 없음 | 기능 잠금 설계 필요 | ✅ 유력 (OCR/클라우드/고급 내보내기) |
| 구독 | 반복 수익 | 대학생 가격 민감, 이탈 | ⚠️ 동기화 기능 이후 |
| 기부/후원 | 신뢰 유지 | 수익 예측 어려움 | ⚠️ 보조 |

Pro를 도입한다면:
- 무료: 필기·페이지·메모·검색·내보내기(현재 범위)
- Pro: OCR, 암호화 PDF, 고급 내보내기, 클라우드 동기화, 다국어
- 결제 추가 시 `BILLING` 권한, 데이터 안전성, 환불 정책, 가격 표시를 갱신

---

## 5. 지표와 운영

초기에는 **Play Console Vitals만** 사용합니다(별도 분석 SDK 없음).

| 지표 | 목표 |
| --- | --- |
| 크래시 없는 사용자 비율 | ≥ 99.5% |
| ANR 비율 | < 0.3% |
| 내보내기 성공률 | ≥ 99% (자체 이벤트 없이는 리뷰/문의로 추정) |
| 설치 후 1일 잔존 | 초기 벤치마크 설정 |
| 리뷰 평점 | ≥ 4.3 |
| 지원 응답 | 48시간 이내 |

- 크래시 리포팅이 필요하면 Firebase Crashlytics 또는 Play Console의 기본 수집 범위를 검토하고,
  데이터 안전성 신고를 갱신합니다.
- 리뷰에서 "내보내기가 안 된다/글자가 깨진다"가 나오면 최우선으로 재현합니다.

---

## 6. 법적·라이선스 체크

- [ ] 앱 자체 라이선스 결정 (MIT/Apache-2.0/독점 중)
- [ ] 오픈소스 고지(Compose, PDFBox-Android, Kotlin 등) 화면 또는 문서 추가
- [ ] PDFBox-Android: Apache License 2.0
- [ ] Jetpack Compose: Apache License 2.0
- [ ] 사용자가 연 PDF의 저작권은 사용자에게 있음 — 약관에 명시
- [ ] 개인정보 처리방침에 "파일은 기기에서만 처리, 서버 전송 없음" 명시
- [ ] Play 정책: 지식재산권, 사기, 악성 코드, 사용자 데이터 정책 준수

---

## 7. 출시 당일 체크리스트

- [ ] 프로덕션 AAB 업로드 및 검토 통과
- [ ] 스토어 등록정보 모든 언어/스크린샷 미리보기 확인
- [ ] 데이터 안전성/광고/콘텐츠 등급 최종 확인
- [ ] 다운로드 링크로 실제 설치 → 열기 → 필기 → 내보내기 1회
- [ ] 지원 이메일 모니터링
- [ ] 단계적 출시 10% 시작
- [ ] 24시간 후 크래시/ANR 확인 → 50% → 100%

---

## 8. 참고

- [Play Console 데이터 안전성](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Play 스크린샷 규격](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Target API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878)
- [16 KB 페이지 크기](https://developer.android.com/guide/practices/page-sizes)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)

> Play 정책과 요구사항은 수시로 바뀝니다. 제출 직전에 Play Console의 안내를 다시 확인하세요.
