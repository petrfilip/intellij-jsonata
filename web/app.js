/* JSONata for IntelliJ — landing page demo logic.
   The browser demo runs jsonata.js; the plugin uses the dashjoin
   Java port of the same engine, so behaviour matches. */

(function () {
  "use strict";

  /* ---- sample document the playground starts with ---- */
  var SAMPLE = {
    orders: [
      {
        id: "A-1042", customer: "Acme Co.", status: "shipped",
        items: [
          { sku: "PEN-01", name: "Gel pen", qty: 12, price: 1.2 },
          { sku: "NTB-22", name: "Notebook", qty: 3, price: 4.5 }
        ]
      },
      {
        id: "A-1043", customer: "Globex", status: "pending",
        items: [{ sku: "MUG-07", name: "Mug", qty: 6, price: 7.0 }]
      },
      {
        id: "A-1044", customer: "Acme Co.", status: "shipped",
        items: [
          { sku: "PEN-01", name: "Gel pen", qty: 24, price: 1.2 },
          { sku: "DSK-15", name: "Desk lamp", qty: 1, price: 29.9 }
        ]
      }
    ]
  };

  /* ---- preset expressions (the chips) ---- */
  var PRESETS = [
    { key: "reshape", label: "Reshape", expr: 'orders.{\n  "order": id,\n  "total": $round($sum(items.(qty * price)), 2)\n}' },
    { key: "filter", label: "Filter", expr: 'orders[status = "shipped"].id' },
    { key: "aggregate", label: "Aggregate", expr: '$round($sum(orders.items.(qty * price)), 2)' },
    { key: "group", label: "Group by", expr: 'orders{\n  customer: $round($sum(items.(qty * price)), 2)\n}' },
    { key: "chain", label: "Chain ~>", expr: 'orders.items.name ~> $distinct ~> $sort()' }
  ];

  /* ---- HTML escaping ---- */
  function esc(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }
  function span(cls, txt) { return '<span class="t-' + cls + '">' + txt + "</span>"; }

  /* ---- render a JS value as syntax-highlighted, indented JSON ---- */
  function renderValue(v, indent) {
    var pad = "  ".repeat(indent);
    var pad1 = "  ".repeat(indent + 1);
    if (v === undefined) return span("punc", "(no match)");
    if (v === null) return span("bool", "null");
    var t = typeof v;
    if (t === "number") return span("num", String(v));
    if (t === "boolean") return span("bool", String(v));
    if (t === "string") return span("str", '"' + esc(v) + '"');
    if (typeof v === "function") return span("punc", "(function)");
    if (Array.isArray(v)) {
      if (v.length === 0) return span("punc", "[]");
      var items = v.map(function (x) { return pad1 + renderValue(x, indent + 1); });
      return span("punc", "[") + "\n" + items.join(span("punc", ",") + "\n") + "\n" + pad + span("punc", "]");
    }
    if (t === "object") {
      var keys = Object.keys(v);
      if (keys.length === 0) return span("punc", "{}");
      var rows = keys.map(function (k) {
        return pad1 + span("key", '"' + esc(k) + '"') + span("punc", ": ") + renderValue(v[k], indent + 1);
      });
      return span("punc", "{") + "\n" + rows.join(span("punc", ",") + "\n") + "\n" + pad + span("punc", "}");
    }
    return span("fld", esc(String(v)));
  }

  /* ---- lightweight JSONata expression highlighter (static snippets) ---- */
  var EXPR_RE = /(\s+)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|(\$[A-Za-z_]\w*|\$)|(\d+\.?\d*)|(~>|:=|!=|<=|>=|&|\.\.|[.\[\]{}()<>=+\-*/%?:,@^])|([A-Za-z_]\w*)|(.)/g;
  function highlightExpr(src) {
    var out = "";
    var m;
    EXPR_RE.lastIndex = 0;
    while ((m = EXPR_RE.exec(src)) !== null) {
      if (m[1]) out += esc(m[1]);
      else if (m[2]) out += span("str", esc(m[2]));
      else if (m[3]) out += span("func", esc(m[3]));
      else if (m[4]) out += span("num", esc(m[4]));
      else if (m[5]) out += span("op", esc(m[5]));
      else if (m[6]) out += span("fld", esc(m[6]));
      else if (m[7]) out += esc(m[7]); // pass through separators like ·
    }
    return out;
  }

  /* ---- the playground ---- */
  function initPlayground() {
    var jsonEl = document.getElementById("json-in");
    var exprEl = document.getElementById("expr-in");
    var outEl = document.getElementById("result");
    var foot = document.getElementById("foot-status");
    var chipRow = document.getElementById("presets");
    if (!jsonEl || !exprEl || !outEl || typeof jsonata === "undefined") return;

    jsonEl.value = JSON.stringify(SAMPLE, null, 2);
    exprEl.value = PRESETS[0].expr;

    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    function setOut(html, cls) {
      outEl.className = "code-out" + (cls ? " " + cls : "");
      outEl.innerHTML = html;
      if (!reduce) {
        outEl.classList.remove("is-pulse");
        // force reflow so the animation restarts each compute
        void outEl.offsetWidth;
        outEl.classList.add("is-pulse");
      }
    }

    function run() {
      var data;
      try {
        data = JSON.parse(jsonEl.value);
      } catch (e) {
        setOut('<span class="err"><span class="err-tag">Invalid JSON</span>' + esc(e.message) + "</span>", "");
        foot.innerHTML = '<span class="grow"></span><span>JSON parse error</span>';
        return;
      }
      var src = exprEl.value.trim();
      if (!src) {
        setOut('<span class="empty">Type a JSONata expression above.</span>', "");
        foot.innerHTML = '<span class="grow"></span>';
        return;
      }
      var t0 = performance.now();
      var expr;
      try {
        expr = jsonata(src);
      } catch (e) {
        setOut('<span class="err"><span class="err-tag">Syntax error</span>' + esc(e.message) + "</span>", "");
        foot.innerHTML = '<span class="grow"></span><span>error at position ' + (e.position != null ? e.position : "?") + "</span>";
        return;
      }
      expr.evaluate(data).then(function (res) {
        var ms = (performance.now() - t0).toFixed(1);
        setOut(renderValue(res, 0), "");
        foot.innerHTML = '<span class="grow"></span><span class="ok">● evaluated</span><span>' + ms + " ms</span>";
      }).catch(function (e) {
        setOut('<span class="err"><span class="err-tag">Evaluation error</span>' + esc(e.message) + "</span>", "");
        foot.innerHTML = '<span class="grow"></span><span>runtime error</span>';
      });
    }

    var timer;
    function schedule() { clearTimeout(timer); timer = setTimeout(run, 220); }

    jsonEl.addEventListener("input", schedule);
    exprEl.addEventListener("input", function () { markActive(null); schedule(); });

    function markActive(key) {
      var chips = chipRow.querySelectorAll(".chip");
      chips.forEach(function (c) { c.setAttribute("aria-pressed", String(c.dataset.key === key)); });
    }

    PRESETS.forEach(function (p) {
      var b = document.createElement("button");
      b.className = "chip";
      b.type = "button";
      b.textContent = p.label;
      b.dataset.key = p.key;
      b.setAttribute("aria-pressed", "false");
      b.addEventListener("click", function () {
        exprEl.value = p.expr;
        markActive(p.key);
        run();
      });
      chipRow.appendChild(b);
    });
    markActive(PRESETS[0].key);

    run();
  }

  /* ---- highlight static code snippets ---- */
  function highlightSnippets() {
    document.querySelectorAll("[data-expr]").forEach(function (el) {
      el.innerHTML = highlightExpr(el.textContent.trim());
    });
    document.querySelectorAll("[data-json]").forEach(function (el) {
      try {
        el.innerHTML = renderValue(JSON.parse(el.textContent), 0);
      } catch (e) { /* leave as-is if not valid JSON */ }
    });
  }

  /* ---- nav border on scroll ---- */
  function initNav() {
    var nav = document.querySelector(".nav");
    if (!nav) return;
    var onScroll = function () { nav.setAttribute("data-scrolled", String(window.scrollY > 8)); };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }

  /* ---- reveal on scroll ---- */
  function initReveal() {
    var els = document.querySelectorAll(".rise");
    if (!("IntersectionObserver" in window)) {
      els.forEach(function (el) { el.classList.add("in"); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
      });
    }, { threshold: 0.12 });
    els.forEach(function (el) { io.observe(el); });
  }

  function init() {
    highlightSnippets();
    initPlayground();
    initNav();
    initReveal();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
