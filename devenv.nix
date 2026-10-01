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
  ];

  enterShell = ''
    echo "mimos dev shell: $(java -version 2>&1 | head -n1), node $(node --version)"
    if ! docker info >/dev/null 2>&1; then
      echo "warning: no Docker daemon reachable — ./mvnw verify (Testcontainers) and compose need one" >&2
    fi
  '';

  enterTest = ''
    java -version 2>&1 | grep -q '"21'
    node -e 'const [a,b]=process.versions.node.split(".").map(Number); process.exit(a>22||(a===22&&b>=18)?0:1)'
    mkdocs --version
    ./mvnw -v
  '';
}
