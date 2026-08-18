(function () {
  // pager-v5：按「可视高度÷行高」整行分页；无 lookbehind
  var ENGINE = 'pager-v5';
  var clip = document.getElementById('page-clip');
  var content = document.getElementById('content');
  var hint = document.getElementById('chrome-hint');
  var book = null;
  var chapterIndex = 0;
  var pageMode = 'scroll';
  var pageIndex = 0;
  var pageCount = 1;
  var pageHeight = 0;
  var linesPerPage = 1;
  var lineHeightPx = 28;
  var padH = 18;
  var padV = 10;
  var padL = 18;
  var padR = 18;
  var padT = 10;
  var padB = 10;
  var lineMult = 1.85;
  var ttsHlMode = 'color';
  var ttsHlColor = '#1565c0';
  var viewportH = 0;
  var viewportW = 0;
  var safeTop = 0;
  var safeBottom = 28;
  var ttsUnits = [];
  var showParaComments = true;
  var showChapterComments = true;
  var speakingFlag = false;
  var pausedFlag = false;

  window.__gnSpeaking = false;
  window.__gnPaused = false;
  window.__gnPager = ENGINE;

  function showHint(msg) {
    if (!hint) return;
    hint.textContent = msg || '\u8f7b\u89e6\u4e2d\u592e\u663e\u793a\u83dc\u5355';
    hint.classList.add('show');
    setTimeout(function () { hint.classList.remove('show'); }, 1600);
  }

  function applyTheme(theme, fontSize) {
    document.body.classList.remove('theme-night', 'theme-paper', 'theme-green');
    document.body.classList.add('theme-' + (theme || 'paper'));
    document.documentElement.style.setProperty('--font-size', (fontSize || 18) + 'px');
    document.documentElement.style.setProperty('--line-height', String(lineMult));
    applyLayoutVars();
    applyTtsHighlightStyle();
  }

  function applyLayoutVars() {
    document.documentElement.style.setProperty('--line-height', String(lineMult));
    document.documentElement.style.setProperty('--pad', padH + 'px');
    document.documentElement.style.setProperty('--pad-v', padV + 'px');
    document.documentElement.style.setProperty('--pad-l', padL + 'px');
    document.documentElement.style.setProperty('--pad-r', padR + 'px');
    document.documentElement.style.setProperty('--pad-t', padT + 'px');
    document.documentElement.style.setProperty('--pad-b', padB + 'px');
    if (content) {
      content.style.paddingLeft = padL + 'px';
      content.style.paddingRight = padR + 'px';
      content.style.paddingTop = padT + 'px';
      content.style.paddingBottom = padB + 'px';
    }
  }

  function hexToRgba(hex, alpha) {
    var h = String(hex || '').replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    if (h.length !== 6) return 'rgba(255, 235, 59, ' + alpha + ')';
    var r = parseInt(h.slice(0, 2), 16);
    var g = parseInt(h.slice(2, 4), 16);
    var b = parseInt(h.slice(4, 6), 16);
    if (isNaN(r) || isNaN(g) || isNaN(b)) return 'rgba(255, 235, 59, ' + alpha + ')';
    return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
  }

  function applyTtsHighlightStyle() {
    var mode = ttsHlMode === 'marker' ? 'marker' : 'color';
    document.body.classList.remove('tts-hl-color', 'tts-hl-marker');
    document.body.classList.add('tts-hl-' + mode);
    document.documentElement.style.setProperty('--tts-mode', mode);
    document.documentElement.style.setProperty('--tts-hl', ttsHlColor || '#1565c0');
    document.documentElement.style.setProperty('--tts-hl-bg', hexToRgba(ttsHlColor || '#FFEB3B', 0.55));
  }

  function esc(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function withInlineBadge(html, pid, count) {
    var badge =
      '<a class="pc-badge" href="javascript:void(0)" data-pid="' + esc(pid) + '" title="\u6bb5\u8bc4">' +
      count + '</a>';
    if (!html) return '<p id="' + esc(pid) + '">' + badge + '</p>';
    if (/<\/p>\s*$/i.test(html)) return html.replace(/<\/p>\s*$/i, badge + '</p>');
    return html + badge;
  }

  function notifyPage() {
    if (window.GnReaderBridge && window.GnReaderBridge.onPageChanged) {
      window.GnReaderBridge.onPageChanged(
        pageIndex, pageCount, chapterIndex, book ? book.chapters.length : 0
      );
    }
  }

  function measureLineHeight() {
    var probe = document.createElement('div');
    probe.style.cssText = 'position:absolute;visibility:hidden;padding:0;margin:0;left:0;top:0;line-height:' + lineMult + ';';
    probe.style.fontSize = window.getComputedStyle(document.body).fontSize;
    probe.textContent = '\u6d4b\u9ad8Hg\u4e00\u884c';
    document.body.appendChild(probe);
    var cs = window.getComputedStyle(probe);
    var lh = parseFloat(cs.lineHeight);
    if (!lh || isNaN(lh)) {
      var fs = parseFloat(cs.fontSize) || 18;
      lh = fs * lineMult;
    }
    document.body.removeChild(probe);
    lineHeightPx = lh;
    return lh;
  }

  /**
   * \u53ef\u89c6\u9ad8 = viewportH\uff08\u539f\u751f\u4f20\u5165\u7684 WebView \u9ad8\uff0c\u5df2\u6263\u9664\u901a\u77e5\u680f/\u4fe1\u606f\u680f\uff09
   * \u5185\u5bb9\u533a = \u53ef\u89c6\u9ad8 - \u4e0a\u4e0b\u8fb9\u8ddd
   * \u884c\u6570 = floor(\u5185\u5bb9\u533a / \u884c\u9ad8)
   * \u9875\u9ad8 = \u884c\u6570 * \u884c\u9ad8  (\u6574\u884c)
   */
  function computePageMetrics() {
    var lh = measureLineHeight();
    var vh = viewportH > 40 ? viewportH : (window.innerHeight || (clip && clip.clientHeight) || 600);
    var inner = Math.max(lh * 3, vh - padT - padB);
    linesPerPage = Math.max(3, Math.floor(inner / lh));
    pageHeight = linesPerPage * lh;
    return { lh: lh, lines: linesPerPage, pageHeight: pageHeight, vh: vh };
  }

  function applyPageTransform() {
    if (pageMode !== 'page') return;
    var y = -pageIndex * pageHeight;
    content.style.webkitTransform = 'translateY(' + y + 'px)';
    content.style.transform = 'translateY(' + y + 'px)';
  }

  function recalcPageCount() {
    var total = Math.max(content.scrollHeight, content.offsetHeight, 1);
    pageCount = Math.max(1, Math.ceil(total / pageHeight));
    if (pageIndex > pageCount - 1) pageIndex = pageCount - 1;
    if (pageIndex < 0) pageIndex = 0;
  }

  function clearPageStyles() {
    content.style.webkitTransform = '';
    content.style.transform = '';
    if (clip) {
      clip.style.height = '';
      clip.style.overflow = '';
    }
  }

  function applyPageMode(mode) {
    pageMode = mode === 'page' ? 'page' : 'scroll';
    document.body.classList.toggle('mode-page', pageMode === 'page');
    clearPageStyles();
    applyLayoutVars();
    window.scrollTo(0, 0);

    if (pageMode === 'page') {
      var m = computePageMetrics();
      if (clip) {
        clip.style.height = pageHeight + 'px';
        clip.style.overflow = 'hidden';
      }
      setTimeout(function () {
        computePageMetrics();
        if (clip) clip.style.height = pageHeight + 'px';
        recalcPageCount();
        applyPageTransform();
        notifyPage();
        showHint('\u5206\u9875 \u00b7 ' + ENGINE + ' \u00b7 ' + linesPerPage + '\u884c/\u9875 \u00b7 ' + pageCount + '\u9875');
      }, 40);
    } else {
      pageIndex = 0;
      pageCount = 1;
      notifyPage();
      showHint('\u6eda\u52a8\u6a21\u5f0f \u00b7 ' + ENGINE);
    }
  }

  function bindChromeHandlers() {
    var badges = content.querySelectorAll('.pc-badge');
    for (var i = 0; i < badges.length; i++) {
      (function (badge) {
        badge.addEventListener('click', function (ev) {
          ev.preventDefault();
          ev.stopPropagation();
          var pid = badge.getAttribute('data-pid');
          if (window.GnReaderBridge && window.GnReaderBridge.openParagraphComments) {
            window.GnReaderBridge.openParagraphComments(chapterIndex, pid);
          }
        });
      })(badges[i]);
    }
    var btn = document.getElementById('btn-chapter-comments');
    if (btn) {
      btn.addEventListener('click', function (ev) {
        ev.stopPropagation();
        if (window.GnReaderBridge && window.GnReaderBridge.openChapterComments) {
          window.GnReaderBridge.openChapterComments(chapterIndex);
        }
      });
    }
    var pn = document.getElementById('btn-prev-chapter');
    if (pn) pn.addEventListener('click', function (ev) { ev.stopPropagation(); window.GnReader.prevChapter(); });
    var nn = document.getElementById('btn-next-chapter');
    if (nn) nn.addEventListener('click', function (ev) { ev.stopPropagation(); window.GnReader.nextChapter(); });
  }

  function fixMedia() {
    if (!book || !book.mediaBase) return;
    var imgs = content.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var src = imgs[i].getAttribute('src') || '';
      if (src.indexOf('../media/') === 0 || src.indexOf('media/') === 0) {
        imgs[i].src = book.mediaBase + src.split('/').pop();
      }
    }
  }

  function buildChapterHtml() {
    if (!book || !book.chapters || !book.chapters.length) return '<p class="meta">\u65e0\u5185\u5bb9</p>';
    var ch = book.chapters[chapterIndex];
    var parts = [];
    parts.push('<h1 class="title">' + esc(ch.title || book.title) + '</h1>');
    if (book.author) parts.push('<div class="meta">' + esc(book.author) + '</div>');
    var paras = ch.paragraphs || [];
    var visibleCount = 0;
    for (var i = 0; i < paras.length; i++) {
      var p = paras[i];
      parts.push('<div class="para-block" data-pid="' + esc(p.id) + '">');
      var html = p.html || ('<p id="' + esc(p.id) + '">' + esc(p.text) + '</p>');
      html = html.replace(/style\s*=\s*("[^"]*"|\'[^\']*\')/gi, function (m) {
        return m
          .replace(/column-[^;:\'"]+;?/gi, '')
          .replace(/text-align\s*:\s*justify;?/gi, 'text-align:left;')
          .replace(/float\s*:\s*[^;:\'"]+;?/gi, '');
      });
      var count = p.commentCount || 0;
      if (showParaComments && count > 0) html = withInlineBadge(html, p.id, count);
      parts.push(html);
      parts.push('</div>');
      if ((p.text && String(p.text).trim()) || p.hasImage) visibleCount++;
    }
    if (!paras.length) {
      parts.push('<p class="meta">\u672c\u7ae0\u65e0\u6bb5\u843d\u6570\u636e\uff08\u53ef\u80fd\u6b63\u6587\u672a\u6293\u53d6\u6210\u529f\uff09</p>');
    } else if (!visibleCount) {
      parts.push('<p class="meta">\u6b63\u6587\u4f3c\u4e4e\u4e3a\u7a7a\uff08\u4ec5\u6709\u7a7a\u6bb5\u843d\u951a\u70b9\uff0c\u8bf7\u91cd\u65b0\u5bfc\u51fa\u4e66\u7c4d\uff09</p>');
    }
    var chapterCount = ((ch.hotComments || []).length) + ((ch.generalComments || []).length);
    if (showChapterComments) {
      parts.push('<div class="chapter-bar"><button type="button" id="btn-chapter-comments">\u672c\u7ae0\u8bc4\u8bba \u00b7 ' + chapterCount + '</button></div>');
    }
    var nav = [];
    if (chapterIndex > 0) nav.push('<button type="button" class="chap-nav" id="btn-prev-chapter">\u2190 \u4e0a\u4e00\u7ae0</button>');
    if (chapterIndex < book.chapters.length - 1) nav.push('<button type="button" class="chap-nav primary" id="btn-next-chapter">\u4e0b\u4e00\u7ae0 \u2192</button>');
    else nav.push('<span class="chap-end">\u5168\u4e66\u5b8c</span>');
    parts.push('<div class="chapter-nav">' + nav.join('') + '</div>');
    return parts.join('\n');
  }

  function renderChapter(idx, keepPage) {
    if (!book || !book.chapters || !book.chapters.length) {
      content.innerHTML = '<p class="meta">\u65e0\u5185\u5bb9</p>';
      return;
    }
    chapterIndex = Math.max(0, Math.min(idx, book.chapters.length - 1));
    if (!keepPage) pageIndex = 0;
    clearTtsHighlight();
    content.innerHTML = buildChapterHtml();
    bindChromeHandlers();
    fixMedia();
    applyPageMode(pageMode);
    if (window.GnReaderBridge && window.GnReaderBridge.onChapterChanged) {
      window.GnReaderBridge.onChapterChanged(chapterIndex, book.chapters.length);
    }
  }

  function clearTtsHighlight() {
    var els = content.querySelectorAll('.para-block.tts-active');
    for (var i = 0; i < els.length; i++) els[i].classList.remove('tts-active');
  }

  function highlightByUnitIndex(idx, allowScroll) {
    clearTtsHighlight();
    var unit = ttsUnits[idx];
    if (!unit) return;
    var el = content.querySelector('.para-block[data-pid="' + String(unit.pid).replace(/"/g, '') + '"]');
    if (!el) return;
    el.classList.add('tts-active');
    if (allowScroll === false) return; // 暂停时不翻页/不滚动，避免跳页
    if (pageMode === 'scroll') {
      try { el.scrollIntoView({ block: 'center' }); } catch (e) { el.scrollIntoView(true); }
    } else {
      ensureParagraphPage(el);
    }
  }

  function ensureParagraphPage(el) {
    if (pageMode !== 'page' || !pageHeight) return;
    var top = el.offsetTop;
    var target = Math.floor(top / pageHeight);
    if (target < 0) target = 0;
    if (target > pageCount - 1) target = pageCount - 1;
    if (target !== pageIndex) {
      pageIndex = target;
      applyPageTransform();
      notifyPage();
    }
  }

  function splitSentences(text) {
    var t = String(text || '').replace(/^\s+|\s+$/g, '');
    if (!t) return [];
    var parts = [];
    var buf = '';
    var i, ch, s, rest;
    for (i = 0; i < t.length; i++) {
      buf += t.charAt(i);
      ch = t.charAt(i);
      if (ch === '\u3002' || ch === '\uff01' || ch === '\uff1f' || ch === '!' || ch === '?' || ch === '\uff1b' || ch === ';' || ch === '\n') {
        s = buf.replace(/^\s+|\s+$/g, '');
        if (s) parts.push(s);
        buf = '';
      }
    }
    rest = buf.replace(/^\s+|\s+$/g, '');
    if (rest) parts.push(rest);
    if (parts.length > 1) return parts;
    if (t.length <= 80) return [t];
    parts = [];
    buf = '';
    for (i = 0; i < t.length; i++) {
      buf += t.charAt(i);
      if (buf.length >= 60 && ('\uff0c\u3001, '.indexOf(t.charAt(i)) >= 0)) {
        parts.push(buf.replace(/^\s+|\s+$/g, ''));
        buf = '';
      } else if (buf.length >= 100) {
        parts.push(buf.replace(/^\s+|\s+$/g, ''));
        buf = '';
      }
    }
    rest = buf.replace(/^\s+|\s+$/g, '');
    if (rest) parts.push(rest);
    return parts.length ? parts : [t];
  }

  function collectParagraphUnits() {
    var ch = book && book.chapters[chapterIndex];
    if (!ch) return [];
    var out = [];
    var paras = ch.paragraphs || [];
    for (var i = 0; i < paras.length; i++) {
      var text = String(paras[i].text || '').replace(/^\s+|\s+$/g, '');
      if (text) out.push({ pid: paras[i].id, text: text });
    }
    return out;
  }

  function visibleParaIds() {
    var ids = [];
    if (pageMode === 'page' && clip) {
      var clipRect = clip.getBoundingClientRect();
      var blocks = content.querySelectorAll('.para-block');
      for (var i = 0; i < blocks.length; i++) {
        var r = blocks[i].getBoundingClientRect();
        if (r.bottom > clipRect.top + 2 && r.top < clipRect.bottom - 2) {
          ids.push(blocks[i].getAttribute('data-pid'));
        }
      }
    } else {
      // \u6eda\u52a8\uff1a\u53d6\u89c6\u53e3\u5185\u7b2c\u4e00\u4e2a\u53ef\u89c1\u6bb5
      var vh = window.innerHeight || 600;
      var blocks2 = content.querySelectorAll('.para-block');
      for (var j = 0; j < blocks2.length; j++) {
        var r2 = blocks2[j].getBoundingClientRect();
        if (r2.bottom > 8 && r2.top < vh - 8) ids.push(blocks2[j].getAttribute('data-pid'));
      }
    }
    return ids;
  }

  /** \u4ece\u5f53\u524d\u9875\u7b2c\u4e00\u6bb5\u8d77\uff0c\u9010\u53e5\u5230\u7ae0\u672b */
  function collectSegmentUnitsFromPage() {
    var all = collectParagraphUnits();
    var vis = visibleParaIds();
    var start = 0;
    if (vis.length) {
      for (var i = 0; i < all.length; i++) {
        if (all[i].pid === vis[0]) { start = i; break; }
      }
    }
    var units = [];
    for (var j = start; j < all.length; j++) {
      var parts = splitSentences(all[j].text);
      if (parts.length <= 1) units.push(all[j]);
      else {
        for (var k = 0; k < parts.length; k++) units.push({ pid: all[j].pid, text: parts[k], part: k });
      }
    }
    return units;
  }

  function collectPageUnits() {
    var units = [];
    var vis = visibleParaIds();
    var ch = book && book.chapters[chapterIndex];
    if (!ch) return collectSegmentUnitsFromPage();
    var paras = ch.paragraphs || [];
    for (var i = 0; i < vis.length; i++) {
      var pid = vis[i];
      var body = '';
      for (var j = 0; j < paras.length; j++) {
        if (paras[j].id === pid) { body = String(paras[j].text || '').replace(/^\s+|\s+$/g, ''); break; }
      }
      if (!body) continue;
      var parts = splitSentences(body);
      for (var k = 0; k < parts.length; k++) units.push({ pid: pid, text: parts[k], part: k });
    }
    return units.length ? units : collectSegmentUnitsFromPage();
  }

  function tryToggleSpeak() {
    try {
      if (!window.GnReaderBridge) return false;
      if (window.__gnSpeaking || speakingFlag) {
        if (typeof window.GnReaderBridge.pauseTts === 'function') window.GnReaderBridge.pauseTts();
        return true;
      }
      if (window.__gnPaused || pausedFlag) {
        if (typeof window.GnReaderBridge.resumeTts === 'function') window.GnReaderBridge.resumeTts();
        return true;
      }
    } catch (e) {}
    return false;
  }

  function scrollToPid(pid) {
    var el = content.querySelector('.para-block[data-pid="' + String(pid).replace(/"/g, '') + '"]');
    if (!el) return false;
    if (pageMode === 'page') ensureParagraphPage(el);
    else {
      try { el.scrollIntoView({ block: 'center' }); } catch (e) { el.scrollIntoView(true); }
    }
    el.classList.add('tts-active');
    setTimeout(function () { el.classList.remove('tts-active'); }, 1800);
    return true;
  }

  window.GnReader = {
    engine: ENGINE,
    loadBook: function (json, opts) {
      try { book = typeof json === 'string' ? JSON.parse(json) : json; }
      catch (e) { content.innerHTML = '<p class="meta">\u4e66\u7c4d\u89e3\u6790\u5931\u8d25</p>'; return; }
      opts = opts || {};
      if (typeof opts.safeTop === 'number') safeTop = opts.safeTop;
      if (typeof opts.safeBottom === 'number') safeBottom = opts.safeBottom;
      if (typeof opts.padH === 'number') { padH = opts.padH; padL = opts.padH; padR = opts.padH; }
      if (typeof opts.padV === 'number') { padV = opts.padV; padT = opts.padV; padB = opts.padV; }
      if (typeof opts.padL === 'number') padL = opts.padL;
      if (typeof opts.padR === 'number') padR = opts.padR;
      if (typeof opts.padT === 'number') padT = opts.padT;
      if (typeof opts.padB === 'number') padB = opts.padB;
      if (typeof opts.lineMult === 'number') lineMult = opts.lineMult;
      if (typeof opts.viewportH === 'number') viewportH = opts.viewportH;
      if (typeof opts.viewportW === 'number') viewportW = opts.viewportW;
      if (typeof opts.showParagraphComments === 'boolean') showParaComments = opts.showParagraphComments;
      if (typeof opts.showChapterComments === 'boolean') showChapterComments = opts.showChapterComments;
      if (typeof opts.ttsHlMode === 'string') ttsHlMode = opts.ttsHlMode;
      if (typeof opts.ttsHlColor === 'string') ttsHlColor = opts.ttsHlColor;
      applyTheme(opts.theme, opts.fontSize);
      applyLayoutVars();
      applyTtsHighlightStyle();
      pageMode = opts.pageMode || 'scroll';
      pageIndex = 0;
      renderChapter(opts.chapterIndex || 0);
    },
    setViewport: function (w, h, pH, pV, lMult, pL, pR, pT, pB) {
      if (typeof w === 'number') viewportW = w;
      if (typeof h === 'number') viewportH = h;
      if (typeof pH === 'number') { padH = pH; padL = pH; padR = pH; }
      if (typeof pV === 'number') { padV = pV; padT = pV; padB = pV; }
      if (typeof pL === 'number') padL = pL;
      if (typeof pR === 'number') padR = pR;
      if (typeof pT === 'number') padT = pT;
      if (typeof pB === 'number') padB = pB;
      if (typeof lMult === 'number') lineMult = lMult;
      applyLayoutVars();
      if (pageMode === 'page') applyPageMode('page');
    },
    setCommentDisplay: function (para, chapter) {
      showParaComments = !!para;
      showChapterComments = !!chapter;
      if (book) renderChapter(chapterIndex, true);
    },
    setSafeInsets: function (top, bottom) {
      safeTop = top || 0;
      safeBottom = bottom || 28;
      if (pageMode === 'page') applyPageMode('page');
    },
    setTheme: function (theme, fontSize) {
      applyTheme(theme, fontSize);
      if (pageMode === 'page') applyPageMode('page');
    },
    setLayout: function (pH, pV, lMult, pL, pR, pT, pB) {
      if (typeof pH === 'number') { padH = pH; padL = pH; padR = pH; }
      if (typeof pV === 'number') { padV = pV; padT = pV; padB = pV; }
      if (typeof pL === 'number') padL = pL;
      if (typeof pR === 'number') padR = pR;
      if (typeof pT === 'number') padT = pT;
      if (typeof pB === 'number') padB = pB;
      if (typeof lMult === 'number') lineMult = lMult;
      applyLayoutVars();
      document.documentElement.style.setProperty('--line-height', String(lineMult));
      if (pageMode === 'page') applyPageMode('page');
    },
    setTtsHighlight: function (mode, color) {
      if (typeof mode === 'string') ttsHlMode = mode;
      if (typeof color === 'string' && color) ttsHlColor = color;
      applyTtsHighlightStyle();
    },
    setPageMode: function (mode) { applyPageMode(mode); },
    setSpeaking: function (on) {
      speakingFlag = !!on;
      window.__gnSpeaking = speakingFlag;
      if (on) { pausedFlag = false; window.__gnPaused = false; }
    },
    setPaused: function (on) {
      pausedFlag = !!on;
      window.__gnPaused = pausedFlag;
      if (on) { speakingFlag = false; window.__gnSpeaking = false; }
    },
    nextChapter: function () {
      if (!book) return;
      if (chapterIndex < book.chapters.length - 1) renderChapter(chapterIndex + 1);
    },
    prevChapter: function () {
      if (!book) return;
      if (chapterIndex > 0) renderChapter(chapterIndex - 1);
    },
    goChapter: function (i) { renderChapter(i); },
    goChapterPid: function (i, pid) {
      renderChapter(i);
      setTimeout(function () { scrollToPid(pid); }, 80);
    },
    nextPage: function () {
      if (pageMode !== 'page') {
        var before = window.scrollY || window.pageYOffset || 0;
        window.scrollBy(0, Math.floor((window.innerHeight - safeTop - safeBottom) * 0.92));
        setTimeout(function () {
          var max = document.documentElement.scrollHeight - window.innerHeight - 4;
          var now = window.scrollY || window.pageYOffset || 0;
          if (now >= max && now <= before + 2) {
            if (book && chapterIndex < book.chapters.length - 1) {
              window.GnReader.nextChapter();
              window.scrollTo(0, 0);
            }
          }
        }, 40);
        return;
      }
      if (pageIndex >= pageCount - 1) {
        if (book && chapterIndex < book.chapters.length - 1) window.GnReader.nextChapter();
        return;
      }
      pageIndex += 1;
      applyPageTransform();
      notifyPage();
    },
    prevPage: function () {
      if (pageMode !== 'page') {
        var y = window.scrollY || window.pageYOffset || 0;
        if (y <= 2) {
          if (book && chapterIndex > 0) {
            window.GnReader.prevChapter();
            setTimeout(function () { window.scrollTo(0, document.documentElement.scrollHeight); }, 50);
          }
          return;
        }
        window.scrollBy(0, -Math.floor((window.innerHeight - safeTop - safeBottom) * 0.92));
        return;
      }
      if (pageIndex <= 0) {
        if (book && chapterIndex > 0) {
          renderChapter(chapterIndex - 1, true);
          setTimeout(function () {
            pageIndex = Math.max(0, pageCount - 1);
            applyPageTransform();
            notifyPage();
          }, 40);
        }
        return;
      }
      pageIndex -= 1;
      applyPageTransform();
      notifyPage();
    },
    prepareTts: function (mode) {
      clearTtsHighlight();
      if (mode === 'page') ttsUnits = collectPageUnits();
      else if (mode === 'chapter') ttsUnits = collectParagraphUnits();
      else ttsUnits = collectSegmentUnitsFromPage();
      return JSON.stringify({
        texts: ttsUnits.map(function (u) { return u.text; }),
        pids: ttsUnits.map(function (u) { return u.pid; }),
        engine: ENGINE,
        unitCount: ttsUnits.length,
        linesPerPage: linesPerPage
      });
    },
    highlightTtsIndex: function (idx, allowScroll) { highlightByUnitIndex(idx, allowScroll !== false); },
    highlightTtsIndexQuiet: function (idx) { highlightByUnitIndex(idx, false); },
    clearTtsHighlight: clearTtsHighlight,
    searchInChapter: function (q) {
      q = String(q || '').replace(/^\s+|\s+$/g, '');
      if (!q || !book) return '[]';
      var hits = [];
      var chs = book.chapters || [];
      for (var ci = 0; ci < chs.length; ci++) {
        var paras = chs[ci].paragraphs || [];
        for (var pi = 0; pi < paras.length; pi++) {
          var text = String(paras[pi].text || '');
          var at = text.indexOf(q);
          if (at < 0) continue;
          var snipStart = Math.max(0, at - 12);
          var snip = text.substring(snipStart, Math.min(text.length, at + q.length + 24));
          hits.push({
            chapterIndex: ci,
            chapterTitle: chs[ci].title || '',
            pid: paras[pi].id,
            snippet: snip
          });
          if (hits.length >= 80) break;
        }
        if (hits.length >= 80) break;
      }
      return JSON.stringify(hits);
    },
    getProgress: function () {
      return {
        chapterIndex: chapterIndex,
        chapterCount: book ? book.chapters.length : 0,
        pageIndex: pageIndex,
        pageCount: pageCount,
        linesPerPage: linesPerPage,
        engine: ENGINE,
        title: book && book.chapters[chapterIndex] ? book.chapters[chapterIndex].title : ''
      };
    }
  };

  document.addEventListener('click', function (ev) {
    var t = ev.target;
    if (t && t.closest && t.closest('button, .pc-badge, a')) return;
    var x = ev.clientX;
    var w = window.innerWidth;
    // \u4e2d\u533a\uff1a\u6717\u8bfb\u4e2d/\u6682\u505c \u5207\u6362\uff1b\u5426\u5219\u547c\u83dc\u5355
    if (x >= w * 0.28 && x <= w * 0.72) {
      if (tryToggleSpeak()) return;
      if (window.GnReaderBridge && window.GnReaderBridge.toggleChrome) window.GnReaderBridge.toggleChrome();
      return;
    }
    if (tryToggleSpeak()) return;
    if (x < w * 0.28) window.GnReader.prevPage();
    else window.GnReader.nextPage();
  });

  window.addEventListener('resize', function () {
    if (pageMode === 'page') applyPageMode('page');
  });

  if (hint) hint.setAttribute('data-engine', ENGINE);
})();
