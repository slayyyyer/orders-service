// Recorded API explorer: renders recorded request/response pairs.
// This page cannot send requests. It has no server and no API route, which is the point.
(function () {
  'use strict';

  var list = document.getElementById('call-list');
  var detail = document.getElementById('call-detail');
  var lab = window.LAB || {};

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  function block(title, content) {
    var wrapper = document.createElement('div');
    wrapper.appendChild(el('h4', null, title));
    var pre = el('pre');
    pre.appendChild(el('code', null, content));
    wrapper.appendChild(pre);
    return wrapper;
  }

  var stamp = document.getElementById('build-stamp');
  if (stamp) {
    var sha = (lab.commit || 'local').slice(0, 7);
    stamp.textContent = 'Recorded from commit ' + sha +
      ' on ' + (lab.generatedAt || 'an unknown date') + '. Static replay; no requests are sent.';
  }

  fetch('data/recordings.json', { cache: 'no-store' })
    .then(function (response) {
      if (!response.ok) throw new Error('recordings.json returned ' + response.status);
      return response.json();
    })
    .then(function (recordings) {
      if (!recordings.requests || !recordings.requests.length) {
        throw new Error('no requests recorded');
      }
      list.innerHTML = '';
      render(recordings, 0);

      recordings.requests.forEach(function (call, index) {
        var item = document.createElement('li');
        var button = document.createElement('button');
        button.type = 'button';
        button.setAttribute('aria-current', index === 0 ? 'true' : 'false');

        var method = el('span', 'method' + (call.method === 'GET' ? '' : ' post'), call.method);
        var label = el('span', null, call.label);
        var ok = call.status >= 200 && call.status < 300;
        var status = el('span', 'status-code ' + (ok ? 'ok' : 'err'), String(call.status));

        button.appendChild(method);
        button.appendChild(label);
        button.appendChild(status);
        button.addEventListener('click', function () {
          Array.prototype.forEach.call(list.querySelectorAll('button'), function (other) {
            other.setAttribute('aria-current', 'false');
          });
          button.setAttribute('aria-current', 'true');
          render(recordings, index);
        });

        item.appendChild(button);
        list.appendChild(item);
      });
    })
    .catch(function (error) {
      list.innerHTML = '';
      detail.innerHTML = '';
      detail.appendChild(el('p', 'loading',
        'No recordings in this build (' + error.message + '). The CI pipeline writes them from a real run.'));
    });

  function render(recordings, index) {
    var call = recordings.requests[index];
    detail.innerHTML = '';

    detail.appendChild(el('h3', null, call.label));

    var meta = el('div', 'meta');
    meta.textContent = call.method + ' ' + call.path + '  ·  ' + call.status +
      '  ·  ' + call.durationMs + ' ms  ·  recorded ' + recordings.recordedAt +
      ' at commit ' + String(recordings.commit || '').slice(0, 7);
    detail.appendChild(meta);

    detail.appendChild(block(
      'Request body',
      call.requestBody == null ? '(no body)' : JSON.stringify(call.requestBody, null, 2)));

    detail.appendChild(block(
      'Recorded response',
      call.responseBody == null ? '(empty response)' : JSON.stringify(call.responseBody, null, 2)));

    if (call.status >= 400) {
      detail.appendChild(el('p', 'sub',
        'This call failed on purpose: the error model is part of the contract, so the ' +
        'recording shows the failure shape rather than hiding it.'));
    }
  }
})();
