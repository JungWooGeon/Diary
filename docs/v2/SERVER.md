# oni-api (Cloudflare Worker) — 계약서 (2026-11-22)

## 변경 (서버 구현 시, 2026-11-22)
- 엔드포인트 모양은 그대로. KV `ent:<uid>` 에 `purchaseToken` 필드 추가 — 7일 재확인 때 `purchases.products.get` 을 다시 부르려면 원본 토큰이 필요(해시만으론 불가). `/v1/me` 삭제 때 같이 지워짐.
- 오류 코드 추가: `/v1/entitlement/play` → 400 `product_invalid`(productId≠premium) / `purchase_invalid`(구글에 없음) / `purchase_pending`(purchaseState 2) / `purchase_cancelled`(1). `/v1/auth/code` → 400 `code_invalid`. 공통 → 401 `unauthorized`(Bearer 없음·무효), 400 `invalid_*`·`invalid_json`, 413 `body_too_large`, 502 `google_unavailable`·`play_unavailable`, 403 `origin_not_allowed`(허용 안 된 Origin; Origin 없는 Android 요청은 통과).
- `/v1/me` 는 `tok:<hash>` 도 지운다(같은 구매를 다른 계정에 다시 연결 가능).


목적 3가지만: ① 웹 로그인 유지(리프레시 토큰 보관) ② Play 결제 확인 → 구글 계정에 프리미엄 연결(앱·웹 공통) ③ 연동 해제.
**일기 내용은 절대 받지 않는다.** 서버가 저장하는 것: 사용자 키(Drive permissionId), 리프레시 토큰(웹 로그인 유지용), 결제 기록(주문번호·상품·시각).

- 위치: `server/` (TypeScript, wrangler, KV 하나 `ONI`). 무료 등급.
- 주소: `https://oni-api.<계정 서브도메인>.workers.dev` (웹은 `VITE_API_BASE`, 앱은 `BuildConfig.API_BASE`)
- CORS: `https://jungwoogeon.github.io`, `http://localhost:5173` 만 허용.
- 비밀값(wrangler secret): `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_WEB_CLIENT_SECRET`, `PLAY_SA_JSON`(서비스 계정 키 JSON 전체), 상수 `PACKAGE_NAME=com.pass.diary`, `PRODUCT_ID=premium`.

## 사용자 식별
`Authorization: Bearer <Google access token>`(drive.appdata 범위면 충분).
서버는 `GET https://www.googleapis.com/drive/v3/about?fields=user(permissionId,emailAddress)` 로 확인해 `uid = permissionId`를 얻는다(실패 → 401). 결과는 토큰 해시로 5분 캐시해도 된다.

## 엔드포인트 (JSON, 오류는 `{error: string}` + 상태코드)
| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/v1/auth/code` | `{code}` (GIS code client, popup → redirect_uri `postmessage`) | `{session, access_token, expires_in, email}` — 리프레시 토큰이 없으면 400 `no_refresh_token`(웹은 prompt=consent로 다시) |
| POST | `/v1/auth/token` | `{session}` | `{access_token, expires_in}` / 401 `session_invalid`(토큰 철회·만료 → 웹은 다시 로그인 버튼) |
| POST | `/v1/auth/logout` | `{session}` | `{ok:true}` (세션 삭제 + 구글 토큰 철회) |
| GET | `/v1/entitlement` | Bearer | `{premium: bool, source: "play"\|null, since: number\|null}` — play 기록은 7일 지나면 다시 확인(환불·취소면 premium:false) |
| POST | `/v1/entitlement/play` | Bearer, `{productId, purchaseToken}` | `{premium:true, source:"play", since}` — Android Publisher `purchases.products.get` 로 purchaseState==0 확인. 같은 구매가 다른 uid에 이미 연결돼 있으면 409 `linked_elsewhere` |
| DELETE | `/v1/me` | Bearer | `{ok:true}` — 그 uid의 세션·리프레시 토큰·프리미엄 연결 삭제. 결제 기록(주문번호·시각, uid 없이)은 전자상거래법상 5년 보관 |

KV 키: `sess:<sid>`={uid, rt, email, createdAt}, `sessions:<uid>`=[sid…], `ent:<uid>`={premium, source, orderId, tokenHash, since, checkedAt}, `tok:<sha256(purchaseToken)>`=uid, `pay:<orderId>`={productId, purchasedAt}.
세션 id는 32바이트 랜덤(base64url).

## 클라이언트 동작
- **웹**: 로그인 = GIS `initCodeClient`(scope drive.appdata, popup) → `/v1/auth/code` → session은 localStorage. 열 때마다 `/v1/auth/token`으로 액세스 토큰 받기(만료 1분 전 자동 갱신) → "드라이브 다시 연결"은 세션이 정말 죽었을 때만. 프리미엄 = `/v1/entitlement` (결과 캐시, 실패 시 마지막 값). 서버가 죽어도 일기 쓰기·동기화(1시간 토큰)는 된다.
- **Android**: Play 결제가 로컬에서 확인되면 언제나 프리미엄(서버 무관). 구글 로그인(드라이브 연결)돼 있고 로컬 프리미엄이면 `/v1/entitlement/play`로 한 번 연결(성공 플래그 저장, 실패 시 다음 실행 때 재시도). 로컬 프리미엄이 아니면 `/v1/entitlement`로 확인(웹 결제가 생겼을 때 대비, 지금은 play만).
- 웹 결제: 사업자등록이 없어 보류. 웹에서는 "프리미엄은 Android 앱에서 구매 → 같은 구글 계정으로 로그인하면 웹에서도 광고가 사라져요" 안내(웹에서 앱으로 안내하는 건 Play 정책과 무관). 앱 안에서는 웹 결제를 언급하지 않는다(Play 결제 정책).
