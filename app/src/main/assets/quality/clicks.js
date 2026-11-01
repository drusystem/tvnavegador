(function () {
  var s = document.createElement("script");
  s.textContent = "(" + page.toString() + ")();";
  (document.documentElement || document.head).appendChild(s);
  s.remove();

  function page() {
    if (window.__tvnavClicks) return;
    window.__tvnavClicks = true;

    var css = document.createElement("style");
    css.textContent =
      "html ins.604c7625,html [id^='adangle-'],html #adangle-pop-iframe-container," +
      "html iframe[src*='agl003.com'],html iframe[src*='acscdn.com']{" +
      "pointer-events:none !important;visibility:hidden !important;}" +
      "html body *{animation-duration:.01ms !important;animation-iteration-count:1 !important;" +
      "transition-duration:.01ms !important;scroll-behavior:auto !important;}";
    document.documentElement.appendChild(css);

    document.addEventListener("beforescriptexecute", function (e) {
      var src = (e.target && (e.target.src || e.target.getAttribute("src"))) || "";
      if (/agl003\.com|acscdn\.com|unboltsecole\.com|kami\.jkdesa\.com|googletagmanager\.com|google-analytics\.com|firebasejs|fontawesome|\/pusher\/|laravel-echo/i.test(src)) {
        e.preventDefault();
        e.stopPropagation();
      }
    }, true);

    function mark(img) {
      if (!img || img.tagName !== "IMG" || img.dataset.tvnav) return;
      img.dataset.tvnav = "1";
      if (!img.getAttribute("loading")) img.setAttribute("loading", "lazy");
      img.decoding = "async";
    }
    new MutationObserver(function (recs) {
      for (var i = 0; i < recs.length; i++) {
        var nodes = recs[i].addedNodes;
        for (var j = 0; j < nodes.length; j++) {
          var n = nodes[j];
          if (!n || !n.tagName) continue;
          if (n.tagName === "IMG") mark(n);
          else if (n.getElementsByTagName && n.tagName !== "SCRIPT" && n.tagName !== "STYLE") {
            var imgs = n.getElementsByTagName("img");
            for (var k = 0; k < imgs.length; k++) mark(imgs[k]);
          }
        }
      }
    }).observe(document.documentElement, { childList: true, subtree: true });

    function calm() {
      if (!window.jQuery || !window.jQuery.fn || !window.jQuery.fn.owlCarousel) return;
      try { window.jQuery(".owl-carousel").trigger("stop.owl.autoplay"); } catch (e) {}
    }
    document.addEventListener("DOMContentLoaded", calm);
    window.addEventListener("load", function () {
      calm();
      setTimeout(calm, 1200);
    });

    function site(host) {
      var p = String(host || "").toLowerCase().split(".");
      if (p[0] === "www") p.shift();
      return p.length >= 2 ? p.slice(-2).join(".") : p.join(".");
    }

    function go(e, a) {
      var raw = a.getAttribute("href");
      if (!raw || raw.charAt(0) === "#" || /^javascript:/i.test(raw)) return false;
      var url;
      try { url = new URL(a.href); } catch (err) { return false; }
      if (url.protocol !== "http:" && url.protocol !== "https:") return false;
      if (site(url.hostname) !== site(location.hostname)) return false;
      if (url.href === location.href) return false;
      e.preventDefault();
      e.stopImmediatePropagation();
      location.assign(url.href);
      return true;
    }

    function layer(el) {
      if (!el || el === document.body || el === document.documentElement) return false;
      var tag = el.tagName;
      if (tag === "IFRAME" || tag === "INS") return true;
      var id = (el.id || "") + " " + (typeof el.className === "string" ? el.className : "");
      if (/adangle|popunder|aclib|acl-/i.test(id)) return true;
      var st;
      try { st = getComputedStyle(el); } catch (err) { return false; }
      if (st.position !== "fixed" && st.position !== "absolute") return false;
      var r = el.getBoundingClientRect();
      return r.width > window.innerWidth * 0.85 && r.height > window.innerHeight * 0.85;
    }

    function under(e) {
      var list;
      try { list = document.elementsFromPoint(e.clientX, e.clientY); } catch (err) { return; }
      if (!list) return;
      for (var i = 0; i < list.length; i++) {
        var node = list[i];
        var a = node.closest ? node.closest("a[href]") : null;
        if (a && go(e, a)) return;
      }
    }

    document.addEventListener("click", function (e) {
      if (e.button !== 0) return;
      var t = e.target;
      if (!t || !t.closest) return;
      if (t.closest("button, input, select, textarea, label")) return;
      var a = t.closest("a[href]");
      if (a && go(e, a)) return;
      if ((a && site(a.hostname) !== site(location.hostname)) || layer(t)) under(e);
    }, true);
  }
})();
