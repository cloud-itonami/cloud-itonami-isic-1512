# Operator Quickstart — Luggage, Handbag & Saddlery/Harness Manufacturing

Shortest path from clone to a verified local dry-run for **ISIC 1512** (`cloud-itonami-isic-1512`).

## Prerequisites

- Clojure 1.12+ (`clojure --version`)
- Java 17+
- Git

No invented metrics; this is a governed OSS blueprint, not a hosted SaaS demo.

## 1. Clone

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-1512.git
cd cloud-itonami-isic-1512
```

## 2. Run tests

```bash
clojure -M:test
```

Expect green if maturity is `implemented`. Fix failures before operating.

## 3. Open the product face

```bash
open docs/index.html   # or: python3 -m http.server -d docs 8080
```

Publish: enable GitHub Pages on `main` `/docs`, or any static host.

## 4. Where the Governor sits

- Blueprint governor key: `luggage-governor`
- Source path: `src/luggage/governor.cljc`
- The actor is a real compiled `langgraph-clj` StateGraph:
  `src/luggage/operation.cljc`'s `operation/build`, pattern
  `intake → advise → govern → decide -+-> commit / request-approval →
  commit / hold` (itonami actor / ADR-2607011000), with a genuine
  checkpointed `interrupt-before #{:request-approval}` for human-in-the-
  loop approval, and every commit/hold decision landing in the
  append-only ledger (`src/luggage/store.cljc`'s `ledger`/
  `append-ledger!`).

## 5. Claim / go-live

- Free claim funnel: https://itonami.cloud/isco-1212/
- Paid path docs: https://itonami.cloud/docs/go-live.md
- Blueprint: `blueprint.edn`

## Constraints

- Do not invent users/revenue numbers for marketing
- No force-push; keep AGPL headers
- Secrets stay out of this repo
