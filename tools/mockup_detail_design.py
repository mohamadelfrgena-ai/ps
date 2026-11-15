#!/usr/bin/env python3
# Renders the NEW addon-page design (AddonStation-PS4-AddonPage spec) in the app's real visual style.
# v2: real app side rail (sliced from tools/host_shots store render), redesigned creator rail
# (app-style gradient cards, floating arrows, counter pill, gold accents), Arabic typography polish.
# Outputs: mine-preview/addonpage-newdesign-{en,ar,states}.png   Run from the repo root.
import struct, os, math, zipfile
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import arabic_reshaper
from bidi.algorithm import get_display

W, H = 1920, 1080
PN=(21,24,29); LN=(42,56,70); TX=(238,243,248); MU=(142,160,179)
GD=(255,214,90); GR=(20,184,138); BL=(26,111,214); WN=(251,160,60); ER=(255,128,150)
GDLINE=(185,138,28); GDBG=(48,36,10); PU=(124,58,237); PU2=(199,155,255)
STAT=(74,168,255); CBT=(38,81,119); CBB=(18,34,46); CHIPC=(26,55,78)

# ---------- self-contained asset prep ----------
os.makedirs('/tmp/mock', exist_ok=True)
data = open('ps4-addon-test/assets/ui.pak','rb').read()
assert data[:8] == b'ASUI0001'
(n,) = struct.unpack_from('<I', data, 8); off = 12; E = {}
for _ in range(n):
    nl, dl = struct.unpack_from('<HI', data, off); off += 4+2
    name = data[off:off+nl].decode(); off += nl
    E[name] = data[off:off+dl]; off += dl
for k in ('font_ar','font_body','font_f77','fox_avatar','icon_coin','btn_cross','btn_circle','btn_triangle','logo_app'):
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

def grad_panel(w, h, top, bottom, r=14):
    g = Image.new('RGB', (w, h))
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = y/(h-1) if h > 1 else 0
        d.line([(0,y),(w,y)], fill=tuple(int(top[i]+(bottom[i]-top[i])*t) for i in range(3)))
    return rounded_img(g, r)

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
    dx = -1 if right else 1
    d.line([(cx+dx*s*0.45, cy-s*0.7), (cx-dx*s*0.45, cy), (cx+dx*s*0.45, cy+s*0.7)], fill=color, width=w, joint='curve')

def focus_ring(d, box, r, color=GD, wdt=3):
    x0,y0,x1,y1 = box
    d.rounded_rectangle([x0-4,y0-4,x1+4,y1+4], radius=r+4, outline=color, width=wdt)

def txt_c(d, cx, cy, s, font, color):
    x0,y0,x1,y1 = d.textbbox((0,0), s, font=font)
    d.text((cx-(x1+x0)/2, cy-(y1+y0)/2), s, font=font, fill=color)

# blurred background = his store image; the item cover stays the cover
base = Image.open('mine-preview/detail-bg-store.jpg').convert('RGB')
bg = cover_crop(base, W, H).filter(ImageFilter.GaussianBlur(28))
ov = Image.new('RGB', (W,H), (6,12,19))
bg = Image.blend(bg, ov, 0.55)

fox = Image.open('/tmp/pk_fox_avatar.png').convert('RGBA')
coin = Image.open('/tmp/pk_icon_coin.png').convert('RGBA')
logo = Image.open('/tmp/pk_logo_app.png').convert('RGBA')
b_cross = Image.open('/tmp/pk_btn_cross.png').convert('RGBA')
b_circle = Image.open('/tmp/pk_btn_circle.png').convert('RGBA')
b_tri = Image.open('/tmp/pk_btn_triangle.png').convert('RGBA')
rail_slice = Image.open('mine-preview/rail-app-slice.png').convert('RGBA')

def paste_icon(base, icon, x, y, size):
    im = icon.resize((size, size), Image.LANCZOS)
    base.alpha_composite(im, (int(x), int(y)))

def draw_cover(base, img_path, x, y, w, h, r=8, border=LN, bw=3):
    im = rounded_img(cover_crop(Image.open(img_path).convert('RGB'), w, h), r)
    base.alpha_composite(im, (x, y))
    d = ImageDraw.Draw(base)
    d.rounded_rectangle([x,y,x+w-1,y+h-1], radius=r, outline=border, width=bw)

# ---------- real app side rail ----------
RAIL_W = 128
EN_LABELS = [("Home",422,0),("Offers",522,0),("Store",621,1),("Downloads",721,0),
             ("Plans",855,0),("Account",957,0),("Settings",1051,0)]
LABEL_BANDS = [(410,434),(509,535),(608,634),(707,735),(844,866),(944,970),(1038,1064)]

def paste_real_rail(img, side='right', english=False):
    sl = rail_slice.copy()
    d = ImageDraw.Draw(sl)
    if english:
        # repaint Arabic labels with the row's own bg (sampled beside the text) and write EN
        px = sl.load()
        for (b0,b1) in LABEL_BANDS:
            for y in range(b0,b1+1):
                r1,g1,b1c = px[16,y][:3]; r2,g2,b2c = px[108,y][:3]
                for x in range(8,121):
                    t = (x-8)/112
                    col = (int(r1+(r2-r1)*t), int(g1+(g2-g1)*t), int(b1c+(b2c-b1c)*t))
                    d.point((x,y), fill=col+(255,))
        f15 = fb(15)
        for (txt,yc,sel) in EN_LABELS:
            tw = d.textlength(txt, font=f15)
            d.text((64-tw/2, yc-9), txt, font=f15, fill=(255,255,255) if sel else (176,196,210))
    if side == 'left':
        # EN: rail at left — move the blue edge bar from the slice's right edge to x=0..4
        px = sl.load()
        for y in range(555,600):
            for x in range(118,128):
                r,g,b,a = px[x,y]
                bgc = px[16,y][:3]
                px[x,y] = bgc+(255,)
        d2 = ImageDraw.Draw(sl)
        d2.rounded_rectangle([1,560,6,593], radius=2, fill=STAT+(255,))
        # feather inner (right) edge so it melts into the page bg
        mask = Image.new('L', sl.size, 255)
        md = ImageDraw.Draw(mask)
        for x in range(24):
            md.line([(x,0),(x,1080)], fill=int(255*(x/24)))
        sl.putalpha(mask)
        img.alpha_composite(sl, (0,0))
    else:
        # AR: rail at right, slice as-is; feather inner (left) edge
        mask = Image.new('L', sl.size, 255)
        md = ImageDraw.Draw(mask)
        for x in range(24):
            md.line([(x,0),(x,1080)], fill=int(255*(1-x/24)))
        sl.putalpha(mask)
        img.alpha_composite(sl, (W-RAIL_W,0))

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
    # gold-bordered AC pill (like the real app)
    pill_w = 180; px_ = (cx2-tw-22-pill_w) if not rtl else (cx2+tw+22)
    rrect(d, [px_, 54, px_+pill_w, 98], 22, fill=GDBG, outline=GDLINE, width=2)
    paste_icon(base, coin, px_+14, 62, 30)
    d.text((px_+54, 66), "1,250", font=fb(21), fill=GD)
    d.text((px_+54+d.textlength("1,250", font=fb(21))+8, 68), "AC", font=fb(17), fill=MU)

def draw_meta_row(base, x, y):
    d = ImageDraw.Draw(base)
    f19 = fb(19)
    cx = x
    def chip_ltr(text, fg, bgc, ol, gap_after):
        nonlocal cx
        tw = d.textlength(text, font=f19)
        rrect(d, [cx, y, cx+tw+32, y+40], 10, fill=bgc, outline=ol, width=2)
        d.text((cx+16, y+10), text, font=f19, fill=fg)
        cx += tw+32+gap_after
    chip_ltr("Add-On", MU, None, LN, 16)
    chip_ltr("Free", (111,177,255), (14,32,54), (45,93,150), 18)
    star(d, cx+11, y+20, 12, GD)
    d.text((cx+28, y+10), "4.3", font=f19, fill=GD)
    cx += 28+50
    arrow_down(d, cx+10, y+19, 10, TX, 2)
    d.text((cx+26, y+10), "1.2K", font=f19, fill=TX)
    cx += 26+66
    d.text((cx, y+10), "995 KB", font=f19, fill=TX)
    cx += d.textlength("995 KB", font=f19)+18
    d.text((cx, y+10), "v1.0.0", font=f19, fill=TX)
    cx += d.textlength("v1.0.0", font=f19)+18
    nw = d.textlength("NEW", font=fb(16))
    rrect(d, [cx, y+4, cx+nw+24, y+36], 8, fill=GR)
    d.text((cx+12, y+11), "NEW", font=fb(16), fill=(255,255,255))

def meta_rtl(img, xr, y):
    d = ImageDraw.Draw(img)
    f19 = far(19)
    cx = xr
    def chip(text, fg, bgc, ol):
        nonlocal cx
        t = ar(text)
        tw = d.textlength(t, font=f19)
        rrect(d, [cx-tw-32, y, cx, y+40], 10, fill=bgc, outline=ol, width=2)
        d.text((cx-16-tw, y+10), t, font=f19, fill=fg)
        cx -= tw+32+16
    def pair(num, glyph):
        nonlocal cx
        if glyph=='star':
            star(d, cx-12, y+20, 12, GD)
        else:
            arrow_down(d, cx-10, y+19, 10, TX, 2)
        d.text((cx-28-d.textlength(num,font=f19), y+10), num, font=f19, fill=GD if glyph=='star' else TX)
        cx -= 28+d.textlength(num,font=f19)+16
    chip("إضافة", MU, None, LN)
    chip("مجاني", (111,177,255), (14,32,54), (45,93,150))
    pair("4.3", 'star')
    pair("1.2K", 'arrow')
    for t in ("995 KB","v1.0.0"):
        d.text((cx-d.textlength(t,font=f19), y+10), t, font=f19, fill=TX)
        cx -= d.textlength(t,font=f19)+18
    nw = d.textlength(ar("جديد"), font=far(16))
    rrect(d, [cx-nw-24, y+4, cx, y+36], 8, fill=GR)
    d.text((cx-12-nw, y+11), ar("جديد"), font=far(16), fill=(255,255,255))

def draw_facts(base, x, y, w, cells, rtl=False):
    d = ImageDraw.Draw(base)
    h = 72
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
            d.text((tx-d.textlength(val,font=f20)-20, y+36), val, font=f20, fill=TX)
        else:
            d.text((cx+20, y+12), lab, font=f16, fill=MU)
            d.text((cx+20, y+36), val, font=f20, fill=TX)
    return y+h

def draw_desc(base, x, y, w, text, font, rtl=False, h=116):
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
            d.text((x+w-26-d.textlength(ln, font=font), y+22+i*44), ln, font=font, fill=TX)
        else:
            d.text((x+26, y+22+i*44), ln, font=font, fill=TX)
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

# ---------- redesigned creator rail (app-style) ----------
RATINGS = ["4.8","4.5","4.2","4.7"]
DL_COUNTS = ["1.3K","860","2.1K","940"]

def draw_creator_rail2(base, x, y, w, rtl=False, titles=None, prices=None, covers=None, badges=None):
    d = ImageDraw.Draw(base)
    f28 = far(28) if rtl else fb(28)
    f18 = far(18) if rtl else fb(18)
    f22 = far(22) if rtl else fb(22)
    f19 = far(19) if rtl else fb(19)
    head = ar("المزيد من هذا المطوّر") if rtl else "More by this creator"
    # gold accent bar + title
    if rtl:
        rrect(d, [x+w-5, y+4, x+w, y+32], 2, fill=GD)
        d.text((x+w-20-d.textlength(head, font=f28), y), head, font=f28, fill=TX)
    else:
        rrect(d, [x, y+4, x+5, y+32], 2, fill=GD)
        d.text((x+20, y), head, font=f28, fill=TX)
    # counter pill at the end side
    counter = "1–4 / 8"
    cw_ = d.textlength(counter, font=f18)+36
    px_ = (x+w-cw_) if not rtl else x
    rrect(d, [px_, y-2, px_+cw_, y+34], 14, fill=(18,26,35), outline=LN, width=2)
    txt_c(d, px_+cw_/2, y+16, counter, f18, MU)
    y += 56
    aw = 52; card_h = 252; gap = 14
    zone_x = x+aw+14
    zone_w = w-2*(aw+14)
    card_w = (zone_w-3*gap)/4
    cy_mid = y+card_h//2
    # floating rounded arrow buttons (prev dims at the start edge on page 1)
    dim_start = True
    for ax, right, dim in [(x, False, dim_start), (x+w-aw, True, not dim_start)] if not rtl else \
                          [(x, False, not dim_start), (x+w-aw, True, dim_start)]:
        rrect(d, [ax, cy_mid-26, ax+aw, cy_mid+26], 16, fill=(18,26,35), outline=LN, width=2)
        col = MU if dim else TX
        chevron(d, ax+aw/2, cy_mid, 13, col, right=right, w=6)
        if dim:
            ov = Image.new('RGBA', (aw, 52), (0,0,0,120))
            base.alpha_composite(ov, (int(ax), int(cy_mid-26)))
    for i in range(4):
        if rtl:
            cx = x+w-aw-14-(i+1)*card_w-i*gap
        else:
            cx = zone_x+i*(card_w+gap)
        panel = grad_panel(int(card_w), card_h, CBT, CBB, r=14)
        base.alpha_composite(panel, (int(cx), int(y)))
        cw2 = int(card_w)-16
        im = rounded_img(cover_crop(Image.open(covers[i]).convert('RGB'), cw2, 152), 10)
        base.alpha_composite(im, (int(cx+8), int(y+8)))
        bd = badges[i]
        bw2 = d.textlength(bd, font=f19)
        if rtl:
            bx = cx+card_w-8-bw2-18
        else:
            bx = cx+8
        rrect(d, [bx, y+8+152-38, bx+bw2+18, y+8+152-8], 4, fill=(0,0,0,190), outline=GD, width=2)
        d.text((bx+9, y+8+152-35), bd, font=f19, fill=GD)
        # name
        name = titles[i]
        if d.textlength(name, font=f22) > card_w-32:
            while d.textlength(name+"…", font=f22) > card_w-32 and len(name)>3:
                name = name[:-1]
            name += "…"
        if rtl:
            d.text((cx+card_w-16-d.textlength(name, font=f22), y+170), name, font=f22, fill=TX)
        else:
            d.text((cx+16, y+170), name, font=f22, fill=TX)
        # rating: 5 mini stars + number
        r = float(RATINGS[i])
        sx = (cx+card_w-16-96) if rtl else cx+16
        for s5 in range(5):
            full = (s5+1) <= int(r)
            star(d, sx+s5*19+8, y+208, 8, GD if full else (70,88,104))
        rt = RATINGS[i]
        if rtl:
            d.text((sx-12-d.textlength(rt, font=f18), y+199), rt, font=f18, fill=MU)
        else:
            d.text((sx+100, y+199), rt, font=f18, fill=MU)
        # price chip + downloads
        pr = prices[i]
        if pr == "Free" or pr == "مجاني":
            txt = ar("مجاني") if rtl else "Free"
            pw = d.textlength(txt, font=f19)+28
            pxx = (cx+card_w-16-pw) if rtl else cx+16
            rrect(d, [pxx, y+222, pxx+pw, y+250], 10, fill=CHIPC)
            txt_c(d, pxx+pw/2, y+236, txt, f19, (130,195,255))
        else:
            txt = pr
            pw = d.textlength(txt, font=f19)+56
            pxx = (cx+card_w-16-pw) if rtl else cx+16
            rrect(d, [pxx, y+222, pxx+pw, y+250], 10, fill=GDBG, outline=GDLINE, width=2)
            paste_icon(base, coin, pxx+10, y+226, 22)
            d.text((pxx+38, y+227), txt, font=f19, fill=GD)
        dc = DL_COUNTS[i]
        arrow_down(d, (cx+16+8) if rtl else (cx+card_w-16-42), y+236, 8, MU, 2)
        if rtl:
            d.text((cx+30, y+227), dc, font=f18, fill=MU)
        else:
            d.text((cx+card_w-16-d.textlength(dc, font=f18), y+227), dc, font=f18, fill=MU)
        if i == 0:
            focus_ring(d, [cx, y, cx+card_w, y+card_h], 14)
    return y+card_h

def draw_legend(base, rtl=False):
    d = ImageDraw.Draw(base)
    y = 1012
    f21 = fb(21) if not rtl else far(21)
    items = [(b_cross, "Select" if not rtl else ar("اختيار")),
             (b_circle, "Back" if not rtl else ar("رجوع")),
             (b_tri, "Search" if not rtl else ar("بحث"))]
    if rtl:
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
badges_ar = [ar("إضافة"), ar("سكين"), ar("حزمة قوام"), ar("إضافة")]

# ================= EN main page =================
img = Image.new('RGBA', (W,H), (0,0,0,0))
img.alpha_composite(bg.convert('RGBA'))
paste_real_rail(img, 'left', english=True)
draw_topbar(img, rtl=False)
d = ImageDraw.Draw(img)
X0 = 210; X1 = 1830
draw_cover(img, covers[0], X0, 130, 500, 281, r=8)
IX = 750; IW = 560
f52 = ff77(52)
d.text((IX, 128), "COMBINE ITEMS!", font=f52, fill=TX)
d.text((IX, 184), "ADD-ON", font=f52, fill=TX)
rrect(d, [IX, 252, IX+72, 258], 3, fill=GD)          # gold accent under title
paste_icon(img, rounded_img(cover_crop(fox, 42, 42), 12), IX, 276, 42)
d.text((IX+58, 282), "sliver fox", font=fb(24), fill=TX)
draw_meta_row(img, IX, 336)
fy = draw_facts(img, IX, 392, IW, [("Size","995 KB"),("Version","1.0.0"),("Published","2026-11-01"),("Downloads","1,240")])
bh = draw_box(img, 1350, 130, rtl=False, state='free', focus_main=True)
desc = "Combine items in new ways and discover new recipes in your world. Craft, merge and upgrade everything you find — made by sliver fox (silverfoxstudio) for your Minecraft worlds."
top_h = max(281, fy-130, bh)
dy = 130 + top_h + 28
draw_desc(img, X0, dy, X1-X0, desc, fb(23))
ry = dy + 116 + 26
draw_creator_rail2(img, X0, ry, X1-X0, rtl=False, titles=titles, prices=prices,
                   covers=covers[1:], badges=badges_en)
draw_legend(img, rtl=False)
img.convert('RGB').save('mine-preview/addonpage-newdesign-en.png')
print("EN saved")

# ================= AR main page (RTL) =================
img = Image.new('RGBA', (W,H), (0,0,0,0))
img.alpha_composite(bg.convert('RGBA'))
paste_real_rail(img, 'right', english=False)
draw_topbar(img, rtl=True)
d = ImageDraw.Draw(img)
cover_x = W-210-500
draw_cover(img, covers[0], cover_x, 130, 500, 281, r=8)
IX = 610; IW = 560
f52ar = far(52)
t1 = ar("إضافة دمج العناصر")
d.text((IX+IW-d.textlength(t1, font=f52ar), 132), t1, font=f52ar, fill=TX)
rrect(d, [IX+IW-72, 202, IX+IW, 208], 3, fill=GD)    # gold accent (start side = right)
paste_icon(img, rounded_img(cover_crop(fox, 42, 42), 12), IX+IW-42, 226, 42)
by = "sliver fox"
d.text((IX+IW-42-16-d.textlength(by, font=fb(24)), 232), by, font=fb(24), fill=TX)
meta_rtl(img, IX+IW, 286)
fy = draw_facts(img, IX, 342, IW, [("الحجم","995 KB"),("الإصدار","1.0.0"),("تاريخ النشر","2026-11-01"),("التنزيلات","1,240")], rtl=True)
bh = draw_box(img, 90, 130, rtl=True, state='free', focus_main=True)
desc_ar = ar("اجمع العناصر بطرق جديدة واكتشف وصفات جديدة في عالمك. اصنع وادمج وطوّر كل ما تجده — من صنع sliver fox (silverfoxstudio) لعوالم ماينكرافت.")
draw_desc(img, 90, dy, 1740, desc_ar, far(23), rtl=True)
draw_creator_rail2(img, 90, ry, 1740, rtl=True, titles=titles, prices=prices,
                   covers=covers[1:], badges=badges_ar)
draw_legend(img, rtl=True)
img.convert('RGB').save('mine-preview/addonpage-newdesign-ar.png')
print("AR saved")

# ================= states sheet =================
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
