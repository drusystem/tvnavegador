(function () {
  function textOf(el) {
    return (el.innerText || "").replace(/\s+/g, " ").trim().toLowerCase();
  }

  function exact(el, label) {
    return textOf(el) === label;
  }

  function targetFor(el) {
    if (el.tagName === "BUTTON" || el.tagName === "A") return el;
    if (el.getAttribute("role") === "button") return el;
    var parent = el.closest("button, a");
    if (!parent) return null;
    var label = textOf(parent);
    if (label === "720p" || label === "480p") return parent;
    return null;
  }

  function pick() {
    var nodes = document.querySelectorAll("button, a, [role='button'], span, div, li");
    var has1080 = false;
    var prefer720 = null;
    var prefer480 = null;
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (exact(el, "1080p")) has1080 = true;
      if (!exact(el, "720p") && !exact(el, "480p")) continue;
      var clickable = targetFor(el);
      if (!clickable || clickable.dataset.tvnavPicked) continue;
      if (exact(clickable, "720p") || exact(el, "720p")) prefer720 = clickable;
      else if (!prefer480) prefer480 = clickable;
    }
    var choice = prefer720 || prefer480;
    if (!has1080 || !choice) return;
    choice.dataset.tvnavPicked = "1";
    choice.click();
  }

  pick();
  setTimeout(pick, 2000);
  setTimeout(pick, 6000);
})();
