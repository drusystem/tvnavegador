(function () {
  var s = document.createElement("script");
  s.textContent = "(" + page.toString() + ")();";
  (document.documentElement || document.head).appendChild(s);
  s.remove();

  function page() {
    if (window.__tvnavScreen) return;
    window.__tvnavScreen = true;

    function arm(frame) {
      if (!frame || frame.tagName !== "IFRAME" || frame.dataset.tvnavFs) return;
      frame.dataset.tvnavFs = "1";
      frame.setAttribute("allowfullscreen", "true");
      frame.allowFullscreen = true;
      var allow = frame.getAttribute("allow") || "";
      if (allow.indexOf("fullscreen") < 0) {
        frame.setAttribute("allow", allow ? allow + "; fullscreen *" : "fullscreen *");
      }
    }

    function scan(root) {
      if (!root || !root.querySelectorAll) return;
      var list = root.querySelectorAll("iframe");
      for (var i = 0; i < list.length; i++) arm(list[i]);
    }

    scan(document);
    var root = document.documentElement;
    if (root && window.MutationObserver) {
      new MutationObserver(function (recs) {
        for (var i = 0; i < recs.length; i++) {
          var nodes = recs[i].addedNodes;
          for (var j = 0; j < nodes.length; j++) {
            var n = nodes[j];
            if (!n || n.nodeType !== 1) continue;
            if (n.tagName === "IFRAME") arm(n);
            else scan(n);
          }
        }
      }).observe(root, { childList: true, subtree: true });
    }

    function control(el) {
      if (el.closest("button, a, input, select, textarea, label")) return true;
      var cls = (typeof el.className === "string" ? el.className : "") + " " + (el.id || "");
      if (/control|setting|quality|volume|progress|menu|icon/i.test(cls)) return true;
      if (el.tagName === "VIDEO") return false;
      var r = el.getBoundingClientRect();
      return r.width < 96 && r.height < 96;
    }

    document.addEventListener("click", function (e) {
      if (e.button !== 0 || document.fullscreenElement) return;
      var t = e.target;
      if (!t || !t.closest || control(t)) return;
      var host = t.closest("video, .jwplayer, .dplayer, .artplayer, .plyr, .video-js");
      if (!host) return;
      var r = host.getBoundingClientRect();
      if (r.width < 200 || r.height < 112) return;
      var req = host.requestFullscreen || host.mozRequestFullScreen;
      if (!req) return;
      try { req.call(host); } catch (err) {}
    }, true);
  }
})();
