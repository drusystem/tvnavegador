(function () {
  function goFullscreen() {
    if (document.fullscreenElement) return;
    var nodes = document.querySelectorAll("video, .jwplayer, .dplayer, .artplayer, .plyr, .video-js");
    var best = null;
    var bestArea = 0;
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var r = node.getBoundingClientRect();
      var area = r.width * r.height;
      if (area > bestArea && r.width >= 160 && r.height >= 90) {
        best = node;
        bestArea = area;
      }
    }
    if (!best) return;
    var req = best.requestFullscreen || best.mozRequestFullScreen;
    if (!req) return;
    try { req.call(best); } catch (e) {}
  }

  function hush(on) {
    var imgs = document.images;
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      if (on) {
        if (img.dataset.tvnavCv) continue;
        var r = img.getBoundingClientRect();
        if (r.height < 2) continue;
        if (r.bottom > 0 && r.top < window.innerHeight) continue;
        img.dataset.tvnavCv = "1";
        img.style.containIntrinsicSize = Math.round(r.width) + "px " + Math.round(r.height) + "px";
        img.style.contentVisibility = "auto";
      } else if (img.dataset.tvnavCv) {
        img.style.contentVisibility = "";
        img.style.containIntrinsicSize = "";
        delete img.dataset.tvnavCv;
      }
    }
  }

  try {
    browser.runtime.onMessage.addListener(function (msg) {
      if (!msg || !msg.action) return;
      if (msg.action === "fullscreen") goFullscreen();
      else if (msg.action === "quiet") hush(!!msg.on);
    });
  } catch (e) {}

  var pausedAnims = [];

  function holdAnims(on) {
    if (!document.getAnimations) return;
    if (on) {
      var list = document.getAnimations();
      for (var i = 0; i < list.length; i++) {
        if (list[i].playState !== "running") continue;
        try { list[i].pause(); pausedAnims.push(list[i]); } catch (err) {}
      }
      return;
    }
    for (var j = 0; j < pausedAnims.length; j++) {
      try { pausedAnims[j].play(); } catch (err) {}
    }
    pausedAnims = [];
  }

  function videoPlaying() {
    var vids = document.querySelectorAll("video");
    for (var i = 0; i < vids.length; i++) {
      if (!vids[i].paused && !vids[i].ended) return true;
    }
    return false;
  }

  document.addEventListener("play", function (e) {
    if (!e.target || e.target.tagName !== "VIDEO") return;
    var box = e.target.getBoundingClientRect();
    if (box.width >= 2 && box.height >= 2 && (box.width < 200 || box.height < 112)) return;
    hush(true);
    holdAnims(true);
    setTimeout(function () { hush(true); holdAnims(true); }, 1200);
    try { browser.runtime.sendMessage({ action: "playing", on: true }); } catch (err) {}
  }, true);

  function release(e) {
    if (!e.target || e.target.tagName !== "VIDEO") return;
    if (videoPlaying()) return;
    hush(false);
    holdAnims(false);
    try { browser.runtime.sendMessage({ action: "playing", on: false }); } catch (err) {}
  }
  document.addEventListener("pause", release, true);
  document.addEventListener("ended", release, true);

  var s = document.createElement("script");
  s.textContent = "(" + page.toString() + ")();";
  (document.documentElement || document.head).appendChild(s);
  s.remove();

  function page() {
    if (window.__tvnavScreen) return;
    window.__tvnavScreen = true;

    function heavy(type) {
      return /av01|hev1|hvc1|vp09|vp9/i.test(String(type || ""));
    }
    var origCan = HTMLMediaElement.prototype.canPlayType;
    HTMLMediaElement.prototype.canPlayType = function (type) {
      if (heavy(type)) return "";
      return origCan.apply(this, arguments);
    };
    if (window.MediaSource && MediaSource.isTypeSupported) {
      var origMs = MediaSource.isTypeSupported.bind(MediaSource);
      MediaSource.isTypeSupported = function (type) {
        if (heavy(type)) return false;
        return origMs(type);
      };
    }

    function heightOf(text) {
      var m = String(text || "").match(/(1080|720|480)/);
      return m ? parseInt(m[1], 10) : 0;
    }

    function choose(items, current) {
      var i480 = -1;
      var i720 = -1;
      var curH = 0;
      var hasAbove480 = false;
      var has1080 = false;
      for (var i = 0; i < items.length; i++) {
        var h = items[i].height;
        if (items[i].index === current) curH = h;
        if (h > 480) hasAbove480 = true;
        if (h === 1080) has1080 = true;
        if (h === 480 && i480 < 0) i480 = items[i].index;
        if (h === 720 && i720 < 0) i720 = items[i].index;
      }
      if (curH === 480) return -1;
      if (i480 >= 0 && hasAbove480) return i480;
      if (i720 >= 0 && has1080 && curH !== 720) return i720;
      return -1;
    }

    function tuneHls(hls) {
      if (!hls || !hls.levels || !hls.levels.length || hls.__tvnavLocked) return;
      var items = [];
      for (var i = 0; i < hls.levels.length; i++) {
        var h = hls.levels[i].height || heightOf(hls.levels[i].name || hls.levels[i].label);
        items.push({ index: i, height: h });
      }
      var choice = choose(items, hls.currentLevel);
      if (choice < 0) {
        if (hls.currentLevel >= 0) hls.__tvnavLocked = true;
        return;
      }
      try { hls.currentLevel = choice; hls.__tvnavLocked = true; } catch (err) {}
    }

    function armHls(C) {
      if (!C || !C.prototype || C.prototype.__tvnav || !C.prototype.attachMedia) return;
      var orig = C.prototype.attachMedia;
      C.prototype.attachMedia = function () {
        var ret = orig.apply(this, arguments);
        var self = this;
        function apply() { tuneHls(self); }
        try {
          if (C.Events && C.Events.MANIFEST_PARSED) this.on(C.Events.MANIFEST_PARSED, apply);
        } catch (err) {}
        setTimeout(apply, 400);
        setTimeout(apply, 2000);
        return ret;
      };
      C.prototype.__tvnav = true;
    }

    function tuneJw(p) {
      if (!p || !p.getQualityLevels || p.__tvnavLocked) return;
      var levels;
      try { levels = p.getQualityLevels(); } catch (err) { return; }
      if (!levels || !levels.length) return;
      var items = [];
      var current = -1;
      try { current = p.getCurrentQuality(); } catch (err) {}
      for (var i = 0; i < levels.length; i++) {
        items.push({ index: i, height: heightOf(levels[i].label) });
      }
      var choice = choose(items, current);
      if (choice < 0) {
        if (current >= 0) p.__tvnavLocked = true;
        return;
      }
      try { p.setCurrentQuality(choice); p.__tvnavLocked = true; } catch (err) {}
    }

    var ticks = 0;
    var timer = setInterval(function () {
      if (++ticks > 16) { clearInterval(timer); return; }
      try { if (window.Hls) armHls(window.Hls); } catch (err) {}
      try {
        if (!window.jwplayer) return;
        var nodes = document.querySelectorAll(".jwplayer");
        if (!nodes.length) {
          var first = window.jwplayer();
          if (first && first.getContainer && first.getContainer()) tuneJw(first);
        }
        for (var i = 0; i < nodes.length; i++) {
          tuneJw(window.jwplayer(nodes[i].id || i));
        }
      } catch (err) {}
    }, 1500);

    try {
      var hlsSeen = window.Hls;
      Object.defineProperty(window, "Hls", {
        configurable: true,
        get: function () { return hlsSeen; },
        set: function (C) { hlsSeen = C; try { armHls(C); } catch (err) {} }
      });
    } catch (err) {}

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
