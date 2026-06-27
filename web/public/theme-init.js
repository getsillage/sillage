(function () {
  var key = "sillage-theme";
  var root = document.documentElement;

  function getMode() {
    try {
      var value = window.localStorage.getItem(key);
      return value === "light" || value === "dark" ? value : "system";
    } catch (_) {
      return "system";
    }
  }

  function prefersDark() {
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  function apply(mode) {
    var dark = mode === "dark" || (mode === "system" && prefersDark());
    root.classList.toggle("dark", dark);
    root.dataset.theme = mode;
    root.style.colorScheme = dark ? "dark" : "light";
  }

  apply(getMode());
})();
