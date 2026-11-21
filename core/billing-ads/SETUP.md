# 광고·결제 출시 전 설정 (온이 일기)

디버그 빌드는 Google 테스트 ID로 돌아가서 설정 없이 확인할 수 있다. **릴리스 빌드는 아래 4개가 `local.properties`에 없으면 실패한다.**

```
admob_app_id=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX
banner_ad_unit_id=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX   # 캘린더·리포트 하단 배너
native_ad_unit_id=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX   # 타임라인 네이티브(고급형)
reward_ad_unit_id=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX   # 특별 빛 팔레트 하루 써 보기
```

1. **AdMob**: 앱 "온이 일기"(com.pass.diary)를 새로 등록하고 광고 단위 3개(배너, 네이티브 고급형, 보상형)를 새로 만든다.
   퍼스널컬러 앱의 ID를 재사용하지 않는다. 개인정보 보호 및 메시지(UMP)에서 GDPR 메시지를 게시한다. app-ads.txt 등록.
2. **Play Console**: 수익 창출 → 인앱 상품 → 일회성 상품 ID `premium`, 가격 ₩3,300, 활성화. 라이선스 테스터 계정으로 구매·복원 확인.
3. Play Console 데이터 보안 양식: 광고 ID·대략적 위치(날씨, 선택)·구매 내역 항목 반영.

디버그에서 프리미엄 화면을 보려면(Play 계정 없는 에뮬레이터):
`adb shell "run-as com.pass.diary sh -c 'echo \"<map><boolean name=\\\"debug_force\\\" value=\\\"true\\\"/></map>\" > shared_prefs/billing.xml'"` 후 앱 재시작.
