try {
  const preference = localStorage.getItem("shortly-theme");
  document.documentElement.dataset.theme =
    preference === "light" || preference === "dark"
      ? preference
      : matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";
} catch {
  /* CSS follows the system when storage is unavailable. */
}
