# oni-api (Cloudflare Worker)

계약서: `../docs/v2/SERVER.md`

## 처음 한 번
```sh
cd server
npm install
npx wrangler login
npx wrangler kv namespace create ONI     # 나온 id를 wrangler.toml 의 REPLACE_KV_ID 자리에
npx wrangler secret put GOOGLE_WEB_CLIENT_ID
npx wrangler secret put GOOGLE_WEB_CLIENT_SECRET
npx wrangler secret put PLAY_SA_JSON < play-sa.json   # 서비스 계정 키 JSON 전체 (파일은 커밋 금지)
```
- `PACKAGE_NAME`, `PRODUCT_ID` 는 `wrangler.toml` 의 `[vars]`.
- 서비스 계정은 Play Console → 사용자 및 권한에서 이 앱의 "재무 데이터 보기"(주문 조회) 권한 필요.
- 웹 OAuth 클라이언트의 승인된 자바스크립트 원본에 `https://jungwoogeon.github.io`, `http://localhost:5173` 추가.

## 배포 / 확인
```sh
npm test && npm run typecheck
npx wrangler deploy
npx wrangler tail        # 실시간 로그 (토큰은 로그에 안 남김)
```
