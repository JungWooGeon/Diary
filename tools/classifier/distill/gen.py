"""Generate diary-style entries with Gemma 4 E4B (llama-server :8720). Appends to gen.jsonl.
python -I gen.py N_REQUESTS   (each request -> 5 entries)
"""
import json, random, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "data/distill/gen.jsonl"
WHO = ["중학교 2학년 여학생", "중학교 3학년 남학생", "고등학교 1학년 여학생", "고3 수험생", "고2 남학생", "재수생",
       "대학교 1학년 새내기", "대학교 3학년 여학생", "휴학 중인 대학생", "졸업반 대학생", "취준생", "공시생",
       "사회초년생 회사원", "20대 후반 직장인", "카페 알바하는 20대", "간호사", "20대 프리랜서 디자이너", "군인(병사)",
       "30대 초반 직장인", "신혼부부 중 아내", "30대 개발자", "어린 아이를 키우는 30대 엄마", "30대 자영업자",
       "40대 회사 팀장", "40대 주부", "고등학생 자녀를 둔 40대 아빠", "40대 자영업자", "자취하는 20대", "대학원생", "교사"]
TOPIC = ["시험/성적", "수행평가·과제", "친구와의 다툼", "친구와 화해", "단짝과 놀러감", "짝사랑", "썸", "연애 중 데이트", "이별",
         "부모님 잔소리", "부모님과 대화", "형제자매", "반려동물", "가족 병문안", "할머니·할아버지", "회사 상사", "회사 동료",
         "야근·업무 폭탄", "면접", "합격·불합격 소식", "월급날·돈 문제", "카드값·지출", "알바 진상 손님", "출퇴근 지하철",
         "날씨(비·눈·맑음)", "혼자 보낸 주말", "아무 일 없는 평범한 하루", "운동·다이어트", "몸이 아픔·병원", "잠·불면",
         "덕질·콘서트", "게임", "드라마·영화", "맛있는 음식", "요리 실패", "카페에서 공부", "이사·자취방", "여행", "생일",
         "기념일", "고향·옛 친구 생각", "돌아가신 분 생각", "육아", "아이 학교 일", "새 학기·새 반", "졸업", "동아리",
         "SNS·단톡방", "택배·쇼핑", "중고거래", "미용실", "취미(그림·뜨개·악기)", "산책", "명절", "진로 고민", "자존감",
         "외모 고민", "발표", "대중교통에서 생긴 일", "이웃·층간소음", "휴가 첫날", "월요일 출근", "퇴사 고민", "승진·칭찬"]
MOOD = ["기쁨", "뿌듯함", "신남", "감사", "편안함", "차분함", "안심", "홀가분", "설렘", "사랑", "기대", "그리움",
        "슬픔", "우울", "외로움", "서운함", "피곤", "불안", "짜증", "화남"]
SPECIAL = ["두 가지 감정이 섞인(예: 좋은데 아쉬움, 피곤한데 뿌듯함)", "별일 없고 감정이 애매한·잘 모르겠는",
           "담담하게 사실만 적어서 감정이 잘 안 드러나는"]
SARC = "반어(비꼼): 안 좋은 일을 적어 놓고 '최고', '역시', '완벽', '감사하다', '덕분에', '행복하다' 같은 좋은 말이나 ^^, ㅎㅎ를 비꼬는 뜻으로 붙인. 속마음은 짜증·화남·서운함·우울 중 하나"
LEN = [("한 줄(5~20자)", 0.15), ("한두 문장", 0.25), ("두세 문장", 0.25), ("서너 문장", 0.2), ("네 문장에서 여섯 문장", 0.15)]
STYLE = ["평범한 일기체(~했다)", "채팅하듯(ㅋㅋ, ㅠㅠ, ㅎㅎ 등)", "줄임말·신조어 섞인 말투", "오타·띄어쓰기 실수가 조금 있는",
         "혼잣말하듯(~네, ~나)", "~함/~음으로 끝나는 메모체", "차분한 문어체"]
SCHEMA = {"type": "object", "properties": {"entries": {"type": "array", "minItems": 5, "maxItems": 5,
          "items": {"type": "string", "minLength": 3, "maxLength": 400}}}, "required": ["entries"]}


def pick(pairs):
    return random.choices([a for a, _ in pairs], [w for _, w in pairs])[0]


def spec():
    r = random.random()
    mood = SARC if r < 0.1 else random.choice(SPECIAL) if r < 0.25 else random.choice(MOOD) + " 감정이 드러나는"
    return f"{random.choice(WHO)} / 소재: {random.choice(TOPIC)} / 길이: {pick(LEN)} / 말투: {random.choice(STYLE)} / {mood}"


def one(seed):
    random.seed(seed)
    sp = spec()
    prompt = ("감정 일기 앱에 한국 사람이 실제로 쓸 법한 오늘의 일기 하나를 써 줘.\n조건: " + sp +
              "\n- 소재는 그 사람에게 자연스럽게 맞춰도 돼. 구체적인 사람·장소·물건·숫자를 넣고, 뻔한 표현은 피해."
              "\n- 감정 이름을 꼭 쓰지 않아도 돼. 제목, 날짜, 따옴표, 설명, 해시태그, 이모지 없이 일기 본문만 출력해.")
    body = {"messages": [{"role": "user", "content": prompt}], "temperature": 1.0, "top_p": 0.95, "seed": seed,
            "max_tokens": 300, "chat_template_kwargs": {"enable_thinking": False}}
    req = urllib.request.Request("http://127.0.0.1:8720/v1/chat/completions", json.dumps(body).encode(), {"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            t = json.load(r)["choices"][0]["message"]["content"].strip().strip('"')
    except Exception as e:
        print("ERR", seed, e, flush=True); return []
    return [{"text": t, "spec": sp, "seed": seed}]


def main():
    n, start = int(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 0
    with open(OUT, "a") as f, ThreadPoolExecutor(10) as ex:
        for i, rows in enumerate(ex.map(one, range(start, start + n))):
            for r in rows: f.write(json.dumps(r, ensure_ascii=False) + "\n")
            f.flush()
            if i % 500 == 0: print(i, flush=True)


if __name__ == "__main__":
    main()
