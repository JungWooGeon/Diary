"""Play Store graphics for 온이 일기 v2.0.

Builds, from real app captures in store/captures/ (debug build on the Pixel_8 / Pixel_Tablet emulators,
DebugSeedReceiver sample data, `--es nav` deep links, SystemUI demo mode status bar, premium debug flag = no ads):
  store/icon_512.png                       app icon (copied from 캐릭터 시안 app_icon/final_A)
  store/feature_graphic.png                1024x500
  store/screenshots/screenshot_01..08.png  1080x1920 (phone)
  store/screenshots_tablet/tablet_01..04.png  2560x1600 (tablet, landscape)
  store/preview_sheet.png                  contact sheet for review

Look: a warm night desk. Deep violet room, the mood light pooling behind the phone, Oni as the hero.
Rendered with headless Chrome through the character package's render_many (same as the 시안 _source scripts).

    python3 store/make_store.py
"""
import pathlib, shutil, sys
from PIL import Image

STORE = pathlib.Path(__file__).resolve().parent
REPO = STORE.parent
JWG = REPO.parent.parent
CAPS = STORE / 'captures'
CHAR = JWG / 'release' / '일기장_캐릭터_시안'
sys.path.insert(0, str(CHAR / '_source'))
import oni as O  # noqa: E402

FONT = (REPO / 'core/designsystem/src/main/res/font/pretendard.ttf').as_uri()
TMP = STORE / '.build'

CSS = """
@font-face{font-family:P;src:url('%(font)s');font-weight:100 900}
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:%(w)dpx;height:%(h)dpx;overflow:hidden;background:#17132A}
body{font-family:P,'Apple SD Gothic Neo',sans-serif;color:#F4EFE8;word-break:keep-all;-webkit-font-smoothing:antialiased;position:relative}
.room{position:absolute;inset:0;background:
  radial-gradient(120%% 70%% at 50%% 0%%,#2A2348 0%%,#1C1830 55%%,#141126 100%%)}
.pool{position:absolute;border-radius:50%%;filter:blur(%(blur)dpx)}
.grain{position:absolute;inset:0;opacity:.09;mix-blend-mode:overlay;background-image:url("data:image/svg+xml,%%3Csvg xmlns='http://www.w3.org/2000/svg' width='220' height='220'%%3E%%3Cfilter id='n'%%3E%%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/%%3E%%3C/filter%%3E%%3Crect width='100%%25' height='100%%25' filter='url(%%23n)'/%%3E%%3C/svg%%3E")}
"""


def hx(h, a):
    r, g, b = O.hx(h)
    return f'rgba({r},{g},{b},{a})'


def page(w, h, body, extra='', blur=60):
    css = CSS % dict(font=FONT, w=w, h=h, blur=blur) + extra
    return f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><style>{css}</style></head><body>{body}</body></html>'


# ---------- screenshots (540x960 CSS px @2x = 1080x1920) ----------
W, H = 540, 960
SHOT_CSS = """
.cap{position:absolute;left:0;right:0;top:58px;text-align:center;z-index:3}
.eb{display:inline-flex;align-items:center;gap:7px;font-size:14px;font-weight:600;color:#CFC6DD;letter-spacing:.2px}
.eb i{width:8px;height:8px;border-radius:4px}
.hd{margin-top:12px;font-size:40px;line-height:50px;font-weight:800;letter-spacing:-1.2px;color:#FFF6E6}
.sub{margin-top:10px;font-size:17px;line-height:24px;font-weight:500;color:#BDB5CC;letter-spacing:-.3px}
.phone{position:absolute;left:50%;top:214px;width:404px;margin-left:-202px;padding:9px;border-radius:52px;
  background:linear-gradient(160deg,#3B3456,#221D36 40%,#17142A);z-index:2;
  box-shadow:0 0 0 1px rgba(255,255,255,.07) inset,0 40px 80px rgba(6,4,16,.55),0 0 0 1.5px #0E0C1A}
.phone .scr{width:386px;height:858px;border-radius:44px;overflow:hidden;background:#1C1830 top/100% auto no-repeat}
.phone:after{content:'';position:absolute;top:22px;left:50%;width:11px;height:11px;margin-left:-5.5px;border-radius:6px;background:#0B0A12}
.float{position:absolute;z-index:4;border-radius:30px;overflow:hidden;box-shadow:0 24px 50px rgba(6,4,16,.6),0 0 0 1px rgba(255,255,255,.08)}
.note{position:absolute;left:0;right:0;bottom:26px;text-align:center;z-index:5}
.note span{display:inline-flex;align-items:center;gap:6px;height:32px;padding:0 15px;border-radius:16px;font-size:13px;font-weight:600;
  color:#F4EFE8;background:rgba(28,24,48,.82);box-shadow:0 0 0 1px rgba(255,255,255,.1) inset,0 8px 24px rgba(0,0,0,.35);backdrop-filter:blur(6px)}
"""

# (capture, eyebrow, glow, headline, sub, note, floats). AI eyebrows only on screens where a model does the work (AI_GROWTH §1).
# A float is a real crop from captures/ laid over the phone: (capture, left, top, width, rotate_deg), CSS px on the 540x960 page.
SHOTS = [
    ('phone_01_today', '무드등 온이', '#F5A957', '기분이 빛이 되는 일기장', '글에서 AI가 마음의 색을 읽고, 온이가 그 빛으로 켜져요', None, []),
    ('phone_02_write', 'AI · 쓰는 동안', '#EE8BA5', '쓰는 동안 빛이 변해요', 'AI가 글에서 마음의 색을 읽고 감정·제목을 추천해요',
     '감정 AI는 폰 안에서만 돌아요', [('phone_02_ai_row', 22, 652, 496, 0)]),
    ('phone_03_view', 'AI · 문장 밑줄 · 비슷했던 날', '#7FC08F', '그때는 이렇게 지나갔어', '감정이 진한 문장엔 빛 밑줄, 결이 비슷한 날을 찾아 줘요', None, []),
    ('phone_04_letter', '주간 온이 편지', '#A595CF', '일요일 밤, 온이의 편지', '이번 주의 빛과 자주 느낀 감정을 편지로 받아요', None, []),
    ('phone_05_calendar', '무드 캘린더 · AI 검색', '#7FC08F', '한 달이 빛으로 물들어요', '단어가 달라도 뜻이 비슷한 일기를 찾아요',
     None, [('phone_05_search_card', 46, 612, 448, 0)]),
    ('phone_06_report', '월간 리포트', '#F4C23E', '내 마음의 패턴을 알아요', 'AI가 고른 이번 달의 한 줄, 기분이 달라진 흐름까지',
     None, [('phone_06_causes_card', 50, 626, 440, 0)]),
    ('phone_07_scan', 'AI · 종이 일기 옮기기', '#F5A957', '종이 일기도 옮겨 와요', '찍으면 한국어 글자로 옮기고, 고친 뒤 넣어요',
     None, [('phone_07_scan_page', 92, 600, 356, -3)]),
    ('phone_08_lock', '잠금 · 백업', '#F5A957', '내 일기는 나만 봐요', 'PIN·지문 잠금, 내 Google 드라이브에 자동 백업',
     None, [('phone_08_backup_card', 34, 822, 472, 0)]),
]

TABLET = [
    ('tablet_01_today', '오늘', '#F5A957', '넓은 화면에서도 온이와 함께', '오늘의 빛, 하루의 흐름, 1년 전 오늘까지 한눈에'),
    ('tablet_02_calendar', '무드 캘린더', '#EE8BA5', '달력 옆에 그날의 이야기', '날짜를 누르면 일기와 온이의 한마디가 바로 옆에 펼쳐져요'),
    ('tablet_03_report', '리포트', '#7FC08F', '내 마음의 패턴을 한 화면에', 'AI가 고른 이번 달의 한 줄, 감정 원인, 자주 나온 이야기'),
    ('tablet_04_write', 'AI · 쓰기', '#EE8BA5', '쓰는 동안 빛이 변해요', '글에 비친 마음의 색으로 온이가 물들고, AI가 감정·제목·태그를 추천해요'),
]


def cap(name):
    return (CAPS / f'{name}.png').as_uri()


def shot_html(name, eb, glow, hd, sub, note, floats):
    pools = (f'<div class="pool" style="left:-60px;top:330px;width:660px;height:560px;background:{hx(glow, .30)}"></div>'
             f'<div class="pool" style="left:150px;top:150px;width:240px;height:200px;background:{hx(glow, .16)}"></div>'
             f'<div class="pool" style="left:-120px;top:880px;width:780px;height:220px;background:{hx("#F5A957", .10)}"></div>')
    extra = ''.join(f'<img class="float" src="{cap(f)}" style="left:{x}px;top:{y}px;width:{w}px;border-radius:22px;transform:rotate({r}deg)">'
                    for f, x, y, w, r in floats)
    nt = f'<div class="note"><span>{note}</span></div>' if note else ''
    body = (f'<div class="room"></div>{pools}<div class="grain"></div>'
            f'<div class="cap"><div class="eb"><i style="background:{glow};box-shadow:0 0 10px {glow}"></i>{eb}</div>'
            f'<div class="hd">{hd}</div><div class="sub">{sub}</div></div>'
            f'<div class="phone"><div class="scr" style="background-image:url({cap(name)})"></div></div>{extra}{nt}')
    return page(W, H, body, SHOT_CSS, blur=70)


# ---------- tablet screenshots (1280x800 CSS px @2x = 2560x1600, landscape) ----------
TW, TH = 1280, 800
TAB_CSS = SHOT_CSS.replace('top:58px', 'top:44px') + """
.tab{position:absolute;left:50%;top:206px;width:1000px;margin-left:-500px;padding:10px;border-radius:36px;
  background:linear-gradient(160deg,#3B3456,#221D36 40%,#17142A);z-index:2;
  box-shadow:0 0 0 1px rgba(255,255,255,.07) inset,0 40px 80px rgba(6,4,16,.55),0 0 0 1.5px #0E0C1A}
.tab .scr{width:980px;height:612.5px;border-radius:26px;overflow:hidden;background:#1C1830 top/100% auto no-repeat}
"""


def tablet_html(name, eb, glow, hd, sub):
    pools = (f'<div class="pool" style="left:80px;top:300px;width:1120px;height:520px;background:{hx(glow, .28)}"></div>'
             f'<div class="pool" style="left:470px;top:70px;width:340px;height:180px;background:{hx(glow, .16)}"></div>')
    body = (f'<div class="room"></div>{pools}<div class="grain"></div>'
            f'<div class="cap"><div class="eb"><i style="background:{glow};box-shadow:0 0 10px {glow}"></i>{eb}</div>'
            f'<div class="hd">{hd}</div><div class="sub">{sub}</div></div>'
            f'<div class="tab"><div class="scr" style="background-image:url({cap(name)})"></div></div>')
    return page(TW, TH, body, TAB_CSS, blur=80)


# ---------- feature graphic (1024x500 @1, rendered @2 then downscaled) ----------
FG_CSS = """
.desk{position:absolute;left:0;right:0;top:400px;bottom:0;background:linear-gradient(180deg,rgba(70,48,52,.55) 0%,rgba(28,22,40,.4) 60%,rgba(20,17,38,0) 100%)}
.desk:before{content:'';position:absolute;left:0;right:0;top:0;height:1px;background:linear-gradient(90deg,transparent,rgba(255,214,160,.35) 60%,transparent)}
.oni{position:absolute;left:648px;top:96px;width:330px;z-index:3}
.orb{position:absolute;border-radius:50%;z-index:2}
.copy{position:absolute;left:72px;top:0;bottom:0;display:flex;flex-direction:column;justify-content:center;z-index:4}
.brand{display:flex;align-items:center;gap:12px;font-size:22px;font-weight:700;color:#F4EFE8;letter-spacing:-.4px}
.brand img{width:44px;height:44px;border-radius:11px}
.t{margin-top:22px;font-size:48px;line-height:60px;font-weight:800;letter-spacing:-1.6px;color:#FFF6E6}
.t b{font-weight:800;color:#FFD9A0}
.v{margin-top:16px;font-size:20px;line-height:28px;font-weight:500;color:#C9C0D8;letter-spacing:-.4px}
"""


def feature_html():
    oni = (CHAR / 'final/oni_main_4096.png').as_uri()
    icon = (STORE / 'icon_512.png').as_uri()
    orbs = ''
    for x, y, r, (light, glow) in [(612, 120, 15, ('#FFF0A8', '#F4C23E')), (590, 250, 11, ('#D6F0CB', '#7FC08F')),
                                   (998, 150, 13, ('#FFD6DF', '#EE8BA5')), (1000, 290, 10, ('#D3E2F6', '#7F9FD1')),
                                   (650, 46, 9, ('#E6DDF4', '#A595CF'))]:
        orbs += (f'<div class="orb" style="left:{x - r}px;top:{y - r + 20}px;width:{2 * r}px;height:{2 * r}px;'
                 f'background:radial-gradient(circle at 50% 42%,#FFFDF6,{light} 38%,{glow});box-shadow:0 0 {2 * r}px {hx(glow, .7)}"></div>')
    body = ('<div class="room"></div>'
            f'<div class="pool" style="left:560px;top:-40px;width:500px;height:500px;background:{hx("#F5A957", .42)}"></div>'
            f'<div class="pool" style="left:700px;top:120px;width:220px;height:220px;background:{hx("#FFE2B0", .35)}"></div>'
            '<div class="desk"></div>'
            f'<div class="pool" style="left:620px;top:380px;width:380px;height:60px;background:{hx("#F5A957", .45)}"></div>'
            f'<div class="grain"></div>{orbs}<img class="oni" src="{oni}">'
            f'<div class="copy"><div class="brand"><img src="{icon}">온이 일기</div>'
            '<div class="t">오늘 기분,<br><b>무슨 색</b>이었어요?</div>'
            '<div class="v">쓰는 동안 마음의 색을 읽어 주는<br>AI 감정일기</div></div>')
    return page(1024, 500, body, FG_CSS, blur=50)


def preview_sheet(shots, fg, tabs):
    cols, tw, gap = 4, 380, 36
    th = round(tw * 1920 / 1080)
    fw = cols * tw + (cols - 1) * gap
    fh = round(fw * 500 / 1024)
    pw = (fw - gap) // 2
    ph = round(pw * 1600 / 2560)
    top = gap * 2 + fh + 2 * (th + gap)
    sheet = Image.new('RGB', (fw + gap * 2, top + 2 * (ph + gap)), (20, 17, 36))
    for i, p in enumerate(tabs):
        r, c = divmod(i, 2)
        sheet.paste(Image.open(p).resize((pw, ph), Image.LANCZOS), (gap + c * (pw + gap), top + r * (ph + gap)))
    sheet.paste(Image.open(fg).resize((fw, fh), Image.LANCZOS), (gap, gap))
    for i, p in enumerate(shots):
        r, c = divmod(i, cols)
        sheet.paste(Image.open(p).resize((tw, th), Image.LANCZOS), (gap + c * (tw + gap), gap * 2 + fh + r * (th + gap)))
    out = STORE / 'preview_sheet.png'
    sheet.save(out, optimize=True)
    return out


def to_rgb(p, size):
    im = Image.open(p).convert('RGB')
    if im.size != size:
        im = im.resize(size, Image.LANCZOS)
    im.save(p, optimize=True)
    return p


if __name__ == '__main__':
    TMP.mkdir(exist_ok=True)
    (STORE / 'screenshots').mkdir(exist_ok=True)
    shutil.copy(CHAR / 'app_icon/final_A/playstore_512.png', STORE / 'icon_512.png')
    only = sys.argv[1:]  # e.g. `make_store.py 3 t2 fg`
    jobs, shots = [], []
    for i, s in enumerate(SHOTS, 1):
        p = STORE / f'screenshots/screenshot_{i:02d}.png'
        shots.append(p)
        if not only or str(i) in only:
            jobs.append((shot_html(*s), str(p), W, H, False, 2))
    (STORE / 'screenshots_tablet').mkdir(exist_ok=True)
    tabs = []
    for i, t in enumerate(TABLET, 1):
        p = STORE / f'screenshots_tablet/tablet_{i:02d}.png'
        tabs.append(p)
        if not only or f't{i}' in only:
            jobs.append((tablet_html(*t), str(p), TW, TH, False, 2))
    fg = STORE / 'feature_graphic.png'
    if not only or 'fg' in only:
        jobs.append((feature_html(), str(fg), 1024, 500, False, 2))
    O.render_many(jobs, workers=6)
    for p in shots:
        to_rgb(p, (1080, 1920))
    for p in tabs:
        to_rgb(p, (2560, 1600))
    to_rgb(fg, (1024, 500))
    print(preview_sheet(shots, fg, tabs))
    shutil.rmtree(TMP, ignore_errors=True)
    assert Image.open(fg).size == (1024, 500) and Image.open(fg).mode == 'RGB'
    assert all(Image.open(p).size == (1080, 1920) for p in shots)
    assert all(Image.open(p).size == (2560, 1600) for p in tabs)
