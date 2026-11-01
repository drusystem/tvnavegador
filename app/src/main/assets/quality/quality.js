(function () {
  function label(el) {
    return (el.textContent || "").replace(/\s+/g, " ").trim().toLowerCase();
  }

  function pick() {
    var buttons = document.querySelectorAll("button, a");
    var has1080 = false;
    var prefer720 = null;
    var prefer480 = null;
    for (var i = 0; i < buttons.length; i++) {
      var text = label(buttons[i]);
      if (text === "1080p") has1080 = true;
      else if (text === "720p" && !buttons[i].dataset.tvnavPicked) prefer720 = buttons[i];
      else if (text === "480p" && !prefer480 && !buttons[i].dataset.tvnavPicked) prefer480 = buttons[i];
    }
    var choice = prefer720 || prefer480;
    if (!has1080 || !choice) return;
    choice.dataset.tvnavPicked = "1";
    choice.click();
  }

  pick();
  setTimeout(pick, 2500);
})();
