"""Oni 표정 → 빛 색을 실행 중에 바꿀 수 있는 3층 WebP (core/designsystem/res/drawable-nodpi).

oni.py 그림은 light/glow 색에 대해 (채널별) 선형이다: mix()·불투명도 합성·blur 모두 선형.
그래서 light=검정/흰색, glow=검정/흰색으로 세 번 그리면
  out = base + light·L + glow·G      (premultiplied)
로 어떤 색이든 다시 만들 수 있다. base 의 알파는 (α − L − G)로 줄여 두어서, 앱에서
L·G 마스크를 BlendMode.Plus 로 더하면 알파가 원래 α 로 돌아온다(Oni.kt).
후광(halo)은 앱이 숨쉬기 애니메이션으로 직접 그리므로 빼고 그린다. 꺼진 온이(기록 없음)는 그림이 달라서 한 장 그대로.

  python3 core/designsystem/tools/oni_layers.py   # 캐릭터 시안 폴더가 ../../release 에 있어야 함
"""
import sys, pathlib, tempfile
import numpy as np
from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[3]
CHAR = REPO.parents[1] / 'release' / '일기장_캐릭터_시안' / '_source'
sys.path.insert(0, str(CHAR))
import oni as O  # noqa: E402

OUT = REPO / 'core/designsystem/src/main/res/drawable-nodpi'
EXPRS = ['default', 'happy', 'calm', 'flutter', 'sad', 'tired', 'sleep', 'think', 'comfort', 'surprised', 'cheer', 'letter']
S = 512
# blur 를 sRGB 에서 계산해야 색에 대해 선형이 된다(기본 linearRGB 는 비선형).
CSS = '<style>*{color-interpolation-filters:sRGB}</style>'


def svg(m, expr, u):
    return f'{CSS}<svg viewBox="0 0 512 512" width="{S}" height="{S}" style="overflow:visible">{O.oni(m, expr, u, halo=False)}</svg>'


def mood(light, glow):
    return dict(O.BRAND, light=light, glow=glow, blush=O.BRAND['blush'])


def load(p):
    a = np.asarray(Image.open(p).convert('RGBA')).astype(np.float64) / 255
    a[..., :3] *= a[..., 3:4]  # premultiply
    return a


def main():
    tmp = pathlib.Path(tempfile.mkdtemp())
    jobs = []
    for e in EXPRS:
        for tag, m in (('k', mood('#000000', '#000000')), ('l', mood('#FFFFFF', '#000000')), ('g', mood('#000000', '#FFFFFF')),
                       ('joy', dict(O.MOODS[0]))):
            jobs.append((svg(m, e, f'{e}{tag}'), tmp / f'{e}_{tag}.png', S, S, True, 1))
    jobs.append((svg(O.OFF, 'sleep', 'off'), tmp / 'off.png', S, S, True, 1))
    O.render_many(jobs, workers=8)
    worst = 0
    for e in EXPRS:
        k, l, g = (load(tmp / f'{e}_{t}.png') for t in 'klg')
        L = np.clip((l[..., :3] - k[..., :3]).mean(-1), 0, 1)
        G = np.clip((g[..., :3] - k[..., :3]).mean(-1), 0, 1)
        alpha = k[..., 3]
        base_a = np.clip(alpha - L - G, 0, 1)
        base_rgb = np.clip(k[..., :3], 0, base_a[..., None])
        # 검증: 기쁨 색으로 다시 만든 것 vs 실제 기쁨 그림
        lc = np.array(O.hx(O.MOODS[0]['light'])) / 255; gc = np.array(O.hx(O.MOODS[0]['glow'])) / 255
        rec = base_rgb + L[..., None] * lc + G[..., None] * gc
        ref = load(tmp / f'{e}_joy.png')
        err = np.abs(rec - ref[..., :3]).max(); worst = max(worst, np.percentile(np.abs(rec - ref[..., :3]), 99.9))
        un = np.where(base_a[..., None] > 0, base_rgb / np.maximum(base_a[..., None], 1e-6), 0)
        Image.fromarray((np.dstack([un, base_a]) * 255 + .5).astype(np.uint8), 'RGBA').save(OUT / f'oni_{e}.webp', quality=92, method=6)
        for name, mask in (('l', L), ('g', G)):
            img = np.zeros((S, S, 4)); img[..., :3] = 1; img[..., 3] = mask
            Image.fromarray((img * 255 + .5).astype(np.uint8), 'RGBA').save(OUT / f'oni_{e}_{name}.webp', lossless=True, quality=100, method=6)
        print(e, 'max err', round(err, 3))
    Image.open(tmp / 'off.png').save(OUT / 'oni_off.webp', quality=92, method=6)
    print('99.9th pct err', round(worst, 4))


if __name__ == '__main__':
    main()
