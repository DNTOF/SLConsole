/* SLConsole bottom-nav concept renderer. Pure function of time so frames are deterministic. */
(function () {
  const W = 1080;
  const H = 2400;
  const TABS = ['概览', '玩家', '控制台', '中心'];
  const ACTIONS = [
    { id: 'broadcast', label: '广播' },
    { id: 'restart', label: '重启回合', danger: true },
    { id: 'cassie', label: 'CASSIE' },
  ];

  const q = new URLSearchParams(location.search);
  const mode = q.get('theme') === 'light' ? 'light' : 'dark';
  const concept = document.body.dataset.concept || 'dust';

  const canvas = document.getElementById('screen');
  const ctx = canvas.getContext('2d');

  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function smooth(t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
  function easeOut(t) { t = clamp(t, 0, 1); return 1 - Math.pow(1 - t, 3); }
  function easeInOut(t) {
    t = clamp(t, 0, 1);
    return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  }
  function rgba(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  function hash(i) {
    const x = Math.sin(i * 127.1 + 311.7) * 43758.5453;
    return x - Math.floor(x);
  }
  function roundRect(x, y, w, h, r) {
    const rr = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + rr, y);
    ctx.arcTo(x + w, y, x + w, y + h, rr);
    ctx.arcTo(x + w, y + h, x, y + h, rr);
    ctx.arcTo(x, y + h, x, y, rr);
    ctx.arcTo(x, y, x + w, y, rr);
    ctx.closePath();
  }
  function setFont(font) { ctx.font = font; }
  function fillText(str, x, y, font, color, align) {
    setFont(font);
    ctx.fillStyle = color;
    ctx.textAlign = align || 'left';
    ctx.textBaseline = 'top';
    ctx.fillText(str, x, y);
  }
  function textWidth(str, font) {
    setFont(font);
    return ctx.measureText(str).width;
  }

  function timeline(t) {
    const segs = [
      { a: 0, b: 1.15, from: 0, to: 0, move: false },
      { a: 1.15, b: 1.78, from: 0, to: 1, move: true },
      { a: 1.78, b: 2.55, from: 1, to: 1, move: false },
      { a: 2.55, b: 3.18, from: 1, to: 2, move: true },
      { a: 3.18, b: 4.05, from: 2, to: 2, move: false },
      { a: 4.05, b: 5.30, from: 2, to: 2, move: false, action: true },
    ];
    let s = segs[segs.length - 1];
    for (const seg of segs) {
      if (t < seg.b) { s = seg; break; }
    }
    const u = clamp((t - s.a) / (s.b - s.a), 0, 1);
    const p = s.move ? easeInOut(u) : 0;
    let press = 0;
    let menu = 0;
    if (s.action) {
      const dt = t - s.a;
      if (dt < 0.10) press = smooth(dt / 0.10);
      else if (dt < 0.28) press = 1 - smooth((dt - 0.10) / 0.18);
      menu = smooth(clamp((dt - 0.14) / 0.26, 0, 1));
    }
    const index = s.move ? (p < 1 ? s.from : s.to) : s.to;
    return { t, from: s.from, to: s.to, p, moving: s.move, press, menu, index };
  }

  function opsSkin(dark, concept) {
    const s = {
      concept,
      dark,
      bg: dark ? '#10151C' : '#F4F1EB',
      card: dark ? '#161B22' : '#FFFcf8',
      line: dark ? '#2C3542' : '#E2DCD2',
      text: dark ? '#E7EDF3' : '#1A1E24',
      muted: dark ? '#8B97A6' : '#5C564C',
      faint: dark ? '#232B36' : '#E8E2D8',
      sakura: dark ? '#F6B8D1' : '#C45E86',
      lavender: dark ? '#C9B6F2' : '#6E5CB4',
      sky: dark ? '#A9D4F5' : '#4E8EB4',
      online: dark ? '#8FCBB0' : '#2C7A52',
      danger: dark ? '#E7A29C' : '#A33B32',
      radius: concept === 'matrix' ? 18 : 26,
      hairline: concept === 'caret',
      map: concept,
      ui(weight, px) {
        return `${weight} ${px}px "Noto Sans SC", "WenQuanYi Micro Hei", sans-serif`;
      },
      mono(px) {
        return `400 ${px}px "JetBrains Mono", ui-monospace, monospace`;
      },
    };
    s.label = s.ui(400, 30);
    s.title = s.ui(500, 84);
    s.body = s.ui(400, 30);
    s.small = s.ui(400, 26);
    s.num = concept === 'dust' ? s.ui(500, 112) : s.mono(108);
    s.numSm = concept === 'dust' ? s.ui(500, 48) : s.mono(46);
    return s;
  }

  function paperSkin(dark) {
    return {
      concept: 'paper',
      dark,
      bg: dark ? '#1A1916' : '#ECE9E0',
      card: dark ? '#222119' : '#F5F3EC',
      line: dark ? '#3A3830' : '#D8D5CC',
      text: dark ? '#EAE7DC' : '#141413',
      muted: dark ? '#9D9A91' : '#6B6860',
      faint: dark ? '#2E2D27' : '#E8E6DC',
      sakura: '#D97757',
      lavender: '#6A9BCC',
      sky: '#788C5D',
      sand: '#C4B99A',
      online: '#6B8F47',
      danger: '#C0453A',
      orange: '#D97757',
      radius: 20,
      hairline: false,
      map: 'paper',
      ui(weight, px) {
        return `${weight} ${px}px Poppins, "Noto Sans SC", "WenQuanYi Micro Hei", sans-serif`;
      },
      serif(px) {
        return `400 ${px}px "LXGW WenKai", "Songti SC", serif`;
      },
      lora(weight, px) {
        return `${weight} ${px}px Lora, Georgia, serif`;
      },
      mono(px) {
        return `400 ${px}px "JetBrains Mono", ui-monospace, monospace`;
      },
      label: null,
    };
  }

  function skin() {
    if (concept === 'paper') {
      const s = paperSkin(mode === 'dark');
      s.label = s.ui(400, 28);
      s.title = s.serif(92);
      s.body = s.serif(32);
      s.small = s.ui(400, 24);
      s.num = s.lora(400, 128);
      s.numSm = s.lora(400, 48);
      return s;
    }
    return opsSkin(mode === 'dark', concept);
  }

  const S0 = skin();

  function card(x, y, w, h, r) {
    roundRect(x, y, w, h, r == null ? S0.radius : r);
    if (S0.hairline) {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.stroke();
    } else {
      ctx.fillStyle = S0.card;
      ctx.fill();
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }
  }

  function dot(x, y, r, color) {
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
  }

  function statusBar() {
    ctx.save();
    const y = 58;
    fillText('21:06', 56, y, S0.ui(500, 40), S0.text);
    const col = S0.text;
    let x = 860;
    for (let i = 0; i < 4; i++) {
      const h = 8 + i * 6;
      ctx.fillStyle = rgba(col, i < 3 ? 0.9 : 0.28);
      roundRect(x, y + 28 - h, 7, h, 2);
      ctx.fill();
      x += 12;
    }
    x += 16;
    ctx.strokeStyle = rgba(col, 0.9);
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.arc(x + 16, y + 24, 7, Math.PI * 1.15, Math.PI * 1.85);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(x + 16, y + 26, 13, Math.PI * 1.18, Math.PI * 1.82);
    ctx.stroke();
    dot(x + 16, y + 26, 2.4, col);
    x += 48;
    ctx.lineWidth = 2.5;
    roundRect(x, y + 8, 46, 24, 5);
    ctx.strokeStyle = rgba(col, 0.9);
    ctx.stroke();
    ctx.fillStyle = rgba(col, 0.9);
    roundRect(x + 4, y + 12, 26, 16, 3);
    ctx.fill();
    ctx.fillRect(x + 48, y + 15, 3, 10);
    ctx.restore();
  }

  function homeIndicator(y) {
    ctx.fillStyle = rgba(S0.text, S0.dark ? 0.45 : 0.28);
    roundRect(W / 2 - 78, y, 156, 8, 4);
    ctx.fill();
  }

  function titleBlock(y, title) {
    if (concept === 'paper') {
      fillText('正在值守', 56, y, S0.ui(400, 26), S0.muted);
      y += 40;
      fillText(title, 56, y, S0.title, S0.text);
      const tw = textWidth(title, S0.title);
      if (title === '概览' || title === '玩家' || title === '控制台') {
        /* cursor-less; the orange rule belongs to the nav */
      }
      y += 108;
      dot(72, y + 16, 8, S0.online);
      fillText('1939 Games', 96, y, S0.ui(500, 32), S0.text);
      fillText('在线', 96 + textWidth('1939 Games', S0.ui(500, 32)) + 20, y + 4, S0.ui(400, 26), S0.muted);
      return y + 56;
    }
    fillText(title, 56, y, S0.title, S0.text);
    if (concept === 'caret') {
      const tw = textWidth(title, S0.title);
      const blink = (S0 && true);
      const on = (timeline(window.__drawT || 0).t % 1.05) < 0.72;
      ctx.fillStyle = on ? S0.lavender : rgba(S0.lavender, 0.25);
      ctx.fillRect(56 + tw + 16, y + 18, 18, 58);
    }
    y += 112;
    const pillW = 32 + 28 + textWidth('1939 Games', S0.ui(500, 32)) + 36;
    const pillH = 72;
    roundRect(56, y, pillW, pillH, pillH / 2);
    ctx.fillStyle = concept === 'caret' ? 'transparent' : S0.card;
    if (concept !== 'caret') ctx.fill();
    ctx.strokeStyle = S0.line;
    ctx.lineWidth = 1.5;
    ctx.stroke();
    dot(56 + 32, y + pillH / 2, 8, S0.online);
    fillText('1939 Games', 56 + 56, y + 18, S0.ui(500, 32), S0.text);
    return y + pillH;
  }

  function hero(y) {
    const x = 56;
    const w = W - 112;
    const h = concept === 'paper' ? 360 : 332;
    if (concept !== 'caret') card(x, y, w, h);
    else {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x, y + h);
      ctx.lineTo(x + w, y + h);
      ctx.stroke();
    }
    const pad = 40;
    fillText('在线', x + pad, y + 36, concept === 'paper' ? S0.ui(400, 26) : S0.small, S0.muted);
    const numFont = S0.num;
    fillText('18', x + pad, y + 84, numFont, S0.text);
    const nw = textWidth('18', numFont);
    fillText('/ 40', x + pad + nw + 16, y + 148, concept === 'paper' ? S0.lora(400, 40) : S0.ui(400, 40), S0.muted);

    if (concept === 'paper') {
      fillText('TPS', x + w - 250, y + 48, S0.ui(400, 24), S0.muted);
      fillText('60', x + w - 250, y + 82, S0.lora(400, 56), S0.text);
      fillText('回合', x + w - 250, y + 168, S0.ui(400, 24), S0.muted);
      fillText('12:34', x + w - 250, y + 202, S0.lora(400, 56), S0.text);
    } else {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x + pad, y + 230);
      ctx.lineTo(x + w - pad, y + 230);
      ctx.stroke();
      fillText('TPS', x + pad, y + 250, S0.small, S0.muted);
      fillText('回合', x + w / 2, y + 250, S0.small, S0.muted);
      fillText('60', x + pad, y + 282, S0.numSm, S0.text);
      fillText('12:34', x + w / 2, y + 282, S0.numSm, S0.text);
    }
    return y + h;
  }

  function statPair(y) {
    if (concept === 'paper') return y;
    const gap = 20;
    const w = (W - 112 - gap) / 2;
    const h = 132;
    const items = [
      ['延迟', '24 ms'],
      ['核弹', '未激活'],
    ];
    items.forEach((it, i) => {
      const x = 56 + i * (w + gap);
      if (!S0.hairline) card(x, y, w, h, S0.radius);
      fillText(it[0], x + (S0.hairline ? 4 : 28), y + 24, S0.small, S0.muted);
      fillText(it[1], x + (S0.hairline ? 4 : 28), y + 64, S0.ui(500, 36), S0.text);
    });
    return y + h;
  }

  function floorPlan(x, y, w, h) {
    ctx.save();
    roundRect(x, y, w, h, 12);
    ctx.clip();
    if (concept === 'matrix') {
      const step = 16;
      for (let yy = y + 10; yy < y + h - 6; yy += step) {
        for (let xx = x + 10; xx < x + w - 6; xx += step) {
          const inRoom = (xx > x + 24 && xx < x + w * 0.62 && yy > y + 18 && yy < y + h * 0.55)
            || (xx > x + w * 0.42 && xx < x + w - 18 && yy > y + h * 0.38 && yy < y + h - 16);
          ctx.fillStyle = rgba(S0.muted, inRoom ? 0.55 : 0.16);
          ctx.fillRect(xx, yy, 3.2, 3.2);
        }
      }
      dot(x + w * 0.32, y + h * 0.32, 5, S0.sakura);
      dot(x + w * 0.72, y + h * 0.62, 5, S0.sky);
      dot(x + w * 0.5, y + h * 0.48, 5, S0.lavender);
    } else if (concept === 'caret') {
      ctx.strokeStyle = S0.muted;
      ctx.lineWidth = 2;
      ctx.strokeRect(x + 16, y + 16, w - 32, h - 32);
      ctx.strokeRect(x + 16, y + 16, w * 0.46, h * 0.48);
      ctx.strokeRect(x + 16 + w * 0.46, y + 16 + h * 0.42, w - 32 - w * 0.46, h - 32 - h * 0.42);
      ctx.fillStyle = S0.lavender;
      ctx.fillRect(x + w * 0.58, y + h * 0.22, 14, 14);
    } else if (concept === 'paper') {
      ctx.strokeStyle = S0.sand || '#C4B99A';
      ctx.lineWidth = 1.5;
      ctx.strokeRect(x + 12, y + 12, w - 24, h - 24);
      ctx.strokeStyle = S0.line;
      ctx.beginPath();
      ctx.moveTo(x + 12, y + h * 0.46);
      ctx.lineTo(x + w - 12, y + h * 0.46);
      ctx.moveTo(x + w * 0.48, y + 12);
      ctx.lineTo(x + w * 0.48, y + h - 12);
      ctx.stroke();
      dot(x + w * 0.28, y + h * 0.26, 5, S0.orange);
    } else {
      ctx.strokeStyle = S0.muted;
      ctx.lineWidth = 2.25;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      roundRect(x + 16, y + 16, w - 32, h - 32, 12);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(x + 16, y + h * 0.58);
      ctx.lineTo(x + w * 0.62, y + h * 0.58);
      ctx.lineTo(x + w * 0.62, y + 16);
      ctx.moveTo(x + w * 0.62, y + h * 0.58);
      ctx.lineTo(x + w * 0.62, y + h - 16);
      ctx.stroke();
      dot(x + w * 0.34, y + h * 0.34, 5, S0.lavender);
      dot(x + w * 0.78, y + h * 0.74, 5, S0.sky);
    }
    ctx.restore();
  }

  function mapCard(y) {
    const x = 56;
    const w = W - 112;
    const h = concept === 'paper' ? 250 : 268;
    if (concept !== 'caret') card(x, y, w, h);
    fillText('地图', x + 36, y + 28, concept === 'paper' ? S0.ui(500, 28) : S0.ui(500, 32), S0.text);
    fillText('轻收容区', x + 36, y + 78, concept === 'paper' ? S0.serif(36) : S0.ui(400, 30), concept === 'paper' ? S0.text : S0.muted);
    fillText('种子 1939', x + 36, y + (concept === 'paper' ? 132 : 122), S0.small, S0.muted);
    floorPlan(x + w - 360, y + 36, 312, h - 72);
    if (concept === 'caret') {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x, y + h);
      ctx.lineTo(x + w, y + h);
      ctx.stroke();
    }
    return y + h;
  }

  function teams(y) {
    const rows = [
      ['D级人员', 6, concept === 'paper' ? '#D97757' : S0.lavender],
      ['基金会', 7, concept === 'paper' ? '#6A9BCC' : S0.sky],
      ['SCP', 2, concept === 'paper' ? '#788C5D' : S0.sakura],
      ['观众', 3, concept === 'paper' ? '#C4B99A' : S0.muted],
    ];
    const x = 56;
    const w = W - 112;
    const rowH = 76;
    const h = 108 + rows.length * rowH;
    if (concept !== 'caret' && concept !== 'paper') card(x, y, w, h);
    if (concept === 'paper') card(x, y, w, h);
    fillText(concept === 'paper' ? '这一局的人' : '阵营', x + 36, y + 28, concept === 'paper' ? S0.ui(500, 28) : S0.ui(500, 32), S0.text);
    fillText('18 人', x + w - 36 - textWidth('18 人', S0.small), y + 32, S0.small, S0.muted);
    rows.forEach((row, i) => {
      const ry = y + 92 + i * rowH;
      fillText(row[0], x + 36, ry, concept === 'paper' ? S0.serif(32) : S0.body, S0.text);
      const count = String(row[1]);
      fillText(count, x + w - 36 - textWidth(count, S0.small), ry + 4, S0.small, S0.muted);
      const bx = x + 36;
      const bw = w - 72;
      const by = ry + 46;
      ctx.fillStyle = S0.faint;
      roundRect(bx, by, bw, 8, 4);
      ctx.fill();
      ctx.fillStyle = row[2];
      roundRect(bx, by, Math.max(8, bw * (row[1] / 18)), 8, 4);
      ctx.fill();
    });
    if (concept === 'caret') {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x, y + h);
      ctx.lineTo(x + w, y + h);
      ctx.stroke();
    }
    return y + h;
  }

  function peopleStrip(y) {
    fillText('在线玩家', 56, y, concept === 'paper' ? S0.ui(500, 28) : S0.ui(500, 32), concept === 'paper' ? S0.muted : S0.text);
    y += 56;
    const rows = [
      ['雾', '雾灯', '科学家'],
      ['N', 'North', '设施警卫'],
    ];
    rows.forEach((p) => {
      dot(92, y + 24, 28, S0.faint);
      if (concept === 'paper') {
        ctx.strokeStyle = S0.line;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(92, y + 24, 28, 0, Math.PI * 2);
        ctx.stroke();
      }
      const initial = concept === 'paper' ? S0.serif(28) : S0.ui(500, 26);
      const nameFont = concept === 'paper' ? S0.serif(32) : S0.ui(500, 32);
      fillText(p[0], 92 - textWidth(p[0], initial) / 2, y + 8, initial, S0.text);
      fillText(p[1], 140, y + 6, nameFont, S0.text);
      fillText(p[2], 140 + textWidth(p[1], nameFont) + 18, y + 10, S0.small, S0.muted);
      y += 80;
    });
    return y;
  }

  function recent(y) {
    const items = [
      ['21:05', '雾灯 加入了服务器'],
      ['21:04', '回合开始'],
      ['21:02', '地图已生成'],
    ];
    const recentTitle = concept === 'paper' ? '刚刚' : '最近';
    fillText(recentTitle, 56, y, concept === 'paper' ? S0.ui(500, 28) : S0.ui(500, 32), concept === 'paper' ? S0.muted : S0.text);
    const sync = '5 秒前';
    fillText(sync, W - 56 - textWidth(sync, S0.small), y + 4, S0.small, S0.muted);
    y += 56;
    items.forEach((it) => {
      fillText(it[0], 56, y, S0.mono(26), S0.muted);
      fillText(it[1], 180, y - 2, concept === 'paper' ? S0.serif(32) : S0.body, S0.text);
      y += 64;
    });
    return y;
  }

  function pageOverview(alpha) {
    if (alpha <= 0.01) return;
    ctx.save();
    ctx.globalAlpha = alpha;
    let y = 150;
    y = titleBlock(y, '概览');
    y += concept === 'paper' ? 36 : 32;
    y = hero(y);
    y += 24;
    y = statPair(y);
    y += concept === 'paper' ? 8 : 24;
    y = mapCard(y);
    y += 28;
    y = teams(y);
    y += 36;
    y = peopleStrip(y);
    y += 28;
    recent(y);
    ctx.restore();
  }

  function pagePlayers(alpha) {
    if (alpha <= 0.01) return;
    ctx.save();
    ctx.globalAlpha = alpha;
    let y = 150;
    y = titleBlock(y, '玩家');
    y += 32;
    fillText('18 人在线', 56, y, S0.small, S0.muted);
    y += 56;
    const fieldW = W - 112;
    if (concept === 'caret') {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.strokeRect(56, y, fieldW, 96);
    } else {
      card(56, y, fieldW, 96, concept === 'paper' ? 16 : 48);
    }
    fillText('搜索昵称', 96, y + 30, S0.body, S0.muted);
    y += 128;
    const people = [
      ['雾', '雾灯', '科学家', '基金会'],
      ['N', 'North', '设施警卫', '基金会'],
      ['纸', '纸船', 'D级人员', 'D级'],
      ['满', '小满', '观众', '观众'],
    ];
    people.forEach((p, i) => {
      const ry = y + i * 132;
      dot(96, ry + 36, 36, concept === 'paper' ? S0.faint : (S0.dark ? '#222A34' : '#E7E0D6'));
      if (concept === 'paper') {
        ctx.strokeStyle = S0.line;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(96, ry + 36, 36, 0, Math.PI * 2);
        ctx.stroke();
      }
      const initialFont = concept === 'paper' ? S0.serif(34) : S0.ui(500, 32);
      const iw = textWidth(p[0], initialFont);
      fillText(p[0], 96 - iw / 2, ry + 18, initialFont, S0.text);
      fillText(p[1], 156, ry + 4, concept === 'paper' ? S0.serif(36) : S0.ui(500, 34), S0.text);
      fillText(p[2], 156, ry + 50, S0.small, S0.muted);
      const chip = p[3];
      const cw = textWidth(chip, S0.small) + 36;
      const cx = W - 56 - cw;
      roundRect(cx, ry + 16, cw, 48, 24);
      ctx.fillStyle = S0.faint;
      ctx.fill();
      fillText(chip, cx + 18, ry + 26, S0.small, S0.text);
    });
    ctx.restore();
  }

  function pageConsole(alpha) {
    if (alpha <= 0.01) return;
    ctx.save();
    ctx.globalAlpha = alpha;
    let y = 150;
    y = titleBlock(y, '控制台');
    y += 32;
    const x = 56;
    const w = W - 112;
    const h = 760;
    if (concept !== 'caret') card(x, y, w, h, S0.radius);
    else {
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.strokeRect(x, y, w, h);
    }
    const lines = [
      ['21:04:12', 'round started'],
      ['21:05:02', 'player joined'],
      ['21:06:18', 'map ready'],
      ['21:06:40', 'tps 60  players 18/40'],
    ];
    lines.forEach((ln, i) => {
      const ly = y + 40 + i * 64;
      fillText(ln[0], x + 36, ly, S0.mono(26), S0.muted);
      fillText(ln[1], x + 250, ly, S0.mono(26), S0.text);
    });
    const iy = y + h - 110;
    ctx.strokeStyle = S0.line;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(x + 28, iy);
    ctx.lineTo(x + w - 28, iy);
    ctx.stroke();
    const promptColor = concept === 'paper' ? S0.orange : (concept === 'dust' ? S0.sakura : S0.lavender);
    const on = ((window.__drawT || 0) % 1.05) < 0.72;
    fillText('>', x + 36, iy + 36, S0.mono(32), promptColor);
    if (concept === 'caret') {
      ctx.fillStyle = on ? S0.text : rgba(S0.text, 0.2);
      ctx.fillRect(x + 78, iy + 32, 16, 36);
    } else if (on) {
      ctx.fillStyle = S0.sakura;
      ctx.fillRect(x + 72, iy + 34, 3, 34);
    }
    ctx.restore();
  }

  function pageAlpha(which, st) {
    if (!st.moving) return which === st.to ? 1 : 0;
    if (which === st.from && which === st.to) return 1;
    if (which === st.from) return 1 - st.p;
    if (which === st.to) return st.p;
    return 0;
  }

  function drawPages(st) {
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 120, W, 1948);
    ctx.clip();
    pageOverview(pageAlpha(0, st));
    pagePlayers(pageAlpha(1, st));
    pageConsole(pageAlpha(2, st));
    ctx.restore();
  }

  /* ---------- icons ---------- */

  function sampleQuad(x0, y0, cx, cy, x1, y1, n) {
    const pts = [];
    for (let i = 0; i <= n; i++) {
      const t = i / n;
      const u = 1 - t;
      pts.push([u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1]);
    }
    return pts;
  }

  function sampleSeg(x1, y1, x2, y2, step) {
    const len = Math.hypot(x2 - x1, y2 - y1);
    const n = Math.max(1, Math.round(len / step));
    const pts = [];
    for (let i = 0; i <= n; i++) {
      const u = i / n;
      pts.push([lerp(x1, x2, u), lerp(y1, y2, u)]);
    }
    return pts;
  }

  const DUST_ICONS = {
    概览: {
      draw(c) {
        c.lineWidth = 1.7;
        c.lineCap = 'round';
        c.lineJoin = 'round';
        roundRectPath(c, 3.2, 3.2, 17.6, 17.6, 3.2);
        c.stroke();
        c.beginPath();
        c.moveTo(3.2, 9); c.lineTo(20.8, 9);
        c.moveTo(7, 13.2); c.lineTo(12.2, 13.2);
        c.moveTo(7, 16.6); c.lineTo(15.2, 16.6);
        c.stroke();
      },
      pts() {
        return [
          ...sampleSeg(3.2, 6, 20.8, 6, 3.2),
          ...sampleSeg(3.2, 3.2, 3.2, 20.8, 3.2),
          ...sampleSeg(20.8, 3.2, 20.8, 20.8, 3.2),
          ...sampleSeg(3.2, 20.8, 20.8, 20.8, 3.2),
          ...sampleSeg(3.2, 9, 20.8, 9, 3.2),
          ...sampleSeg(7, 13.2, 12.2, 13.2, 3),
          ...sampleSeg(7, 16.6, 15.2, 16.6, 3),
        ];
      },
      mote: [21.4, 3.4],
    },
    玩家: {
      draw(c) {
        c.lineWidth = 1.75;
        c.lineCap = 'round';
        c.lineJoin = 'round';
        c.beginPath();
        c.arc(15.4, 6.6, 2.15, 0, Math.PI * 2);
        c.stroke();
        c.beginPath();
        c.moveTo(12.2, 16.8);
        c.quadraticCurveTo(15.4, 10.4, 19.4, 16.8);
        c.stroke();
        c.beginPath();
        c.arc(8.2, 9.2, 2.7, 0, Math.PI * 2);
        c.stroke();
        c.beginPath();
        c.moveTo(3.4, 20.2);
        c.quadraticCurveTo(8.2, 12.6, 13.2, 20.2);
        c.stroke();
      },
      pts() {
        const pts = [];
        for (let i = 0; i < 8; i++) {
          const a = (i / 8) * Math.PI * 2;
          pts.push([15.4 + Math.cos(a) * 2.15, 6.6 + Math.sin(a) * 2.15]);
          pts.push([8.2 + Math.cos(a) * 2.7, 9.2 + Math.sin(a) * 2.7]);
        }
        pts.push(...sampleQuad(12.2, 16.8, 15.4, 10.4, 19.4, 16.8, 6));
        pts.push(...sampleQuad(3.4, 20.2, 8.2, 12.6, 13.2, 20.2, 7));
        return pts;
      },
      mote: [21.2, 3.6],
    },
    控制台: {
      draw(c) {
        c.lineWidth = 1.7;
        c.lineCap = 'round';
        c.lineJoin = 'round';
        c.beginPath();
        c.lineWidth = 1.9;
        c.moveTo(3.6, 5.4); c.lineTo(10.6, 11.6); c.lineTo(3.6, 17.8);
        c.moveTo(12.4, 17.8); c.lineTo(20.6, 17.8);
        c.stroke();
      },
      pts() {
        return [
          ...sampleSeg(3.6, 5.4, 10.6, 11.6, 2.6),
          ...sampleSeg(10.6, 11.6, 3.6, 17.8, 2.6),
          ...sampleSeg(12.4, 17.8, 20.6, 17.8, 2.6),
        ];
      },
      mote: [20.8, 13.4],
    },
    中心: {
      draw(c) {
        c.lineWidth = 1.7;
        c.lineCap = 'round';
        c.lineJoin = 'round';
        [[3.4, 3.4], [13.2, 3.4], [3.4, 13.2]].forEach(([x, y]) => {
          roundRectPath(c, x, y, 7.2, 7.2, 1.6);
          c.stroke();
        });
      },
      pts() {
        const pts = [];
        [[3.4, 3.4], [13.2, 3.4], [3.4, 13.2]].forEach(([x, y]) => {
          pts.push(...sampleSeg(x, y, x + 7.2, y, 2.4));
          pts.push(...sampleSeg(x + 7.2, y, x + 7.2, y + 7.2, 2.4));
          pts.push(...sampleSeg(x + 7.2, y + 7.2, x, y + 7.2, 2.4));
          pts.push(...sampleSeg(x, y + 7.2, x, y, 2.4));
        });
        return pts;
      },
      mote: [16.8, 16.8],
    },
  };

  function roundRectPath(c, x, y, w, h, r) {
    const rr = Math.min(r, w / 2, h / 2);
    c.beginPath();
    c.moveTo(x + rr, y);
    c.arcTo(x + w, y, x + w, y + h, rr);
    c.arcTo(x + w, y + h, x, y + h, rr);
    c.arcTo(x, y + h, x, y, rr);
    c.arcTo(x, y, x + w, y, rr);
    c.closePath();
  }

  const MATRIX = {
    概览: [
      '1111111',
      '1000001',
      '1011001',
      '1000001',
      '1010101',
      '1000001',
      '1111111',
    ],
    玩家: [
      '0110110',
      '0110110',
      '0000000',
      '1110111',
      '1010101',
      '0100010',
      '0000000',
    ],
    控制台: [
      '1100000',
      '0110000',
      '0011000',
      '0110000',
      '1100000',
      '0000000',
      '1111110',
    ],
    中心: [
      '0110011',
      '0110011',
      '0000000',
      '0000000',
      '0110011',
      '0110011',
      '0000000',
    ],
    操作: [
      '0000000',
      '0001000',
      '0001000',
      '0111110',
      '0001000',
      '0001000',
      '0000000',
    ],
  };

  function matrixDots(key) {
    const rows = MATRIX[key];
    const pts = [];
    rows.forEach((row, y) => {
      for (let x = 0; x < row.length; x++) {
        if (row[x] === '1') pts.push([x, y]);
      }
    });
    return pts;
  }

  function drawMatrix(key, cx, cy, cell, r, color, scatter, alpha) {
    const pts = matrixDots(key);
    ctx.save();
    ctx.globalAlpha *= alpha;
    pts.forEach(([x, y], i) => {
      const hx = (hash(i + 3) - 0.5) * scatter;
      const hy = (hash(i + 19) - 0.5) * scatter;
      const px = cx + (x - 3) * cell + hx;
      const py = cy + (y - 3) * cell + hy;
      dot(px, py, r, color);
    });
    ctx.restore();
  }

  function caretIcon(name, cx, cy, scale, color, cursorOn) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(scale, scale);
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = 1.7;
    ctx.lineCap = 'square';
    ctx.lineJoin = 'miter';
    ctx.beginPath();
    if (name === '概览') {
      ctx.moveTo(2, 3); ctx.lineTo(18, 3); ctx.lineTo(18, 12);
      ctx.moveTo(2, 3); ctx.lineTo(2, 20); ctx.lineTo(13, 20);
    } else if (name === '玩家') {
      ctx.strokeRect(2, 2, 7, 7);
      ctx.strokeRect(13, 4, 6, 6);
      ctx.beginPath();
      ctx.moveTo(5.5, 11); ctx.lineTo(5.5, 20);
      ctx.moveTo(16, 11); ctx.lineTo(16, 19);
    } else if (name === '控制台') {
      ctx.moveTo(3, 5); ctx.lineTo(10, 12); ctx.lineTo(3, 19);
      ctx.moveTo(12, 19); ctx.lineTo(20, 19);
    } else if (name === '中心') {
      ctx.strokeRect(2, 2, 8, 8);
      ctx.strokeRect(14, 2, 8, 8);
      ctx.strokeRect(2, 14, 8, 8);
    } else if (name === '操作') {
      ctx.strokeRect(3, 3, 18, 18);
    }
    ctx.stroke();
    const cursor = {
      概览: [15, 15],
      玩家: [19, 14],
      控制台: [18, 6],
      中心: [15, 15],
      操作: [9, 9],
    }[name];
    ctx.globalAlpha *= cursorOn ? 1 : 0.28;
    ctx.fillRect(cursor[0], cursor[1], 5, 5);
    ctx.restore();
  }

  function paperIcon(name, cx, cy, color) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = 1.6;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    if (name === '概览') {
      ctx.strokeRect(-11, -11, 10, 10);
      ctx.strokeRect(1, -11, 10, 10);
      ctx.strokeRect(-11, 1, 10, 10);
      ctx.fillRect(1, 1, 10, 10);
    } else if (name === '玩家') {
      ctx.arc(0, -6, 4.2, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, 14, 8, Math.PI * 1.22, Math.PI * 1.78);
      ctx.stroke();
    } else if (name === '控制台') {
      ctx.moveTo(-11, -7); ctx.lineTo(-2, 1); ctx.lineTo(-11, 9);
      ctx.moveTo(0, 9); ctx.lineTo(12, 9);
      ctx.stroke();
    } else if (name === '中心') {
      ctx.arc(0, 0, 10, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, 0, 3.2, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  /* ---------- navs ---------- */

  function dustGeom() {
    const slots = [0, 1, 2, 3].map((i) => 36 + i * 180 + 90);
    return { slots, iconCy: 2224, labelY: 2278, line: 2156, action: { x: 972, y: 2216, r: 34 } };
  }

  function drawDustNav(st) {
    const g = dustGeom();
    ctx.strokeStyle = S0.line;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(0, g.line);
    ctx.lineTo(W, g.line);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(888, g.line + 28);
    ctx.lineTo(888, 2310);
    ctx.strokeStyle = S0.faint;
    ctx.stroke();

    TABS.forEach((name, i) => {
      const selected = !st.moving && st.to === i;
      const color = selected ? S0.text : S0.muted;
      const showSolid = !st.moving || (i !== st.from && i !== st.to);
      if (showSolid) {
        drawDustGlyph(name, g.slots[i], g.iconCy, 48, color, selected ? S0.lavender : S0.muted);
      }
      fillText(name, g.slots[i] - textWidth(name, S0.label) / 2, g.labelY, S0.label, color);
    });
    if (st.moving) drawDustAssemble(st, g);

    const a = g.action;
    ctx.save();
    ctx.translate(a.x, a.y);
    ctx.scale(1 - 0.06 * st.press, 1 - 0.06 * st.press);
    ctx.translate(-a.x, -a.y);
    ctx.beginPath();
    ctx.arc(a.x, a.y, a.r, 0, Math.PI * 2);
    ctx.strokeStyle = st.menu > 0.4 ? S0.sakura : S0.text;
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.strokeStyle = S0.text;
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(a.x, a.y - 12); ctx.lineTo(a.x, a.y + 12);
    ctx.moveTo(a.x - 12, a.y); ctx.lineTo(a.x + 12, a.y);
    ctx.stroke();
    dot(a.x + 22, a.y - 22, 3.2, S0.sakura);
    ctx.restore();
    fillText('操作', a.x - textWidth('操作', S0.label) / 2, g.labelY, S0.label, st.menu > 0.2 ? S0.text : S0.muted);
    homeIndicator(2372);
    return g;
  }

  function drawDustGlyph(name, cx, cy, size, color, moteColor) {
    const icon = DUST_ICONS[name];
    ctx.save();
    ctx.translate(cx - size / 2, cy - size / 2);
    ctx.scale(size / 24, size / 24);
    ctx.strokeStyle = color;
    icon.draw(ctx);
    ctx.beginPath();
    ctx.arc(icon.mote[0], icon.mote[1], 1.8, 0, Math.PI * 2);
    ctx.fillStyle = moteColor;
    ctx.fill();
    ctx.restore();
  }

  function drawDustAssemble(st, g) {
    [st.from, st.to].forEach((idx) => {
      const incoming = idx === st.to;
      const p = incoming ? st.p : 1 - st.p;
      const icon = DUST_ICONS[TABS[idx]];
      const pts = icon.pts();
      const size = 48;
      const originX = g.slots[idx] - size / 2;
      const originY = g.iconCy - size / 2;
      const sc = size / 24;
      pts.forEach((pt, i) => {
        const scatter = (1 - easeOut(incoming ? st.p : 1 - st.p)) * 22;
        const ox = (hash(i + idx * 17) - 0.5) * scatter;
        const oy = (hash(i + 40 + idx) - 0.5) * scatter;
        const alpha = incoming ? smooth(st.p) : 1 - smooth(st.p);
        dot(originX + pt[0] * sc + ox, originY + pt[1] * sc + oy, 2.15, rgba(S0.text, alpha));
      });
      const moteA = incoming ? smooth(st.p) : 1 - smooth(st.p);
      dot(originX + icon.mote[0] * sc, originY + icon.mote[1] * sc, 3, rgba(S0.lavender, moteA));
      void p;
    });
  }

  function dustParticles(st, g) {
    const colors = [S0.sakura, S0.lavender, S0.sky];
    for (let i = 0; i < 8; i++) {
      const speed = 22 + (i % 3) * 10;
      const x = (hash(i) * W + st.t * speed) % (W + 40) - 20;
      const y = g.line - 18 - (i % 4) * 16 + Math.sin(st.t * 0.8 + i) * 7;
      dot(x, y, 1.7 + (i % 3) * 0.45, rgba(colors[i % 3], S0.dark ? 0.55 : 0.7));
    }
    if (!st.moving) {
      const cx = g.slots[st.to];
      for (let i = 0; i < 3; i++) {
        const ox = (i - 1) * 12 + Math.sin(st.t * 0.8 + i) * 2;
        dot(cx + ox, g.labelY + 40, 2.6, rgba(colors[i], 0.9));
      }
    }
    const age = st.t - 4.22;
    if (age > 0 && age < 0.7) {
      for (let i = 0; i < 12; i++) {
        const ang = -Math.PI / 2 + (hash(i + 4) - 0.5) * 1.7;
        const v = 70 + hash(i + 9) * 90;
        const x = g.action.x + Math.cos(ang) * v * age;
        const y = g.action.y + Math.sin(ang) * v * age + 140 * age * age;
        dot(x, y, 2.4, rgba(i % 2 ? S0.sakura : S0.lavender, 1 - age / 0.7));
      }
    }
  }

  function matrixGeom() {
    const slab = { x: 48, y: 2096, w: 748, h: 196, r: 32 };
    const key = { x: 816, y: 2096, w: 196, h: 196, r: 32 };
    const slots = [0, 1, 2, 3].map((i) => slab.x + (i + 0.5) * (slab.w / 4));
    return { slab, key, slots, iconCy: slab.y + 78, labelY: slab.y + 132 };
  }

  function drawMatrixNav(st) {
    const g = matrixGeom();
    roundRect(g.slab.x, g.slab.y, g.slab.w, g.slab.h, g.slab.r);
    ctx.fillStyle = S0.card;
    ctx.fill();
    ctx.strokeStyle = S0.line;
    ctx.lineWidth = 1.5;
    ctx.stroke();

    ctx.save();
    ctx.translate(g.key.x + g.key.w / 2, g.key.y + g.key.h / 2);
    const s = 1 - 0.035 * st.press;
    ctx.scale(s, s);
    ctx.translate(-(g.key.x + g.key.w / 2), -(g.key.y + g.key.h / 2));
    roundRect(g.key.x, g.key.y + st.press * 2, g.key.w, g.key.h, g.key.r);
    ctx.fillStyle = S0.dark ? '#1C232C' : '#F7F4EE';
    ctx.fill();
    ctx.strokeStyle = st.menu > 0.4 ? S0.sakura : S0.line;
    ctx.stroke();
    const scatter = st.moving ? 0 : 0;
    drawMatrix('操作', g.key.x + g.key.w / 2, g.key.y + 78, 11, 3.3, S0.text, scatter, 1);
    ctx.restore();
    fillText('操作', g.key.x + g.key.w / 2 - textWidth('操作', S0.label) / 2, g.labelY, S0.label, S0.text);

    TABS.forEach((name, i) => {
      const selected = !st.moving && st.to === i;
      const showSolid = !st.moving || (i !== st.from && i !== st.to);
      if (showSolid) {
        const breathe = !selected;
        drawMatrix(name, g.slots[i], g.iconCy, 9.5, selected ? 3.5 : 2.5, selected ? S0.text : S0.muted, 0, 1);
        if (breathe) {
          const pts = matrixDots(name);
          const k = 0;
          const pulse = 0.35 + 0.4 * (0.5 + 0.5 * Math.sin(st.t * 1.2 + i));
          const [x, y] = pts[k];
          dot(g.slots[i] + (x - 3) * 9.5, g.iconCy + (y - 3) * 9.5, 2.4, rgba(S0.muted, pulse));
        }
      }
      const col = (selected || (st.moving && (i === st.from || i === st.to))) ? S0.text : S0.muted;
      fillText(name, g.slots[i] - textWidth(name, S0.label) / 2, g.labelY, S0.label, i === st.to && !st.moving ? S0.text : col);
    });
    if (st.moving) {
      [st.from, st.to].forEach((idx) => {
        const incoming = idx === st.to;
        const sc = incoming ? (1 - easeOut(st.p)) * 18 : easeOut(1 - st.p) * 18;
        const alpha = incoming ? smooth(st.p) : 1 - smooth(st.p);
        drawMatrix(TABS[idx], g.slots[idx], g.iconCy, 9.5, 3.1, S0.text, sc, alpha);
      });
    }
    homeIndicator(2372);
    return g;
  }

  function matrixParticles(st, g) {
    if (!st.moving) {
      const cx = g.slots[st.to];
      const y = g.labelY + 42;
      dot(cx - 8 + Math.sin(st.t * 0.7) * 1.5, y, 3.2, S0.sakura);
      dot(cx + 8, y + Math.sin(st.t * 0.9) * 1.5, 3.2, S0.lavender);
    }
    const age = st.t - 4.22;
    if (age > 0 && age < 0.65) {
      const cx = g.key.x + g.key.w / 2;
      const cy = g.key.y + 78;
      for (let i = 0; i < 8; i++) {
        const ang = (i / 8) * Math.PI * 2;
        const dist = age * 70;
        dot(cx + Math.cos(ang) * dist, cy + Math.sin(ang) * dist * 0.65, 3, rgba(S0.sakura, 1 - age / 0.65));
      }
    }
  }

  function caretGeom() {
    const slots = [0, 1, 2, 3].map((i) => 36 + i * 196 + 98);
    return { slots, iconCy: 2214, labelY: 2276, action: { x: 968, y: 2204, s: 68 } };
  }

  function drawCaretNav(st) {
    const g = caretGeom();
    const cursorOn = (st.t % 1.05) < 0.72;
    TABS.forEach((name, i) => {
      const selected = st.to === i && !st.moving;
      const hot = st.moving && (i === st.from || i === st.to);
      const color = (selected || hot) ? S0.text : S0.muted;
      if (!st.moving || (i !== st.from && i !== st.to)) {
        caretIcon(name, g.slots[i], g.iconCy, 1.7, color, selected ? cursorOn : false);
      }
      fillText(name, g.slots[i] - textWidth(name, S0.label) / 2, g.labelY, S0.label, selected ? S0.text : S0.muted);
    });
    if (st.moving) {
      [st.from, st.to].forEach((idx) => {
        const alpha = idx === st.to ? smooth(st.p) : 1 - smooth(st.p);
        ctx.save();
        ctx.globalAlpha *= alpha;
        caretIcon(TABS[idx], g.slots[idx], g.iconCy, 1.7, S0.text, idx === st.to);
        ctx.restore();
      });
    }
    const x = st.moving ? lerp(g.slots[st.from], g.slots[st.to], st.p) : g.slots[st.to];
    const y = g.labelY + 40;
    ctx.fillStyle = S0.lavender;
    ctx.fillRect(x - 16, y, 32, 8);

    const a = g.action;
    ctx.save();
    ctx.translate(a.x, a.y);
    ctx.scale(1 - 0.05 * st.press, 1 - 0.05 * st.press);
    ctx.translate(-a.x, -a.y);
    ctx.strokeStyle = st.menu > 0.4 ? S0.sakura : S0.text;
    ctx.lineWidth = 2;
    ctx.strokeRect(a.x - a.s / 2, a.y - a.s / 2, a.s, a.s);
    caretIcon('操作', a.x, a.y, 1.35, S0.text, cursorOn);
    ctx.restore();
    fillText('操作', a.x - textWidth('操作', S0.label) / 2, g.labelY, S0.label, st.menu > 0.2 ? S0.text : S0.muted);
    homeIndicator(2372);
    return g;
  }

  function caretParticles(st, g) {
    const x = st.moving ? lerp(g.slots[st.from], g.slots[st.to], st.p) : g.slots[st.to];
    const y = g.labelY + 44;
    if (st.moving) {
      for (let k = 1; k <= 5; k++) {
        const tp = clamp(st.p - k * 0.07, 0, 1);
        const px = lerp(g.slots[st.from], g.slots[st.to], tp);
        ctx.fillStyle = rgba(S0.lavender, 0.45 * (1 - k / 6));
        ctx.fillRect(px - 8, y - 2, 14, 8);
      }
    } else {
      for (let i = 0; i < 2; i++) {
        const ox = Math.sin(st.t * 0.7 + i * 2) * 10;
        const oy = -8 - i * 10 + Math.cos(st.t * 0.5 + i) * 3;
        ctx.save();
        ctx.globalAlpha = 0.8;
        ctx.fillStyle = i ? S0.sky : S0.lavender;
        ctx.fillRect(x + 24 + ox, y - 2 + oy * 0.3, 7, 7);
        ctx.restore();
      }
    }
    const age = st.t - 4.22;
    if (age > 0 && age < 0.6) {
      for (let i = 0; i < 7; i++) {
        const px = g.action.x - 30 + hash(i) * 60;
        const py = g.action.y + 20 + 30 * age + 220 * age * age + i * 2;
        ctx.fillStyle = rgba(i % 2 ? S0.sakura : S0.lavender, 1 - age / 0.6);
        ctx.fillRect(px, py, 6, 6);
      }
    }
  }

  function paperGeom() {
    const slots = [0, 1, 2, 3].map((i) => 24 + i * 200 + 100);
    return { slots, iconCy: 2246, labelY: 2284, line: 2168, actionX: 980 };
  }

  function drawPaperNav(st) {
    const g = paperGeom();
    ctx.fillStyle = S0.card;
    ctx.fillRect(0, g.line, W, H - g.line);
    ctx.strokeStyle = S0.line;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(0, g.line);
    ctx.lineTo(W, g.line);
    ctx.stroke();
    const xSel = st.moving ? lerp(g.slots[st.from], g.slots[st.to], st.p) : g.slots[st.to];
    TABS.forEach((name, i) => {
      const selected = i === st.to && !st.moving;
      const color = (selected || (st.moving && i === st.to)) ? S0.text : S0.muted;
      paperIcon(name, g.slots[i], g.iconCy, color);
      const font = S0.ui(selected ? 500 : 400, 28);
      fillText(name, g.slots[i] - textWidth(name, font) / 2, g.labelY, font, color);
    });
    ctx.fillStyle = S0.orange;
    roundRect(xSel - 16, g.labelY + 40, 32, 4, 2);
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(860, g.line + 28);
    ctx.lineTo(860, 2320);
    ctx.strokeStyle = S0.line;
    ctx.stroke();
    const aFont = S0.ui(500, 30);
    ctx.save();
    ctx.translate(g.actionX, 2258);
    ctx.scale(1 - 0.04 * st.press, 1 - 0.04 * st.press);
    ctx.translate(-g.actionX, -2258);
    fillText('操作', g.actionX - textWidth('操作', aFont) / 2, g.labelY, aFont, S0.orange);
    ctx.restore();
    homeIndicator(2372);
    return g;
  }

  function paperParticles(st, g) {
    for (let i = 0; i < 7; i++) {
      const x = hash(i + 2) * W;
      const base = g.line - 200 + hash(i + 8) * 180;
      const y = base + Math.sin(st.t * 0.35 + i) * 10 - (st.t * 8 + i * 20) % 30;
      dot(x, y, 1.5, rgba(S0.dark ? '#9B9890' : '#B0AEA5', 0.45));
    }
    if (st.moving) {
      const x1 = g.slots[st.from];
      const x2 = g.slots[st.to];
      for (let i = 0; i < 4; i++) {
        const u = clamp(st.p - i * 0.05, 0, 1);
        dot(lerp(x1, x2, u), g.labelY + 42 - i * 6, 1.8, rgba(S0.orange, 0.35 * (1 - i / 5)));
      }
    }
  }

  function drawMenu(st, g) {
    if (st.menu <= 0.01) return;
    ctx.save();
    ctx.globalAlpha = st.menu;
    if (concept === 'dust') {
      const y = 1988;
      const h = 112;
      const gap = 16;
      const w = (W - 112 - gap * 2) / 3;
      ACTIONS.forEach((a, i) => {
        const x = 56 + i * (w + gap);
        roundRect(x, y, w, h, 28);
        ctx.fillStyle = S0.card;
        ctx.fill();
        ctx.lineWidth = 1.5;
        ctx.strokeStyle = a.danger ? rgba(S0.danger, 0.7) : S0.line;
        ctx.stroke();
        const font = S0.ui(500, 30);
        fillText(a.label, x + w / 2 - textWidth(a.label, font) / 2, y + 38, font, a.danger ? S0.danger : S0.text);
      });
    } else if (concept === 'matrix') {
      const y = 1928;
      const h = 132;
      const w = 760;
      const x = 48;
      roundRect(x, y, w, h, 24);
      ctx.fillStyle = S0.card;
      ctx.fill();
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = S0.line;
      ctx.stroke();
      ACTIONS.forEach((a, i) => {
        const cx = x + (i + 0.5) * (w / 3);
        const font = S0.ui(500, 30);
        fillText(a.label, cx - textWidth(a.label, font) / 2, y + 48, font, a.danger ? S0.danger : S0.text);
      });
    } else if (concept === 'caret') {
      const w = 520;
      const x = W - 56 - w;
      const y = 1860;
      const rowH = 84;
      const h = 24 + ACTIONS.length * rowH;
      ctx.strokeStyle = S0.line;
      ctx.lineWidth = 1.5;
      ctx.strokeRect(x, y, w, h);
      ctx.fillStyle = rgba(S0.bg, 0.94);
      ctx.fillRect(x, y, w, h);
      ACTIONS.forEach((a, i) => {
        const ly = y + 20 + i * rowH;
        fillText('>', x + 28, ly + 16, S0.mono(30), a.danger ? S0.danger : S0.lavender);
        fillText(a.label, x + 72, ly + 14, S0.ui(400, 32), a.danger ? S0.danger : S0.text);
      });
    } else {
      const w = 560;
      const x = W - 48 - w;
      const y = 1848;
      const rowH = 92;
      const h = ACTIONS.length * rowH;
      roundRect(x, y, w, h, 18);
      ctx.fillStyle = S0.card;
      ctx.fill();
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = S0.line;
      ctx.stroke();
      ACTIONS.forEach((a, i) => {
        const ly = y + i * rowH;
        if (i > 0) {
          ctx.beginPath();
          ctx.moveTo(x + 28, ly);
          ctx.lineTo(x + w - 28, ly);
          ctx.strokeStyle = S0.faint;
          ctx.stroke();
        }
        const font = a.danger ? S0.ui(500, 32) : S0.serif(36);
        fillText(a.label, x + 36, ly + 26, font, a.danger ? S0.danger : S0.text);
      });
    }
    ctx.restore();
  }

  function background() {
    ctx.fillStyle = S0.bg;
    ctx.fillRect(0, 0, W, H);
    if (concept === 'paper') {
      const grd = ctx.createRadialGradient(220, 180, 40, 220, 260, 780);
      grd.addColorStop(0, rgba('#D97757', S0.dark ? 0.10 : 0.07));
      grd.addColorStop(1, rgba('#D97757', 0));
      ctx.fillStyle = grd;
      ctx.fillRect(0, 0, W, H);
    }
  }

  function drawAt(t) {
    window.__drawT = t;
    const st = timeline(t);
    background();
    statusBar();
    drawPages(st);
    let g;
    if (concept === 'dust') g = drawDustNav(st);
    else if (concept === 'matrix') g = drawMatrixNav(st);
    else if (concept === 'caret') g = drawCaretNav(st);
    else g = drawPaperNav(st);
    if (concept === 'dust') dustParticles(st, g);
    else if (concept === 'matrix') matrixParticles(st, g);
    else if (concept === 'caret') caretParticles(st, g);
    else paperParticles(st, g);
    drawMenu(st, g);
    /* status stays readable */
    statusBar();
  }

  let playStart = null;
  function loop(now) {
    if (window.__hold != null) {
      drawAt(window.__hold);
      return;
    }
    if (playStart == null) playStart = now;
    const t = Math.min(5.25, (now - playStart) / 1000);
    drawAt(t);
    if (t < 5.25) requestAnimationFrame(loop);
  }

  window.seek = function (t) {
    window.__hold = t;
    drawAt(t);
  };

  async function boot() {
    const jobs = [
      ['400 40px "Noto Sans SC"'],
      ['500 40px "Noto Sans SC"'],
      ['400 40px "LXGW WenKai"'],
      ['400 40px "JetBrains Mono"'],
      ['400 40px Lora'],
      ['500 40px Lora'],
      ['400 40px Poppins'],
      ['500 40px Poppins'],
    ];
    await Promise.all(jobs.map((f) => document.fonts.load(f[0]).catch(() => null)));
    await document.fonts.ready;
    window.__fontsReady = true;
    if (q.get('t') != null) window.seek(parseFloat(q.get('t')));
    else requestAnimationFrame(loop);
  }
  boot();
})();
