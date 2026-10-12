# syntax=docker/dockerfile:1

# A fake intervals.icu for the harness (deploy/docker/compose.fakes.yml),
# never part of the product stack. Zero dependencies: it runs straight from
# TypeScript source on Node's native type stripping, so the image needs
# nothing but its source. The harness gate typechecks and tests it.
FROM node:22-alpine

RUN addgroup --system --gid 1001 mimos \
 && adduser --system --uid 1001 --ingroup mimos --home /app mimos

WORKDIR /app
ENV PORT=8080

COPY --chown=mimos:mimos tools/harness/fakes/intervals-icu/src ./src

USER mimos
EXPOSE 8080
CMD ["node", "src/server.ts"]
