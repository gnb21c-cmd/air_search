// 점검용: 페이지가 주고받는 자료를 모두 적어 둔다 (어느 주소에서 어떤 이름으로 가격이 오는지 찾기 위함).
(function () {
  if (window.__diag) return;
  var D = (window.__diag = { net: [], started: Date.now() });

  function pricePaths(o, path, out, depth) {
    if (out.length >= 25 || depth > 25 || !o || typeof o !== 'object') return;
    if (Array.isArray(o)) {
      for (var i = 0; i < o.length && i < 3; i++) pricePaths(o[i], path + '[' + i + ']', out, depth + 1);
      return;
    }
    for (var k in o) {
      if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
      var v = o[k];
      var p = path + '.' + k;
      if (/price|amount|fare|cost/i.test(k)) {
        var s;
        try { s = JSON.stringify(v); } catch (e) { s = String(v); }
        out.push(p + ' = ' + String(s).slice(0, 140));
        if (out.length >= 25) return;
      }
      if (v && typeof v === 'object') pricePaths(v, p, out, depth + 1);
    }
  }

  function record(kind, method, url, status, ctype, text) {
    var e = { k: kind, m: method, u: String(url).slice(0, 220), s: status, t: String(ctype || '').slice(0, 40),
      n: text ? text.length : 0, json: false, it: false, keys: '', prices: [] };
    if (text && (/json/i.test(ctype || '') || /^\s*[\[{]/.test(text))) {
      try {
        var o = JSON.parse(text);
        e.json = true;
        e.it = text.indexOf('itineraries') >= 0;
        e.keys = (Array.isArray(o) ? ['(배열 ' + o.length + ')'] : Object.keys(o)).slice(0, 15).join(',');
        pricePaths(o, '$', e.prices, 0);
      } catch (x) {}
    }
    if (D.net.length < 400) D.net.push(e);
  }
  function readable(ctype) {
    return !ctype || /json|text\/plain|javascript/i.test(ctype);
  }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var url = input && input.url ? input.url : input;
      var method = (init && init.method) || (input && input.method) || 'GET';
      return origFetch.apply(this, arguments).then(function (res) {
        try {
          var ct = res.headers.get('content-type') || '';
          if (readable(ct) && !/javascript/i.test(ct)) {
            res.clone().text().then(function (t) { record('fetch', method, url, res.status, ct, t); }, function () {
              record('fetch', method, url, res.status, ct, '');
            });
          } else record('fetch', method, url, res.status, ct, '');
        } catch (e) {}
        return res;
      }, function (err) {
        record('fetch', method, url, 'ERR ' + err, '', '');
        throw err;
      });
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__d = { m: method, u: url };
    return origOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    var xhr = this;
    xhr.addEventListener('loadend', function () {
      try {
        var d = xhr.__d || {};
        var ct = xhr.getResponseHeader('content-type') || '';
        var t = '';
        if (xhr.responseType === '' || xhr.responseType === 'text') t = xhr.responseText;
        else if (xhr.responseType === 'json') t = JSON.stringify(xhr.response);
        record('xhr', d.m, d.u, xhr.status, ct, readable(ct) ? t : '');
      } catch (e) {}
    });
    return origSend.apply(this, arguments);
  };

  // 점검 기록 글 만들기 (앱이 불러 씀)
  window.__diagReport = function () {
    var L = [];
    var body = document.body ? document.body.innerText : '';
    var href = location.href;
    var captcha = href.indexOf('captcha') >= 0 || !!document.querySelector('#px-captcha') ||
      body.indexOf('로봇이 아닙니다') >= 0 || body.indexOf('사람인지') >= 0 || /are you a person or a robot/i.test(body);
    L.push('[페이지]');
    L.push('주소: ' + href);
    L.push('제목: ' + document.title);
    L.push('사람 확인 화면: ' + (captcha ? '예' : '아니오'));
    L.push('연 뒤 지난 시간: ' + Math.round((Date.now() - D.started) / 1000) + '초');
    var fa = window.__fa || {};
    L.push('');
    L.push('[앱의 가격 엿보기]');
    L.push('최저: ' + (fa.min || '없음') + ' · 잡은 가격 수: ' + (fa.hits || 0) + ' · 엿보기 동작: ' + (window.__fa ? '예' : '아니오'));

    var json = D.net.filter(function (e) { return e.json; });
    var it = json.filter(function (e) { return e.it; });
    L.push('');
    L.push('[주고받은 자료] 전체 ' + D.net.length + '개 · JSON ' + json.length + '개 · 항공편(itineraries) 든 것 ' + it.length + '개');
    var shown = D.net.filter(function (e) { return e.json || (typeof e.s === 'number' && e.s >= 400) || typeof e.s === 'string'; });
    shown.slice(0, 60).forEach(function (e) {
      L.push('- ' + e.k + ' ' + e.m + ' ' + e.s + ' ' + e.t + ' ' + e.n + '자' + (e.it ? ' [항공편]' : '') + '\n  ' + e.u +
        (e.keys ? '\n  키: ' + e.keys : ''));
      e.prices.slice(0, e.it ? 25 : 6).forEach(function (p) { L.push('    ' + p); });
    });

    L.push('');
    L.push('[페이지 안에 박힌 자료]');
    var scripts = document.querySelectorAll('script');
    var found = 0;
    for (var i = 0; i < scripts.length; i++) {
      var s = scripts[i].textContent || '';
      var at = s.indexOf('itineraries');
      if (at < 0 && !/"price"/.test(s)) continue;
      found++;
      L.push('- script id=' + (scripts[i].id || '') + ' type=' + (scripts[i].type || '') + ' ' + s.length + '자');
      var pa = s.indexOf('price', at < 0 ? 0 : at);
      if (pa >= 0) L.push('  …' + s.slice(Math.max(0, pa - 80), pa + 160).replace(/\s+/g, ' ') + '…');
      if (found >= 8) break;
    }
    if (!found) L.push('없음');

    var cheap = body.indexOf('최저가');
    L.push('');
    L.push("[화면 글자] '최저가' " + (cheap >= 0 ? '있음: …' + body.slice(cheap, cheap + 60).replace(/\s+/g, ' ') + '…' : '없음'));
    L.push(body.slice(0, 3000));
    return JSON.stringify({ report: L.join('\n'), text: body.slice(0, 30000) });
  };
})();
