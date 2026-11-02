browser.webRequest.onBeforeRequest.addListener(
  function () {
    return { cancel: true };
  },
  {
    urls: [
      "*://*.agl003.com/*",
      "*://agl003.com/*",
      "*://*.acscdn.com/*",
      "*://acscdn.com/*",
      "*://*.unboltsecole.com/*",
      "*://kami.jkdesa.com/*",
      "*://fonts.googleapis.com/*",
      "*://fonts.gstatic.com/*",
      "*://use.fontawesome.com/*",
      "*://*.fontawesome.com/*",
      "*://*.googletagmanager.com/*",
      "*://*.google-analytics.com/*",
      "*://*.doubleclick.net/*",
      "*://*.googlesyndication.com/*",
      "*://connect.facebook.net/*",
      "*://www.gstatic.com/firebasejs/*",
      "*://cdn.jsdelivr.net/npm/@tabler/*",
      "*://cdnjs.cloudflare.com/ajax/libs/pusher/*",
      "*://cdnjs.cloudflare.com/ajax/libs/laravel-echo/*",
      "*://tinyurl.com/*",
      "*://*.tinyurl.com/*"
    ]
  },
  ["blocking"]
);

(function () {
  var tries = 0;

  function connect() {
    if (tries++ > 25) return;
    var port;
    try {
      port = browser.runtime.connectNative("tvnav");
    } catch (e) {
      setTimeout(connect, 500);
      return;
    }
    port.onDisconnect.addListener(function () {
      setTimeout(connect, 500);
    });
    port.onMessage.addListener(function (msg) {
      if (!msg || !msg.action) return;
      browser.runtime.sendMessage(msg).catch(function () {});
    });
  }

  connect();

  browser.runtime.onMessage.addListener(function (msg) {
    if (!msg || msg.action !== "playing") return;
    browser.runtime.sendMessage({ action: "quiet", on: !!msg.on }).catch(function () {});
  });
})();
