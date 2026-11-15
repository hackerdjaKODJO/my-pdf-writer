# 경쟁 앱 역공학 분석

> 대상: Google Play에서 PDF 리더/편집기로 배포되는 APK 5종
> 목적: My PDF Writer의 차별화 지점과 Play Store 진입 전략 수립
> 방법: 정적 분석(매니페스트·DEX 헤더·컴포넌트·권한·서명) — 런타임/동적 분석 없음

---

## 1. 분석 대상

| 앱 | 패키지 | 버전 | APK 크기 | min/target SDK |
| --- | --- | --- | ---: | --- |
| Foxit PDF Editor | `com.foxit.mobile.pdf.lite` | 2026.6.0.1026.0325 | 138.6 MB | 28 / 36 |
| PDF Converter: Editor & Reader | `com.pdfeditor.converter.reader.scantopdf` | 1.0.6 | 40.7 MB | 24 / 36 |
| PDF Reader - PDF Editor (Luminous) | `com.luminousbyte.pdfreader.pdfeditor` | 1.2.9-Luminous | 85.1 MB | 32 / 36 |
| PDF Reader, PDF Viewer (Office) | `com.trusted.pdfeditor.pdfreader` | 2.0.3-Office | 85.7 MB | 32 / 36 |
| PDF Reader: Edit & Scan (Nimbussoft) | `com.nimbussoft.pdfreader.pdfeditor` | 1.0.3-Nimbussoft | 85.0 MB | 32 / 36 |

## 2. 구조적 사실

| 항목 | Foxit | Converter | Luminous | Office | Nimbussoft |
| --- | ---: | ---: | ---: | ---: | ---: |
| DEX 파일 수 | 6 | 3 | 9 | 9 | 9 |
| 클래스 수 | 53,422 | 26,331 | 66,815 | 66,815 | 66,434 |
| 권한 수 | 20 | 24 | 21 | 21 | 21 |
| Application 클래스 | `com.fx.app.AppMain` | `com.pairip.application.Application` | `com.artifex.sonui.MainApp` | `com.artifex.sonui.MainApp` | `com.artifex.sonui.MainApp` |
| 런처 Activity | `com.fx.app.ui.AppActivity` | `com.ag.sampleadsfirstflow.ui.splash.SplashActivity` | `com.roosterx.featurefirst.splash.SplashActivity` | `com.roosterx.featurefirst.splash.SplashActivity` | `com.roosterx.featurefirst.splash.SplashActivity` |
| assets 수 | 116 | 124 | 160 | 160 | 160 |

### 2-1. Luminous·Office·Nimbussoft는 사실상 같은 기반 빌드

세 앱에서 다음이 **동일**합니다.

- Application 클래스: `com.artifex.sonui.MainApp` (Artifex MuPDF/Sonui 계열)
- 런처: `com.roosterx.featurefirst.splash.SplashActivity`
- assets 160개, DEX 9개, 권한 21개(집합도 사실상 동일)
- Luminous와 Office는 클래스 수까지 **66,815개로 완전히 일치**
- Nimbussoft는 66,434개로 381개 차이 — SDK/설정 차이 수준

패키지명·앱 이름·아이콘·서명 인증서만 다르고, 매니페스트에 등록된 컴포넌트 구성이
거의 같습니다. **동일한 화이트레이블 PDF SDK/앱 템플릿을 재브랜딩한 빌드**로 판단됩니다.

### 2-2. 광고·결제 스택이 앱의 상당 부분을 차지

세 클론 빌드의 매니페스트에는 다음 광고/중개 SDK 컴포넌트가 함께 등록되어 있습니다.

- Google AdMob (`com.google.android.gms.ads.*`)
- AppLovin MAX (`com.applovin.*`)
- Meta Audience Network (`com.facebook.ads.*`)
- Pangle / ByteDance (`com.bytedance.sdk.openadsdk.*`)
- IronSource / LevelPlay (`com.ironsource.*`)
- InMobi (`com.inmobi.ads.*`)
- Fyber / Inneractive (`com.fyber.inneractive.*`)
- Bigo Ads (`sg.bigo.ads.*`)

권한도 `AD_ID`, `ACCESS_ADSERVICES_*`, `com.android.vending.BILLING`,
`com.google.android.c2dm.permission.RECEIVE`, 설치 리퍼러 수신 권한을 포함합니다.
PDF 기능보다 **수익화·광고 중개·결제**를 위한 표면이 더 큰 구조입니다.

### 2-3. Converter는 광고 우선 템플릿 + 위변조 방지 래퍼

- Application이 `com.pairip.application.Application` — Google Play 무결성/위변조 방지 래퍼
- 런처가 `com.ag.sampleadsfirstflow.ui.splash.SplashActivity` — 이름 그대로
  "sample ads first flow" 템플릿에서 출발한 흔적
- Meta Audience Network, Pangle 컴포넌트 포함
- 권한 24개: `MANAGE_EXTERNAL_STORAGE`, `CAMERA`, `POST_NOTIFICATIONS`,
  `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `AD_ID`, 결제, 설치 리퍼러 등
- 40.7 MB로 상대적으로 작지만, 핵심은 변환·스캔·광고 흐름

### 2-4. Foxit은 무겁지만 진짜 편집기

- 138.6 MB, 53.4k 클래스, 6 DEX
- `MANAGE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`, `GET_ACCOUNTS`,
  `READ_PHONE_STATE`, 결제 권한 포함
- ML Kit·스캐너·클라우드(Dropbox 등)·서명 관련 구성 요소가 확인됨
- 데스크톱급 기능을 이식한 대형 앱. 기능 폭은 넓지만 설치·권한·학습 비용이 큼

## 3. 권한·신뢰 비용 비교

| 권한/기능 | Foxit | Converter | 클론 3종 | My PDF Writer |
| --- | :---: | :---: | :---: | :---: |
| `INTERNET` | ✅ | ✅ | ✅ | ❌ |
| `MANAGE_EXTERNAL_STORAGE` | ✅ | ✅ | ✅ | ❌ (SAF) |
| `CAMERA` | ✅ | ✅ | ✅ | ❌ |
| `AD_ID` / 광고 서비스 | ❌* | ✅ | ✅ | ❌ |
| `BILLING` | ✅ | ✅ | ✅ | ❌ |
| `POST_NOTIFICATIONS` | ❌* | ✅ | ✅ | ❌ |
| 광고 SDK 컴포넌트 | ❌* | 2종 | 8종 | 0종 |
| 앱 크기 | 138.6 MB | 40.7 MB | 85 MB | **~28 MB(디버그)** |

\* 정적 팩트에서 광고 SDK 컴포넌트가 확인되지 않은 것. Foxit이 광고를 전혀 하지 않는다는 뜻은 아님.

**해석**: 경쟁 앱 대부분이 `INTERNET` + 광고 ID + 저장소 전체 접근 + 카메라를 요구합니다.
대학생 사용자 입장에서 "강의자료에 필기하는 앱"에 카메라·연락처·광고 식별자가 왜 필요한지
설명하기 어렵습니다. 이는 **프라이버시를 제품 메시지로 만들 기회**입니다.

## 4. 기능 표면의 공통점과 빈틈

정적 분석에서 확인되는 공통 표면:

- PDF 열기/렌더링, 주석(펜·형광펜·텍스트), 페이지 관리, 내보내기
- 스캐너/OCR(ML Kit 계열 구성 요소) — 클론 3종과 Foxit
- 광고 제거/프리미엄 결제 — 클론 3종과 Foxit
- 클라우드/서명 — Foxit 중심

빈틈:

1. **"전공 공부"라는 좁은 사용자 정의가 없음.** 모두 "PDF 리더/편집기/스캐너"라는
   범용 카테고리로 싸웁니다. 강의자료·전공 교재·시험 대비라는 맥락은 제품 언어에 없습니다.
2. **필기 후 "정리" 흐름이 약함.** 페이지 회전·삭제·빈 페이지·메모·검색을 한 흐름으로
   묶어주는 앱이 드뭅니다. 대부분은 주석 자체에 집중합니다.
3. **내보내기 충실도에 대한 불안.** 한국어 텍스트, 형광펜 투명도, 회전된 페이지에서의
   주석이 PDF 뷰어마다 다르게 보이는 문제는 사용자 불만의 단골 소재입니다.
4. **신뢰 비용.** 광고·계정·과도한 권한은 "내 강의자료를 어디로 보내는가"라는
   불안을 만듭니다.

## 5. My PDF Writer의 포지셔닝

### 한 문장

> **"광고 없이, 인터넷 권한 없이, 강의자료 위에 바로 필기하고 정리해 PDF로 내보내는 전공 공부 작업대."**

### 차별화 축

| 축 | 경쟁 앱 | My PDF Writer |
| --- | --- | --- |
| 사용자 | 범용 PDF 사용자 | 대학생·전공 공부 |
| 수익화 | 광고·구독·결제 | 없음 (v0.1) |
| 권한 | 인터넷·광고 ID·전체 저장소·카메라 | SAF + 앱 전용 저장소 |
| 크기 | 40~139 MB | ~27 MB(디버그) |
| 내보내기 | 뷰어별 차이 가능 | 화면과 동일한 페인터로 평탄화 |
| 페이지 정리 | 기능은 있으나 분산 | 회전·이동·삭제·빈 페이지·초기화를 한 시트에 |
| 공부 맥락 | 없음 | 페이지 메모 + 검색 + 페이지 점프 |

### 경쟁 앱에서 배울 점

- **홈의 즉시 행동**: "PDF 열기"와 "최근 문서"를 첫 화면에 크게 노출 (이미 반영)
- **도구 접근성**: 펜·형광펜·텍스트를 한 줄에 두고, 색/굵기는 즉시 변경 (이미 반영)
- **페이지 썸네일**: 현재는 목록형. v0.2에서 썸네일 그리드 검토
- **내보내기 완료 피드백**: 저장 후 "어디에 저장됐는지" 더 명확히 안내 필요

### 피해야 할 것

- 광고 SDK/광고 ID 권한: 초기 신뢰 메시지와 정면 충돌
- `MANAGE_EXTERNAL_STORAGE`: Play 정책 심사 부담 + 사용자 불신
- 스캐너·OCR·클라우드의 동시 확장: v0.1의 좁은 포지셔닝을 흐림
- 과도한 온보딩/계정: 첫 실행에서 PDF를 바로 열 수 있어야 함

## 6. 리스크와 대응

| 리스크 | 내용 | 대응 |
| --- | --- | --- |
| 기능 복제 | 화이트레이블 클론은 기능 추가가 빠름 | 사용자 맥락·UX·신뢰·한국어 품질로 차별화 |
| PDF 충실도 | 폼·서명·암호화 PDF | v0.2에서 암호화/폼 지원 범위 명시, 미지원 시 안내 |
| 내보내기 용량 | 주석 페이지를 2x 래스터로 임베드 | 주석 없는 페이지는 원본 벡터 유지, 필요 시 해상도/압축 옵션 |
| Play 심사 | 권한·데이터 안전성 신고 | `INTERNET` 미선언, SAF만 사용, 데이터 수집 없음으로 신고 |
| 검색 품질 | 스캔 PDF는 텍스트 없음 | OCR은 v0.3, 미지원 시 명확한 안내(이미 구현) |
| 성능 | 대용량 PDF·고해상도 페이지 | LruCache, 렌더 크기 상한, 백그라운드 조립 |

## 7. 분석의 한계

- 정적 분석만 수행했습니다. 런타임 동작·실제 광고 노출·결제 흐름은 검증하지 않았습니다.
- 앱 이름/아이콘은 빌드별로 다를 수 있어, 패키지명과 매니페스트 기준으로 식별했습니다.
- DEX 클래스 수는 헤더 집계이며, 난독화·공유 라이브러리 포함 값입니다.
- "화이트레이블" 판단은 매니페스트·Application·런처·assets·클래스 수의 동일성에 근거한
  **기술적 추정**입니다. 각 개발사의 계약 관계를 확인한 것은 아닙니다.
- 광고 SDK는 매니페스트 컴포넌트 기준입니다. 컴포넌트 없이 코드로만 포함된 SDK는
  이 방법으로 잡히지 않을 수 있습니다.

## 8. 참고 자료

- 원본 APK: `apk_for_pdf/` (로컬 분석용, 저장소에 커밋하지 않음)
- 정적 팩트: `.tools/ca-projects/apk-facts/*.json`
- 도구: REA 6.9.0, Craft Anything 0.6.1
- 서명 검증: v2/v3 서명 블록과 APK 내용 다이제스트 검증 완료
