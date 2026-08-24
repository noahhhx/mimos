import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: "Mimos",
  description:
    "A recipe companion for people who cook: a free recipe library, personal recipes, meal planning, shopping lists, and a picture of what you're actually eating.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <main>{children}</main>
      </body>
    </html>
  );
}
