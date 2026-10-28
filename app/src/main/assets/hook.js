// 스카이스캐너 페이지가 서버에서 받아 오는 검색 결과(JSON)를 엿보아 가장 싼 값을 모은다.
// 페이지 글자를 읽는 것보다 정확하다 (광고·달력 가격이 섞이지 않음).
(function () {
  if (window.__fa) return;
  var fa = (window.__fa = { min: null, hits: 0, last: 0 });

  function divisor(unit) {
    unit = String(unit || '').toUpperCase();
    if (unit.indexOf('MILLI') >= 0) return 1000;
    if (unit.indexOf('CENTI') >= 0) return 100;
    return 1;
  }
  function take(v, unit) {
    if (typeof v === 'string') v = parseFloat(v);
    if (typeof v !== 'number' || !isFinite(v)) return;
    v = v / divisor(unit);
    if (v < 10000 || v > 100000000) return; // 원화 항공권으로 보기 어려운 수
    v = Math.round(v);
    if (fa.min === null || v < fa.min) fa.min = v;
    fa.hits++;
    fa.last = Date.now();
  }
  function readPrice(p) {
    if (p === null || p === undefined) return;
    if (typeof p === 'number' || typeof p === 'string') return take(p);
    if (typeof p !== 'object') return;
    if ('raw' in p) return take(p.raw);
    if ('amount' in p) return take(p.amount, p.unit);
  }
  // 항공편 묶음(itineraries) 안의 가격만 본다
  function walk(o, depth) {
    if (!o || typeof o !== 'object' || depth > 30) return;
    if (Array.isArray(o)) {
      for (var i = 0; i < o.length; i++) walk(o[i], depth + 1);
      return;
    }
    for (var k in o) {
      if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
      if (k === 'price' || k === 'rawPrice' || k === 'cheapestPrice') readPrice(o[k]);
      else walk(o[k], depth + 1);
    }
  }
  function findItineraries(o, depth) {
    if (!o || typeof o !== 'object' || depth > 8) return;
    for (var k in o) {
      if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
      if (k === 'itineraries') walk(o[k], 0);
      else if (o[k] && typeof o[k] === 'object') findItineraries(o[k], depth + 1);
    }
  }
  function inspect(text) {
    if (!text || text.indexOf('itineraries') < 0) return;
    try {
      findItineraries(JSON.parse(text), 0);
    } catch (e) {}
  }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function () {
      return origFetch.apply(this, arguments).then(function (res) {
        try {
          res.clone().text().then(inspect, function () {});
        } catch (e) {}
        return res;
      });
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function () {
    return origOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    var xhr = this;
    xhr.addEventListener('load', function () {
      try {
        if (xhr.responseType === '' || xhr.responseType === 'text') inspect(xhr.responseText);
        else if (xhr.responseType === 'json') inspect(JSON.stringify(xhr.response));
      } catch (e) {}
    });
    return origSend.apply(this, arguments);
  };
})();
