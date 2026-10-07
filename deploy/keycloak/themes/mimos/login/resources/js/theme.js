// The app sends its theme on the authorization request (ADR-0021). Only the
// first page has that URL, so the choice is stored for the pages after it.
(function () {
  var root = document.documentElement;
  var sent = new URLSearchParams(location.search).get("mimos_theme");
  var theme = sent === "light" || sent === "dark" ? sent : null;
  try {
    if (theme) localStorage.setItem("theme", theme);
    else theme = localStorage.getItem("theme");
  } catch (e) {}
  if (theme === "light" || theme === "dark") root.dataset.theme = theme;

  document.addEventListener("DOMContentLoaded", function () {
    var header = document.getElementById("kc-header");
    if (!header) return;
    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "theme-toggle";
    toggle.setAttribute("aria-label", "Toggle light and dark mode");
    var icon = document.createElement("span");
    icon.setAttribute("aria-hidden", "true");
    toggle.appendChild(icon);
    toggle.addEventListener("click", function () {
      var next = root.dataset.theme === "dark" ? "light" : "dark";
      root.dataset.theme = next;
      try {
        localStorage.setItem("theme", next);
      } catch (e) {}
    });
    header.appendChild(toggle);
  });
})();
