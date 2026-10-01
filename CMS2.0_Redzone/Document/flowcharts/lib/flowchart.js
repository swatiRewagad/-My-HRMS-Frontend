/* Shared bootstrap for the CMS 2.0 flowchart pages: offline mermaid render + pan/zoom. */
(function () {
  'use strict';

  var THEME = {
    startOnLoad: false,
    theme: 'base',
    securityLevel: 'loose',
    maxTextSize: 500000,
    maxEdges: 2000,
    themeVariables: {
      fontFamily: "'Segoe UI', system-ui, sans-serif",
      fontSize: '14px',
      primaryColor: '#ffffff',
      primaryBorderColor: '#94a3b8',
      primaryTextColor: '#1f2937',
      lineColor: '#64748b',
      tertiaryColor: '#f1f5f9'
    },
    flowchart: { curve: 'basis', nodeSpacing: 45, rankSpacing: 60, padding: 12, useMaxWidth: false },
    sequence: { useMaxWidth: false, wrap: true },
    er: { useMaxWidth: false }
  };

  /* Node classes shared by every diagram on every page. */
  var CLASSDEFS = [
    'classDef start fill:#dbeafe,stroke:#2563eb,stroke-width:2px,color:#1e3a5f',
    'classDef state fill:#ffffff,stroke:#94a3b8,stroke-width:1.5px,color:#1f2937',
    'classDef dec fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f',
    'classDef term fill:#dcfce7,stroke:#059669,stroke-width:2px,color:#064e3b',
    'classDef bad fill:#fee2e2,stroke:#dc2626,stroke-width:2px,color:#7f1d1d',
    'classDef auto fill:#f3e8ff,stroke:#7c3aed,stroke-width:2px,color:#4c1d95',
    'classDef gap fill:#fdf2f8,stroke:#db2777,stroke-width:2px,stroke-dasharray:4 3,color:#831843',
    'classDef ext fill:#f8fafc,stroke:#475569,stroke-width:1.5px,color:#334155'
  ].join('\n  ');

  function attachPanZoom(view) {
    var pan = view.querySelector('.pan');
    if (!pan) return;
    var scale = 1, x = 0, y = 0, dragging = false, sx = 0, sy = 0;

    function apply() {
      pan.style.transform = 'translate(' + x + 'px,' + y + 'px) scale(' + scale + ')';
    }
    function zoomAt(factor, cx, cy) {
      var next = Math.min(4, Math.max(0.15, scale * factor));
      var r = view.getBoundingClientRect();
      var px = (cx - r.left - x) / scale, py = (cy - r.top - y) / scale;
      x -= px * (next - scale);
      y -= py * (next - scale);
      scale = next;
      apply();
    }

    view.addEventListener('wheel', function (e) {
      e.preventDefault();
      zoomAt(e.deltaY < 0 ? 1.12 : 1 / 1.12, e.clientX, e.clientY);
    }, { passive: false });

    view.addEventListener('mousedown', function (e) {
      dragging = true; sx = e.clientX - x; sy = e.clientY - y;
      view.classList.add('grabbing');
    });
    window.addEventListener('mousemove', function (e) {
      if (!dragging) return;
      x = e.clientX - sx; y = e.clientY - sy; apply();
    });
    window.addEventListener('mouseup', function () {
      dragging = false; view.classList.remove('grabbing');
    });

    function dims() {
      var svg = pan.querySelector('svg');
      if (!svg) return null;
      var bb = svg.getBoundingClientRect();
      var vb = svg.viewBox && svg.viewBox.baseVal;
      var w = vb && vb.width ? vb.width : bb.width / (scale || 1);
      var h = vb && vb.height ? vb.height : bb.height / (scale || 1);
      return (w && h) ? { w: w, h: h } : null;
    }

    view._fit = function () {
      var d = dims();
      if (!d) return;
      var r = view.getBoundingClientRect();
      scale = Math.min((r.width - 24) / d.w, (r.height - 24) / d.h, 1.6);
      if (!isFinite(scale) || scale <= 0) scale = 1;
      x = (r.width - d.w * scale) / 2;
      y = 12;
      apply();
    };

    /* Initial view: fit the width, but never shrink below a readable size —
       a tall graph is meant to be scrolled, not squinted at. */
    view._initFit = function () {
      var d = dims();
      if (!d) return;
      var r = view.getBoundingClientRect();
      scale = Math.min((r.width - 32) / d.w, 1.25);
      if (!isFinite(scale) || scale <= 0) scale = 1;
      if (scale < 0.62) scale = 0.62;
      x = (r.width - d.w * scale) / 2;
      y = 12;
      apply();
    };
    view._reset = function () { scale = 1; x = 0; y = 0; apply(); };
    view._zoom = function (f) {
      var r = view.getBoundingClientRect();
      zoomAt(f, r.left + r.width / 2, r.top + r.height / 2);
    };
  }

  function wireBar(diagram) {
    var view = diagram.querySelector('.diagram-view');
    var bar = diagram.querySelector('.diagram-bar');
    if (!view || !bar) return;
    bar.addEventListener('click', function (e) {
      var act = e.target.getAttribute && e.target.getAttribute('data-act');
      if (!act) return;
      if (act === 'in') view._zoom(1.25);
      else if (act === 'out') view._zoom(1 / 1.25);
      else if (act === 'fit') view._fit();
      else if (act === 'reset') view._reset();
      else if (act === 'full') {
        if (document.fullscreenElement) document.exitFullscreen();
        else if (diagram.requestFullscreen) diagram.requestFullscreen().then(function () {
          setTimeout(view._fit, 120);
        });
      } else if (act === 'svg') {
        var svg = view.querySelector('svg');
        if (!svg) return;
        var blob = new Blob([
          '<?xml version="1.0" encoding="UTF-8"?>\n' + svg.outerHTML
        ], { type: 'image/svg+xml' });
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = (diagram.id || 'diagram') + '.svg';
        a.click();
        URL.revokeObjectURL(a.href);
      }
    });
  }

  /* The pages write <br/> inside their graph source for multi-line node labels.
     The browser parses those as real elements, so textContent would silently
     glue the words together — recover them from the markup instead. */
  function sourceOf(block) {
    return block.innerHTML
      .replace(/<br\s*\/?>/gi, '<br/>')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#0?39;/g, "'")
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, '&');
  }

  async function boot() {
    var blocks = [].slice.call(document.querySelectorAll('.mermaid'));

    // Inject the shared classDefs into every graph that uses the shared class names.
    blocks.forEach(function (b) {
      var src = sourceOf(b);
      if (/^\s*(flowchart|graph)\b/.test(src) && src.indexOf('classDef start') === -1) {
        src = src.replace(/\s*$/, '') + '\n  ' + CLASSDEFS + '\n';
      }
      b.textContent = src;
    });

    if (typeof mermaid === 'undefined') {
      blocks.forEach(function (b) {
        b.innerHTML = '<p style="color:#dc2626;padding:12px">mermaid.min.js failed to load '
          + '— check that lib/mermaid.min.js sits next to this file.</p>';
      });
      return;
    }

    mermaid.initialize(THEME);

    // Render one at a time so a single bad graph cannot abort the rest of the page.
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      try {
        await mermaid.run({ nodes: [b], suppressErrors: false });
      } catch (err) {
        b.innerHTML = '<pre style="color:#b91c1c;white-space:pre-wrap;padding:12px;'
          + 'font-size:12px">Diagram failed to render:\n' + String(err && err.message || err)
          + '</pre>';
        b.setAttribute('data-render-failed', '1');
      }
    }

    document.querySelectorAll('.diagram').forEach(function (d) {
      var view = d.querySelector('.diagram-view');
      if (!view) return;
      attachPanZoom(view);
      wireBar(d);
      if (view._initFit) view._initFit();
    });

    document.addEventListener('fullscreenchange', function () {
      var d = document.fullscreenElement;
      if (!d) return;
      var v = d.querySelector && d.querySelector('.diagram-view');
      if (v && v._fit) setTimeout(v._fit, 120);
    });

    document.body.setAttribute('data-flowcharts-ready', '1');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
