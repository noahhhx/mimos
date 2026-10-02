{ pkgs, ... }:

# Nix dev shell for working on Mimos without installing toolchains on the
# host. Versions track CI (.github/workflows/ci.yml): JDK 21, Node 22,
# Python 3.13 for MkDocs. Docker itself is NOT provided here — Testcontainers
# and compose need a host daemon (e.g. `virtualisation.docker.enable = true`
# on NixOS); only the CLI is on the PATH.
{
  # Backend: Java 21 + the checked-in Maven wrapper (./mvnw), which pins and
  # downloads its own Maven version — no Maven package needed.
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk21;
  };

  # Frontend, API client/SDK generation, and plugins (npm workspaces).
  # package.json engines + country-week need Node >= 22.18.
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_22;
    npm.enable = true;
  };

  # Docs: MkDocs pinned via docs/requirements.txt, same as CI.
  languages.python = {
    enable = true;
    package = pkgs.python313;
    venv = {
      enable = true;
      requirements = ./docs/requirements.txt;
    };
  };

  packages = [
    pkgs.docker-client
    pkgs.docker-compose
    pkgs.git
    # Agent harness: ad-hoc inspection of JSON evidence in .harness/runs/.
    pkgs.jq
    # Exploratory browsing for agents (registered in .mcp.json); its wrapper
    # points at the same nixpkgs browsers as below.
    pkgs.playwright-mcp
    # Rebuilding the committed web fonts (apps/web/src/fonts/README.md):
    # pyftsubset subsets the upstream TTFs, woff2_compress packs them. The
    # product build only uses the committed files.
    pkgs.python313Packages.fonttools
    pkgs.woff2
  ];

  # Browsers for `harness ui` (Playwright scenarios) come from nixpkgs:
  # Playwright's downloaded builds don't run on NixOS, and agent tooling comes
  # from devenv anyway. The npm @playwright/test pin in tools/harness must equal
  # pkgs.playwright-driver.version, or the browser revisions won't match —
  # enterTest checks it; bump both together when devenv.lock moves.
  env = {
    PLAYWRIGHT_BROWSERS_PATH = "${pkgs.playwright-driver.browsers}";
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1";
    PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS = "true";
  };

  # Agent harness (docs/harness/): deploy, drive, observe, and debug the local
  # stack. Agents call it as `devenv shell -- harness <cmd>`.
  scripts.harness = {
    exec = ''node "$DEVENV_ROOT/tools/harness/src/cli.ts" "$@"'';
    description = "Mimos agent harness — harness --help";
  };

  enterShell = ''
    # stderr, so `devenv shell -- <cmd>` leaves the command's stdout clean
    # (e.g. TOKEN=$(devenv shell -- harness token)).
    echo "mimos dev shell: $(java -version 2>&1 | head -n1), node $(node --version)" >&2
    if ! docker info >/dev/null 2>&1; then
      echo "warning: no Docker daemon reachable — ./mvnw verify (Testcontainers) and compose need one" >&2
    fi
  '';

  enterTest = ''
    java -version 2>&1 | grep -q '"21'
    node -e 'const [a,b]=process.versions.node.split(".").map(Number); process.exit(a>22||(a===22&&b>=18)?0:1)'
    mkdocs --version
    ./mvnw -v
    harness --version
    pinned=$(jq -r '.devDependencies["@playwright/test"]' tools/harness/package.json)
    if [ "$pinned" != "${pkgs.playwright-driver.version}" ]; then
      echo "tools/harness pins @playwright/test $pinned but nixpkgs' playwright-driver is ${pkgs.playwright-driver.version} — pin it exactly to match" >&2
      exit 1
    fi
  '';
}
