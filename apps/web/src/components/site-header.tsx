"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { ProfileMenu } from "@/components/profile-menu";

/**
 * The site-wide header: brand, the two top-level areas, the theme toggle
 * (its click handler lives in the root layout's pre-paint script) and,
 * when signed in, the profile menu.
 */
export function SiteHeader() {
  const pathname = usePathname();
  const current = (prefix: string) => (pathname.startsWith(prefix) ? "page" : undefined);
  return (
    <header className="site-header">
      <div className="wrap site-header-inner">
        <Link href="/" className="brand">
          <span className="brand-mark" aria-hidden="true" />
          Mimos
        </Link>
        <nav className="site-nav" aria-label="Site">
          <Link href="/recipes" aria-current={current("/recipes")}>
            Library
          </Link>
          <Link href="/app" aria-current={current("/app")}>
            Kitchen
          </Link>
          <div className="site-tools">
            <button className="theme-toggle" type="button" data-theme-toggle aria-label="Toggle light and dark mode">
              <span aria-hidden="true" />
            </button>
            <ProfileMenu />
          </div>
        </nav>
      </div>
    </header>
  );
}
