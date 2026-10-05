import Link from "next/link";

import { SplitPage } from "@/components/split-page";

/** The user guide, published from main to GitHub Pages (mkdocs.yml). */
const DOCS_URL = "https://noahhhx.github.io/mimos/";

/**
 * The public landing page. No client JavaScript, no authentication: it is
 * statically generated at build time (SEO-friendly by construction).
 */
export default function HomePage() {
  return (
    <SplitPage
      rail={
        <>
          <header className="page-header">
            <p className="eyebrow">A recipe companion for people who cook</p>
            <h1>Mimos</h1>
          </header>
          <p className="lede">
            A library of free recipes, a place to keep your own, and &ldquo;what am I cooking this
            week?&rdquo; turned into plans, shopping lists, and a picture of what you&apos;re actually
            eating.
          </p>
        </>
      }
    >
      <p className="cta">
        <Link href="/app" className="button">
          Open the app
        </Link>
        <Link href="/recipes" className="button secondary">
          Browse the library
        </Link>
        <a href={DOCS_URL} className="button secondary">
          Read the guide
        </a>
      </p>

      <div className="card">
        <h2>Self-hosting</h2>
        <p>
          Mimos runs the same everywhere: our cloud or your own hardware. Bring the whole stack up
          with Docker:
        </p>
        <pre>
          <code>git clone https://github.com/noahhhx/mimos.git{"\n"}cd mimos/deploy/docker{"\n"}docker compose up -d --wait</code>
        </pre>
        <p>
          To run it on a server, follow the <a href={`${DOCS_URL}self-hosting/`}>self-hosting guide</a>.
        </p>
      </div>
    </SplitPage>
  );
}
