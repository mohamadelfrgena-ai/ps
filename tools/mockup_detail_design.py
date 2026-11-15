#!/usr/bin/env python3
# Renders the NEW addon-page design (AddonStation-PS4-AddonPage spec) in the app's real visual style.
# Outputs: mine-preview/addonpage-newdesign-{en,ar,states}.png
# Run from the repo root. Self-contained: extracts fonts/avatars from assets/ui.pak and covers
# from mine-preview/content-releases-upload.zip. Blurred bg = mine-preview/detail-bg-store.jpg
# (his store image; cover stays the item cover). Legendary items get the AC unlock at a fixed,
# higher price (e.g. 1200 AC vs Epic 600), with the same gold AC button style as Epic.
import struct, os, math, zipfile
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import arabic_reshaper
from bidi.algorithm import get_display

W, H = 1920, 1080
PN=(21,24,29); LN=(42,56,70); TX=(238,243,248); MU=(142,160,179)
GD=(255,214,90); GR=(20,184,138); BL=(26,111,214); WN=(251,160,60); ER=(255,128,150)
CTL=(27,42,57); GDLINE=(185,138,28); GDBG=(48,36,10); PU=(124,58,237); PU2=(199,155,255)

# ---------- self-contained asset prep ----------
os.makedirs('/tmp/mock', exist_ok=True)
data = open('ps4-addon-test/assets/ui.pak','rb').read()
assert data[:8] == b'ASUI0001'
(n,) = struct.unpack_from('<I', data, 8); off = 12; E = {}
for _ in range(n):
    nl, dl = struct.unpack_from('<HI', data, off); off += 4+2
    name = data[off:off+nl].decode(); off += nl
    E[name] = data[off:off+dl]; off += dl
for k in ('font_ar','font_body','font_f77','fox_avatar','icon_coin','btn_cross','btn_circle','btn_triangle'):
    open('/tmp/pk_'+k+('.ttf' if k.startswith('font') else '.png'),'wb').write(E[k])
z = zipfile.ZipFile('mine-preview/content-releases-upload.zip')
for nme in z.namelist():
    if nme.endswith('-cover.jpg'):
        open('/tmp/mock/'+nme.split('/')[-1],'wb').write(z.read(nme))

def fb(s): return ImageFont.truetype('/tmp/pk_font_body.ttf', s)
def ff77(s): return ImageFont.truetype('/tmp/pk_font_f77.ttf', s)
def far(s): return ImageFont.truetype('/tmp/pk_font_ar.ttf', s)
def ar(t): return get_display(arabic_reshaper.reshape(t))

def cover_crop(img, w, h):
    r = max(w/img.width, h/img.height)
    im = img.resize((int(img.width*r)+1, int(img.height*r)+1), Image.LANCZOS)
    x = (im.width-w)//2; y = (im.height-h)//2
    return im.crop((x, y, x+w, y+h))

def rounded_img(img, r):
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0,0,img.size[0]-1,img.size[1]-1], radius=r, fill=255)
    out = Image.new('RGBA', img.size, (0,0,0,0)); out.paste(img, (0,0), mask)
    return out

def rrect(d, box, r, fill=None, outline=None, w=1, width=None):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width or w)

def star(d, cx, cy, rad, color):
    pts=[]
    for i in range(10):
        ang = -math.pi/2 + i*math.pi/5
        rr = rad if i%2==0 else rad*0.42
        pts.append((cx+rr*math.cos(ang), cy+rr*math.sin(ang)))
    d.polygon(pts, fill=color)

def arrow_down(d, cx, cy, s, color, w=4):
    d.polygon([(cx, cy+s*0.9), (cx-s*0.85, cy), (cx-s*0.32, cy), (cx-s*0.32, cy-s*0.95),
               (cx+s*0.32, cy-s*0.95), (cx+s*0.32, cy), (cx+s*0.85, cy)], fill=color)

def chevron(d, cx, cy, s, color, right=True, w=6):
    dx = -1 if right else 1   # right=True draws '>', right=False draws '<'
    d.line([(cx+dx*s*0.45, cy-s*0.7), (cx-dx*s*0.45, cy), (cx+dx*s*0.45, cy+s*0.7)], fill=color, width=w, joint='curve')

def focus_ring(d, box, r, color=GD, wdt=3):
    x0,y0,x1,y1 = box
    d.rounded_rectangle([x0-4,y0-4,x1+4,y1+4], radius=r+4, outline=color, width=wdt)

def txt_c(d, cx, cy, s, font, color):
    x0,y0,x1,y1 = d.textbbox((0,0), s, font=font)
    d.text((cx-(x1+x0)/2, cy-(y1+y0)/2), s, font=font, fill=color)

# blurred background = his store image (detail-bg-store.jpg); the item cover stays the cover
base = Image.open('mine-preview/detail-bg-store.jpg').convert('RGB')
bg = cover_crop(base, W, H).filter(ImageFilter.GaussianBlur(28))
ov = Image.new('RGB', (W,H), (6,12,19))
bg = Image.blend(bg, ov, 0.55)

fox = Image.open('/tmp/pk_fox_avatar.png').convert('RGBA')
coin = Image.open('/tmp/pk_icon_coin.png').convert('RGBA')
b_cross = Image.open('/tmp/pk_btn_cross.png').convert('RGBA')
b_circle = Image.open('/tmp/pk_btn_circle.png').convert('RGBA')
b_tri = Image.open('/tmp/pk_btn_triangle.png').convert('RGBA')

def paste_icon(base, icon, x, y, size):
    im = icon.resize((size, size), Image.LANCZOS)
    base.alpha_composite(im, (int(x), int(y)))

def draw_cover(base, img_path, x, y, w, h, r=8, border=LN, bw=3):
    im = rounded_img(cover_crop(Image.open(img_path).convert('RGB'), w, h), r)
    base.alpha_composite(im, (x, y))
    d = ImageDraw.Draw(base)
    d.rounded_rectangle([x,y,x+w-1,y+h-1], radius=r, outline=border, width=bw)

def draw_rail(base, side='left', selected=2):
    rw = 128
    x0 = 0 if side=='left' else W-rw
    rl = Image.new('RGBA', (rw, H), (5,12,19,150))
    base.alpha_composite(rl, (x0,0))
    d = ImageDraw.Draw(base)
    d.line([(x0+rw-1 if side=='left' else x0+1, 0), (x0+rw-1 if side=='left' else x0+1, H)], fill=LN, width=2)
    paste_icon(base, rounded_img(cover_crop(fox, 64, 64), 16), x0+32, 34, 64)
    icons = ['home','pct','store','dl','star','user']
    ys = 130
    for i, ic in enumerate(icons):
        cy = ys + i*86 + 38
        cx = x0+rw//2
        sel = (i == selected)
        if sel:
            rrect(d, [cx-38, cy-38, cx+38, cy+38], 20, fill=BL)
        col = (255,255,255) if sel else MU
        if ic=='home':
            d.polygon([(cx, cy-22),(cx-20, cy-2),(cx+20, cy-2)], fill=col)
            d.rectangle([cx-13, cy-2, cx+13, cy+18], fill=col)
        elif ic=='pct':
            d.ellipse([cx-16, cy-16, cx-4, cy-4], outline=col, width=4)
            d.ellipse([cx+4, cy+4, cx+16, cy+16], outline=col, width=4)
            d.line([(cx-14, cy+16), (cx+14, cy-14)], fill=col, width=4)
        elif ic=='store':
            for a in range(2):
                for b in range(2):
                    rrect(d, [cx-17+a*19, cy-17+b*19, cx-4+a*19, cy-4+b*19], 3, fill=col)
        elif ic=='dl':
            arrow_down(d, cx, cy, 17, col, 3)
        elif ic=='star':
            star(d, cx, cy, 19, col)
        elif ic=='user':
            d.ellipse([cx-9, cy-19, cx+9, cy-1], fill=col)
            d.pieslice([cx-16, cy+2, cx+16, cy+32], 180, 360, fill=col)
    cy = H-70; cx = x0+rw//2
    d.ellipse([cx-17, cy-17, cx+17, cy+17], outline=MU, width=5)
    d.ellipse([cx-6, cy-6, cx+6, cy+6], fill=MU)

def draw_topbar(base, rtl=False):
    d = ImageDraw.Draw(base)
    f20 = fb(20) if not rtl else far(20)
    if not rtl:
        d.text((210, 66), "Store", font=f20, fill=MU)
        d.text((282, 66), "›", font=f20, fill=MU)
        d.text((308, 66), "Addons", font=f20, fill=TX)
    else:
        s1 = ar("المتجر"); s2 = ar("الإضافات")
        d.text((W-210-d.textlength(s1, font=f20), 62), s1, font=f20, fill=MU)
        d.text((W-210-d.textlength(s1, font=f20)-26, 62), "‹", font=f20, fill=MU)
        d.text((W-210-d.textlength(s1, font=f20)-52-d.textlength(s2, font=f20), 62), s2, font=f20, fill=TX)
    ax = W-90 if not rtl else 90
    pas = rounded_img(cover_crop(fox, 42, 42), 21)
    base.alpha_composite(pas, (int(ax-42) if not rtl else int(ax), 60))
    f22 = fb(22); clk = "21:45"
    cx2 = (ax-42-18) if not rtl else (ax+42+18)
    tw = d.textlength(clk, font=f22)
    if not rtl:
        d.text((cx2-tw, 68), clk, font=f22, fill=TX)
    else:
        d.text((cx2, 68), clk, font=f22, fill=TX)
    pill_w = 170; px = (cx2-tw-22-pill_w) if not rtl else (cx2+tw+22)
    rrect(d, [px, 56, px+pill_w, 96], 20, fill=(18,26,35), outline=LN, width=2)
    paste_icon(base, coin, px+14, 63, 28)
    d.text((px+50, 66), "1,250 AC", font=fb(21), fill=GD)

def draw_meta_row(base, x, y):
    d = ImageDraw.Draw(base)
    f18 = fb(18)
    cx = x
    def chip_ltr(text, fg, bgc, ol, gap_after):
        nonlocal cx
        tw = d.textlength(text, font=f18)
        rrect(d, [cx, y, cx+tw+28, y+38], 9, fill=bgc, outline=ol, width=2)
        d.text((cx+14, y+9), text, font=f18, fill=fg)
        cx += tw+28+gap_after
    chip_ltr("Add-On", MU, None, LN, 16)
    chip_ltr("Free", (111,177,255), (14,32,54), (45,93,150), 18)
    star(d, cx+10, y+19, 11, GD)
    d.text((cx+26, y+9), "4.3", font=f18, fill=GD)
    cx += 26+46
    arrow_down(d, cx+9, y+18, 9, TX, 2)
    d.text((cx+24, y+9), "1.2K", font=f18, fill=TX)
    cx += 24+62
    d.text((cx, y+9), "995 KB", font=f18, fill=TX)
    cx += d.textlength("995 KB", font=f18)+18
    d.text((cx, y+9), "v1.0.0", font=f18, fill=TX)
    cx += d.textlength("v1.0.0", font=f18)+18
    nw = d.textlength("NEW", font=fb(15))
    rrect(d, [cx, y+4, cx+nw+22, y+34], 8, fill=GR)
    d.text((cx+11, y+10), "NEW", font=fb(15), fill=(255,255,255))

def meta_rtl(img, xr, y):
    # exact geometric mirror of the EN meta row: type chip first (rightmost), NEW last (leftmost);
    # inside pairs the glyph sits to the RIGHT of its number (bidi RTL).
    d = ImageDraw.Draw(img)
    f18 = far(18)
    cx = xr
    def chip(text, fg, bgc, ol):
        nonlocal cx
        t = ar(text)
        tw = d.textlength(t, font=f18)
        rrect(d, [cx-tw-28, y, cx, y+38], 9, fill=bgc, outline=ol, width=2)
        d.text((cx-14-tw, y+9), t, font=f18, fill=fg)
        cx -= tw+28+16
    def pair(num, glyph):
        nonlocal cx
        if glyph=='star':
            star(d, cx-11, y+19, 11, GD)
        else:
            arrow_down(d, cx-9, y+18, 9, TX, 2)
        d.text((cx-26-d.textlength(num,font=f18), y+9), num, font=f18, fill=GD if glyph=='star' else TX)
        cx -= 26+d.textlength(num,font=f18)+16
    chip("إضافة", MU, None, LN)
    chip("مجاني", (111,177,255), (14,32,54), (45,93,150))
    pair("4.3", 'star')
    pair("1.2K", 'arrow')
    for t in ("995 KB","v1.0.0"):
        d.text((cx-d.textlength(t,font=f18), y+9), t, font=f18, fill=TX)
        cx -= d.textlength(t,font=f18)+18
    nw = d.textlength(ar("جديد"), font=far(15))
    rrect(d, [cx-nw-22, y+4, cx, y+34], 8, fill=GR)
    d.text((cx-11-nw, y+10), ar("جديد"), font=far(15), fill=(255,255,255))

def draw_facts(base, x, y, w, cells, rtl=False):
    d = ImageDraw.Draw(base)
    h = 70
    rrect(d, [x, y, x+w, y+h], 14, fill=PN, outline=LN, width=2)
    n = len(cells); cw = w/n
    fl = (lambda s: far(s)) if rtl else fb
    f16 = fl(16); f20 = fl(20)
    for i,(lab,val) in enumerate(cells):
        cx = x+i*cw
        if i>0:
            d.line([(cx, y+12), (cx, y+h-12)], fill=LN, width=2)
        if rtl:
            tx = x+w-i*cw
            d.text((tx-d.textlength(lab,font=f16)-20, y+12), lab, font=f16, fill=MU)
            d.text((tx-d.textlength(val,font=f20)-20, y+34), val, font=f20, fill=TX)
        else:
            d.text((cx+20, y+12), lab, font=f16, fill=MU)
            d.text((cx+20, y+34), val, font=f20, fill=TX)
    return y+h

def draw_desc(base, x, y, w, text, font, rtl=False, h=112):
    d = ImageDraw.Draw(base)
    rrect(d, [x, y, x+w, y+h], 12, fill=PN, outline=LN, width=2)
    words = text.split(' ')
    lines = []; cur = ''
    for wd in words:
        t = (cur+' '+wd).strip()
        if d.textlength(t, font=font) <= w-52:
            cur = t
        else:
            lines.append(cur); cur = wd
    if cur: lines.append(cur)
    for i, ln in enumerate(lines[:2]):
        if rtl:
            d.text((x+w-26-d.textlength(ln, font=font), y+22+i*42), ln, font=font, fill=TX)
        else:
            d.text((x+26, y+22+i*42), ln, font=font, fill=TX)
    return y+h

def draw_box(base, x, y, rtl=False, state='free', focus_main=True, focus_ac=False):
    d = ImageDraw.Draw(base)
    uf = (lambda s: far(s)) if rtl else fb
    bw = 480
    f22 = uf(22); f28 = uf(28); f19 = uf(19); f21 = uf(21); f26 = uf(26)
    hint = None; hint_warn=False; acbtn=None; progress=None
    if state=='guest':
        access = ("Free with your account", False); btn = ("Sign in to download", 'blue')
        hint = "Sign in to get it — same account as your phone"
    elif state=='free':
        access = ("Free with your account", False); btn = ("Download", 'blue')
        hint = "Minecraft opens automatically after install (if enabled)"
    elif state=='downloading':
        access = ("Free with your account", False); btn = ("Downloading...  63%", 'blue'); progress = 0.63
    elif state=='installing':
        access = ("Free with your account", False); btn = ("Installing...", 'purple')
    elif state=='waiting':
        access = ("Free with your account", False); btn = ("Waiting for Minecraft", 'grey')
    elif state=='installed':
        access = ("Free with your account", False); btn = ("Installed", 'ok')
    elif state=='epic':
        access = ("Requires Epic or higher", True)
        btn = ("Get Epic on your phone", 'purple')
        acbtn = ("Unlock for 600 AC", False)
    elif state=='legendary':
        # NEW rule: Legendary ALSO unlocks with AC, at a fixed price higher than Epic
        access = ("Requires Legendary or higher", True)
        btn = ("Get Legendary on your phone", 'gold')
        acbtn = ("Unlock for 1200 AC", False)
    elif state=='unlocked':
        access = ("Unlocked with AC", False); btn = ("Download", 'blue')
    elif state=='low':
        access = ("Requires Epic or higher", True)
        btn = ("Get Epic on your phone", 'purple')
        acbtn = ("Unlock for 600 AC", False)
        hint = "Your balance: 270 AC — top up in the phone app"; hint_warn=True
    elif state=='sale':
        access = ("Requires Epic or higher", True)
        btn = ("Get Epic on your phone", 'purple')
        acbtn = ("Unlock for 450 AC", "600")
    elif state=='limit':
        access = ("Free with your account", False); btn = ("Download", 'blue'); hint = "Downloads used: 3 of 10"
    elif state=='notice':
        access = ("Free with your account", False); btn = ("Download", 'blue')
    if rtl:
        A = {"Free with your account":"مجانية مع حسابك","Unlocked with AC":"مفتوحة بـ AC",
             "Requires Epic or higher":"تتطلب Epic أو أعلى","Requires Legendary or higher":"تتطلب Legendary أو أعلى",
             "Sign in to download":"سجّل الدخول للتنزيل","Download":"تنزيل","Downloading...  63%":"جارٍ التنزيل... 63%",
             "Installing...":"جارٍ التثبيت...","Waiting for Minecraft":"بانتظار Minecraft","Installed":"تم التثبيت ✓",
             "Get Epic on your phone":"اشترك في Epic من هاتفك","Get Legendary on your phone":"اشترك في Legendary من هاتفك",
             "Unlock for 600 AC":"افتح مقابل 600 AC","Unlock for 1200 AC":"افتح مقابل 1200 AC","Unlock for 450 AC":"افتح مقابل 450 AC",
             "Minecraft opens automatically after install (if enabled)":"يفتح ماينكرافت تلقائياً بعد التثبيت (إن كان مفعّلاً)",
             "Sign in to get it — same account as your phone":"سجّل الدخول للحصول عليها — نفس حساب هاتفك",
             "Your balance: 270 AC — top up in the phone app":"رصيدك: 270 AC — اشحن من تطبيق الهاتف",
             "Downloads used: 3 of 10":"التنزيلات المستخدمة: 3 من 10"}
        def T(s): return ar(A.get(s, s))
    else:
        def T(s): return s
    pad = 26
    y2 = y + pad
    alh = 28; bt_h = 80; ach = 72
    total = pad + alh + 18 + bt_h + (16+30 if hint else 0) + (14+ach if acbtn else 0) + (16+56 if state=='notice' else 0) + pad
    rrect(d, [x, y, x+bw, y+total], 26, fill=PN, outline=LN, width=2)
    acy = y2+2
    if rtl:
        # start side = right: dot at right, text to its left
        if access[1]:
            rrect(d, [x+bw-pad-26, acy, x+bw-pad, acy+26], 8, outline=PU2, width=3)
        else:
            d.ellipse([x+bw-pad-28, acy, x+bw-pad, acy+28], fill=GR)
        col = PU2 if access[1] else TX
        at = T(access[0])
        d.text((x+bw-pad-28-14-d.textlength(at, font=f22), acy-2), at, font=f22, fill=col)
    else:
        if access[1]:
            rrect(d, [x+pad, acy, x+pad+26, acy+26], 8, outline=PU2, width=3)
        else:
            d.ellipse([x+pad, acy, x+pad+28, acy+28], fill=GR)
        col = PU2 if access[1] else TX
        at = T(access[0])
        d.text((x+pad+42, acy-2), at, font=f22, fill=col)
    y2 += alh + 18
    if btn[1]=='blue': bfill, tcol = BL, (255,255,255)
    elif btn[1]=='purple': bfill, tcol = PU, (255,255,255)
    elif btn[1]=='gold': bfill, tcol = (255,201,64), (44,26,2)
    elif btn[1]=='ok': bfill, tcol = (31,59,48), (124,236,170)
    else: bfill, tcol = (42,54,68), MU
    rrect(d, [x+pad, y2, x+bw-pad, y2+bt_h], 20, fill=bfill)
    if btn[1]=='ok':
        rrect(d, [x+pad, y2, x+bw-pad, y2+bt_h], 20, outline=(46,107,82), width=2)
    if focus_main:
        focus_ring(d, [x+pad, y2, x+bw-pad, y2+bt_h], 20)
    label = T(btn[0])
    if btn[0] in ('Download','Sign in to download'):
        tw = d.textlength(label, font=f28)
        cx = x+bw/2
        if rtl:
            arrow_down(d, cx+tw/2+22, y2+bt_h/2-2, 14, tcol, 3)
            d.text((cx-tw/2-8, y2+bt_h/2-18), label, font=f28, fill=tcol)
        else:
            arrow_down(d, cx-tw/2-24, y2+bt_h/2-2, 14, tcol, 3)
            txt_c(d, cx+14, y2+bt_h/2, label, f28, tcol)
    else:
        txt_c(d, x+bw/2, y2+bt_h/2, label, f28, tcol)
    if progress is not None:
        rrect(d, [x+pad+20, y2+bt_h-22, x+bw-pad-20, y2+bt_h-12], 5, fill=(0,0,0,90))
        rrect(d, [x+pad+20, y2+bt_h-22, x+pad+20+int((bw-2*pad-40)*progress), y2+bt_h-12], 5, fill=(255,255,255))
    y2 += bt_h
    if acbtn:
        y2 += 14
        rrect(d, [x+pad, y2, x+bw-pad, y2+ach], 20, fill=GDBG, outline=GDLINE, width=2)
        if focus_ac:
            focus_ring(d, [x+pad, y2, x+bw-pad, y2+ach], 20)
        t = T(acbtn[0])
        if isinstance(acbtn[1], str):
            tw = d.textlength(t, font=f26)
            cx = x+bw/2
            d.text((cx-tw/2-20, y2+20), t, font=f26, fill=GD)
            old = acbtn[1]
            ow = d.textlength(old, font=fb(20))
            xx = cx+tw/2+14
            d.text((xx, y2+24), old, font=fb(20), fill=MU)
            d.line([(xx, y2+34), (xx+ow, y2+34)], fill=MU, width=2)
        else:
            paste_icon(base, coin, x+pad+22, y2+20, 32)
            txt_c(d, x+bw/2+16, y2+ach/2, t, f26, GD)
        y2 += ach
    if hint:
        y2 += 16
        col = WN if hint_warn else MU
        ht = T(hint)
        words = ht.split(' '); lines=[]; cur=''
        for wd in words:
            t=(cur+' '+wd).strip()
            if d.textlength(t, font=f19) <= bw-2*pad-10: cur=t
            else: lines.append(cur); cur=wd
        if cur: lines.append(cur)
        for i,ln in enumerate(lines[:2]):
            if rtl:
                d.text((x+bw-pad-d.textlength(ln, font=f19), y2+i*28), ln, font=f19, fill=col)
            else:
                d.text((x+pad, y2+i*28), ln, font=f19, fill=col)
        y2 += 30*len(lines[:2])
    if state=='notice':
        y2 += 16
        t = "Download failed — check your connection"
        tw = d.textlength(t, font=f21)
        rrect(d, [x+pad, y2, x+pad+tw+56, y2+56], 28, fill=(42,18,24), outline=(251,76,110,160), width=2)
        d.ellipse([x+pad+22, y2+20, x+pad+38, y2+36], fill=ER)
        d.text((x+pad+50, y2+15), t, font=f21, fill=ER)
        y2 += 56
    y2 += pad
    return y2 - y

def draw_creator_rail(base, x, y, w, rtl=False, titles=None, prices=None, covers=None, badges=None):
    d = ImageDraw.Draw(base)
    f28 = far(28) if rtl else fb(28)
    f20 = fb(20); f22 = fb(22); f19 = far(19) if rtl else fb(19)
    head = ar("المزيد من هذا المطوّر") if rtl else "More by this creator"
    counter = "1–4 / 8"
    d.text((x, y), head, font=f28, fill=TX)
    cw = d.textlength(counter, font=f20)
    d.text((x+w-cw, y+6), counter, font=f20, fill=MU)
    y += 52
    aw = 50
    card_h = 210
    gap = 18
    cards_w = w - 2*aw - 2*gap
    card_w = (cards_w - 3*gap)/4
    # RTL mirror: left end = next (active '‹'), right end = prev (dim '›'); shapes stay '‹' left / '›' right
    dim_left = (not rtl)
    for ax, right, dim in [(x, False, dim_left), (x+w-aw, True, not dim_left)]:
        rrect(d, [ax, y, ax+aw, y+card_h], 8, fill=CTL, outline=LN, width=1)
        col = MU if dim else TX
        chevron(d, ax+aw/2, y+card_h/2, 14, col, right=right, w=6)
        if dim:
            ov = Image.new('RGBA', (aw, card_h), (0,0,0,110))
            base.alpha_composite(ov, (int(ax), int(y)))
    for i in range(4):
        if rtl:
            cx = x+w-aw-gap-(i+1)*card_w-i*gap   # first card at the right (start)
        else:
            cx = x+aw+gap+i*(card_w+gap)
        rrect(d, [cx, y, cx+card_w, y+card_h], 10, fill=PN, outline=LN, width=2)
        im = rounded_img(cover_crop(Image.open(covers[i]).convert('RGB'), int(card_w), 150), 8)
        base.alpha_composite(im, (int(cx), int(y)))
        bd = badges[i]
        bw2 = d.textlength(bd, font=f19)
        if rtl:
            bx = cx+card_w-10-bw2-18
        else:
            bx = cx+10
        rrect(d, [bx, y+150-38, bx+bw2+18, y+150-8], 4, fill=(0,0,0,190), outline=GD, width=2)
        d.text((bx+9, y+150-35), bd, font=f19, fill=GD)
        name = titles[i]
        if d.textlength(name, font=f22) > card_w-28:
            while d.textlength(name+"…", font=f22) > card_w-28 and len(name)>3:
                name = name[:-1]
            name += "…"
        rating = ["4.8","4.5","4.2","4.7"][i]
        pr = prices[i]
        if rtl:
            d.text((cx+card_w-14-d.textlength(name, font=f22), y+162), name, font=f22, fill=TX)
            star(d, cx+card_w-22, y+194, 9, MU)
            d.text((cx+card_w-36-d.textlength(rating, font=f19), y+183), rating, font=f19, fill=MU)
            d.text((cx+14, y+183), pr, font=f19, fill=GD)
        else:
            d.text((cx+14, y+162), name, font=f22, fill=TX)
            star(d, cx+22, y+194, 9, MU)
            d.text((cx+36, y+183), rating, font=f19, fill=MU)
            pw = d.textlength(pr, font=f19)
            d.text((cx+card_w-14-pw, y+183), pr, font=f19, fill=GD)
    return y+card_h

def draw_legend(base, rtl=False):
    d = ImageDraw.Draw(base)
    y = 1012
    f21 = fb(21) if not rtl else far(21)
    items = [(b_cross, "Select" if not rtl else ar("اختيار")),
             (b_circle, "Back" if not rtl else ar("رجوع")),
             (b_tri, "Search" if not rtl else ar("بحث"))]
    if rtl:
        # mirror: start edge = right; label to the LEFT of its icon
        x = W-210
        for icon, label in items:
            lw = d.textlength(label, font=f21)
            x -= 30
            paste_icon(base, icon, x, y, 30)
            x -= 12
            d.text((x-lw, y+1), label, font=f21, fill=MU)
            x -= lw + 44
    else:
        x = 210
        for icon, label in items:
            paste_icon(base, icon, x, y, 30)
            d.text((x+42, y+1), label, font=f21, fill=MU)
            x += 42 + d.textlength(label, font=f21) + 44

covers = ['/tmp/mock/combine-items-add-on-cover.jpg','/tmp/mock/backpacks-and-jetpacks-cover.jpg',
          '/tmp/mock/gravestones-cover.jpg','/tmp/mock/owls-cover.jpg','/tmp/mock/spark-pets-cover.jpg']
titles = ["Zephyr Add-On","Castle Skin","Ocean Texture Pack","Space Add-On"]
prices = ["450 AC","Free","620 AC","1100 AC"]
badges_en = ["Add-On","Skin","Texture Pack","Add-On"]

# ================= EN main page =================
img = Image.new('RGBA', (W,H), (0,0,0,0))
img.alpha_composite(bg.convert('RGBA'))
draw_rail(img, 'left')
draw_topbar(img, rtl=False)
d = ImageDraw.Draw(img)
X0 = 210; X1 = 1830
draw_cover(img, covers[0], X0, 130, 500, 281, r=8)
IX = 750; IW = 560
f52 = ff77(52)
d.text((IX, 128), "COMBINE ITEMS!", font=f52, fill=TX)
d.text((IX, 184), "ADD-ON", font=f52, fill=TX)
paste_icon(img, rounded_img(cover_crop(fox, 42, 42), 12), IX, 252, 42)
d.text((IX+58, 258), "sliver fox", font=fb(24), fill=TX)
draw_meta_row(img, IX, 312)
fy = draw_facts(img, IX, 366, IW, [("Size","995 KB"),("Version","1.0.0"),("Published","2026-11-01"),("Downloads","1,240")])
bh = draw_box(img, 1350, 130, rtl=False, state='free', focus_main=True)
desc = "Combine items in new ways and discover new recipes in your world. Craft, merge and upgrade everything you find — made by sliver fox (silverfoxstudio) for your Minecraft worlds."
top_h = max(281, fy-130, bh)
dy = 130 + top_h + 28
draw_desc(img, X0, dy, X1-X0, desc, fb(23))
ry = dy + 112 + 26
draw_creator_rail(img, X0, ry, X1-X0, rtl=False, titles=titles, prices=prices,
                  covers=covers[1:], badges=badges_en)
draw_legend(img, rtl=False)
img.convert('RGB').save('mine-preview/addonpage-newdesign-en.png')
print("EN saved")

# ================= AR main page (RTL) =================
img = Image.new('RGBA', (W,H), (0,0,0,0))
img.alpha_composite(bg.convert('RGBA'))
draw_rail(img, 'right')
draw_topbar(img, rtl=True)
d = ImageDraw.Draw(img)
cover_x = W-210-500
draw_cover(img, covers[0], cover_x, 130, 500, 281, r=8)
IX = 610; IW = 560
f44 = far(44)
t1 = ar("إضافة دمج العناصر")
d.text((IX+IW-d.textlength(t1, font=f44), 138), t1, font=f44, fill=TX)
paste_icon(img, rounded_img(cover_crop(fox, 42, 42), 12), IX+IW-42, 202, 42)
by = "sliver fox"
d.text((IX+IW-42-16-d.textlength(by, font=fb(24)), 208), by, font=fb(24), fill=TX)
meta_rtl(img, IX+IW, 262)
fy = draw_facts(img, IX, 320, IW, [("الحجم","995 KB"),("الإصدار","1.0.0"),("تاريخ النشر","2026-11-01"),("التنزيلات","1,240")], rtl=True)
bh = draw_box(img, 90, 130, rtl=True, state='free', focus_main=True)
desc_ar = ar("اجمع العناصر بطرق جديدة واكتشف وصفات جديدة في عالمك. اصنع وادمج وطوّر كل ما تجده — من صنع sliver fox (silverfoxstudio) لعوالم ماينكرافت.")
draw_desc(img, 90, dy, 1740, desc_ar, far(23), rtl=True)
draw_creator_rail(img, 90, ry, 1740, rtl=True, titles=titles, prices=prices,
                  covers=covers[1:], badges=[ar("إضافة"), ar("سكين"), ar("حزمة قوام"), ar("إضافة")])
draw_legend(img, rtl=True)
img.convert('RGB').save('mine-preview/addonpage-newdesign-ar.png')
print("AR saved")

# ================= states sheet (6 key action-box states) =================
img = Image.new('RGBA', (W,H), (0,0,0,0))
img.alpha_composite(bg.convert('RGBA'))
d = ImageDraw.Draw(img)
d.text((180, 56), "ACTION BOX — KEY STATES", font=ff77(36), fill=TX)
states = [("Guest",'guest'),("Downloading 63%",'downloading'),("Installed",'installed'),
          ("Epic needed + AC (600)",'epic'),("Legendary + AC (1200)",'legendary'),("Low balance",'low')]
xs = [180, 720, 1260]
ys = [170, 640]
for i,(lab,st) in enumerate(states):
    x = xs[i%3]; y = ys[i//3]
    d.text((x+8, y-42), lab, font=fb(24), fill=MU)
    draw_box(img, x, y, state=st, focus_main=(i==0))
img.convert('RGB').save('mine-preview/addonpage-newdesign-states.png')
print("states saved")
