import type { Metadata } from "next";

import { SiteHeader } from "@/components/site-header";

import "./globals.css";

export const metadata: Metadata = {
  title: "Mimos",
  description:
    "A recipe companion for people who cook: a free recipe library, personal recipes, meal planning, shopping lists, and a picture of what you're actually eating.",
};

/**
 * Applies the saved theme before first paint (no flash) and handles the
 * header's theme toggle through a delegated click listener.
 */
const THEME_SCRIPT = `(function () {
  var root = document.documentElement;
  try { var saved = localStorage.getItem("theme"); if (saved) root.dataset.theme = saved; } catch (e) {}
  document.addEventListener("click", function (e) {
    if (!(e.target instanceof Element) || !e.target.closest("[data-theme-toggle]")) return;
    var dark = root.dataset.theme
      ? root.dataset.theme === "dark"
      : matchMedia("(prefers-color-scheme: dark)").matches;
    var next = dark ? "light" : "dark";
    root.dataset.theme = next;
    try { localStorage.setItem("theme", next); } catch (e) {}
  });
})();`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // The theme script sets data-theme before hydration.
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body>
        <SiteHeader />
        <main className="wrap">{children}</main>
      </body>
    </html>
  );
}
