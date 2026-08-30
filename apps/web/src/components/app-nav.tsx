"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * The app shell navigation. Mobile-first: a wrapping row of links under
 * the header instead of a sidebar.
 */
const LINKS = [
  { href: "/app", label: "Kitchen" },
  { href: "/app/recipes", label: "Recipes" },
  { href: "/app/plan", label: "Plan" },
  { href: "/app/shopping-list", label: "Shopping" },
  { href: "/app/log", label: "Log" },
];

export function AppNav() {
  const pathname = usePathname();
  return (
    <nav className="app-nav" aria-label="App">
      {LINKS.map((link) => {
        const active = link.href === "/app" ? pathname === "/app" : pathname.startsWith(link.href);
        return (
          <Link key={link.href} href={link.href} className={active ? "active" : undefined}>
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}
