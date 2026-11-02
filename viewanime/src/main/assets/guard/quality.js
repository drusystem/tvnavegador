(function () {
  function label(el) {
    return (el.textContent || "").replace(/\s+/g, " ").trim().toLowerCase();
  }

  function level(text) {
    if (!text || text.length > 18) return 0;
    if (text.indexOf("1080") >= 0) return 1080;
    if (text.indexOf("720") >= 0) return 720;
    if (text.indexOf("480") >= 0) return 480;
    return 0;
  }

  function chosen(el) {
    var mark = String(el.className || "") + " " +
      (el.getAttribute("aria-pressed") || "") + " " +
      (el.getAttribute("aria-selected") || "");
    return /active|selected|current|true/i.test(mark);
  }

  function pick() {
    var nodes = document.querySelectorAll(
      "button, a, li, [role='button'], [class*='quality'], [class*='resolution'], [class*='settings-content-item']"
    );
    var option480 = null;
    var option720 = null;
    var playing480 = false;
    var has1080 = false;
    var hasAbove480 = false;
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var lv = level(label(el));
      if (!lv) continue;
      if (lv === 1080) has1080 = true;
      if (lv > 480) hasAbove480 = true;
      if (lv === 480 && chosen(el)) playing480 = true;
      if (el.dataset.tvnavPicked || chosen(el)) continue;
      if (lv === 480 && !option480) option480 = el;
      else if (lv === 720 && !option720) option720 = el;
    }
    if (playing480) return;
    var choice = null;
    if (option480 && hasAbove480) choice = option480;
    else if (option720 && has1080) choice = option720;
    if (!choice) return;
    choice.dataset.tvnavPicked = "1";
    choice.click();
  }

  pick();
  setTimeout(pick, 1500);
  setTimeout(pick, 4000);
  setTimeout(pick, 8000);
  setTimeout(pick, 14000);
})();
