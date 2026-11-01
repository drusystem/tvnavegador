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
