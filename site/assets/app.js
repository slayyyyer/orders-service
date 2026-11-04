// Overview page: build stamp and the k6 summary, rendered from recorded CI artefacts.
// Every fetch is allowed to fail; a missing artefact becomes a sentence, not an exception.
(function () {
  'use strict';

  var lab = window.LAB || {};

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  var stamp = document.getElementById('build-stamp');
  if (stamp) {
    stamp.innerHTML = '';
    var sha = (lab.commit || 'local').slice(0, 7);
    stamp.appendChild(document.createTextNode('Recorded from commit '));
    stamp.appendChild(el('code', null, sha));
    stamp.appendChild(document.createTextNode(
      ' on ' + (lab.generatedAt || 'an unknown date') + '. Responses captured by CI; no server is running.'));
    if (lab.runUrl) {
      stamp.appendChild(document.createTextNode(' '));
      var link = el('a', null, 'CI run');
      link.href = lab.runUrl;
      stamp.appendChild(link);
    }
  }

  function stat(value, label) {
    var box = el('div', 'stat');
    box.appendChild(el('div', 'value', value));
    box.appendChild(el('div', 'label', label));
    return box;
  }

  function bars(values, labels) {
    // Hand-rolled SVG: no chart library, no runtime dependency.
    var width = 640, height = 180, pad = 28;
    var max = Math.max.apply(null, values.concat([1]));
    var barWidth = (width - pad * 2) / values.length - 16;

    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'Latency percentiles in milliseconds');

    values.forEach(function (value, index) {
      var x = pad + index * (barWidth + 16);
      var barHeight = Math.max(2, ((height - pad * 2) * value) / max);
      var y = height - pad - barHeight;

      var bar = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
      bar.setAttribute('x', x);
      bar.setAttribute('y', y);
      bar.setAttribute('width', barWidth);
      bar.setAttribute('height', barHeight);
      bar.setAttribute('rx', 3);
      bar.setAttribute('fill', index === values.length - 1 ? '#1f5f8b' : '#8fb4cc');
      svg.appendChild(bar);

      var caption = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      caption.setAttribute('x', x + barWidth / 2);
      caption.setAttribute('y', y - 7);
      caption.setAttribute('text-anchor', 'middle');
      caption.setAttribute('font-size', '11');
      caption.setAttribute('font-family', 'monospace');
      caption.setAttribute('fill', '#4d5765');
      caption.textContent = value.toFixed(0) + 'ms';
      svg.appendChild(caption);

      var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      label.setAttribute('x', x + barWidth / 2);
      label.setAttribute('y', height - 8);
      label.setAttribute('text-anchor', 'middle');
      label.setAttribute('font-size', '11');
      label.setAttribute('font-family', 'monospace');
      label.setAttribute('fill', '#4d5765');
      label.textContent = labels[index];
      svg.appendChild(label);
    });

    return svg;
  }

  var body = document.getElementById('load-body');
  if (!body) return;

  fetch('data/k6-summary.json', { cache: 'no-store' })
    .then(function (response) {
      if (!response.ok) throw new Error('k6 summary returned ' + response.status);
      return response.json();
    })
    .then(function (summary) {
      var metrics = summary.metrics || {};
      var duration = metrics.http_req_duration ? metrics.http_req_duration.values : {};
      var failed = metrics.http_req_failed ? metrics.http_req_failed.values.rate : null;
      var requests = metrics.http_reqs ? metrics.http_reqs.values : {};

      body.innerHTML = '';

      var stats = el('div', 'stats');
      stats.appendChild(stat(duration['p(95)'] != null ? duration['p(95)'].toFixed(0) + 'ms' : '—', 'p95 latency'));
      stats.appendChild(stat(duration['p(99)'] != null ? duration['p(99)'].toFixed(0) + 'ms' : '—', 'p99 latency'));
      stats.appendChild(stat(requests.rate != null ? requests.rate.toFixed(1) : '—', 'requests / s'));
      stats.appendChild(stat(failed != null ? (failed * 100).toFixed(2) + '%' : '—', 'failed requests'));
      stats.appendChild(stat(requests.count != null ? requests.count : '—', 'total requests'));
      body.appendChild(stats);

      var chart = el('div', 'chart');
      var labels = ['p50', 'p90', 'p95', 'p99'];
      var values = [
        duration['p(50)'] || 0,
        duration['p(90)'] || 0,
        duration['p(95)'] || 0,
        duration['p(99)'] || 0
      ];
      chart.appendChild(bars(values, labels));
      chart.appendChild(el('div', 'legend',
        'Thresholds enforced in CI: p95 < 500ms, p99 < 900ms, failed requests < 1%. ' +
        'A regression fails the build, not just this chart.'));
      body.appendChild(chart);
    })
    .catch(function () {
      body.innerHTML = '';
      body.appendChild(el('p', 'loading',
        'No k6 summary in this build. It is produced by the load-test step in CI.'));
    });
})();
