# ADR 0011: SpotBugs EI_EXPOSE_REP2 exclusion for dependency-injection constructors

- Status: Accepted (human decision, 2026-10-07)
- Date: 2026-10-07
- Gate affected: G3 (SpotBugs + FindSecBugs, threshold Medium), design §9.2
- Build step: 7 (design §7.5)

## Context
From build step 7 on, SpotBugs reported `EI_EXPOSE_REP2` ("may expose internal representation by
storing an externally mutable object") on every constructor that stores an injected Spring bean
whose type looks mutable to SpotBugs' heuristic (methods named `put*`, `insert*`, `clear*`, ...):
Caffeine `Cache` beans, `LinkCache`, `LinkRepository`, `LinkService`, `SecureRandom`,
`MeterRegistry`. These are singletons wired by Spring through constructor injection, the only
injection style this design allows. Storing the shared collaborator is the intent, not a leak of
internal state. In steps 5 and 6 the findings could be avoided by depending on interfaces or
immutable wrappers, but caches, a CSPRNG and a meter registry are mutable by design, and every
later step hits the same pattern.

## Options considered
1. Narrow exclusion filter: only `EI_EXPOSE_REP2`, only in constructors, only for application
   classes in `api`, `service`, `infra`, `repository` and `config`. **Chosen.**
2. Per-class `@SuppressFBWarnings`: needs the `spotbugs-annotations` dependency (outside §7.4)
   and adds noise to every bean.
3. Code changes that dodge the heuristic (storing method references, renaming methods): obscures
   the code and only games the analyser.

## Decision
`config/spotbugs/exclude.xml`, wired through `excludeFilterFile` in `pom.xml`:

- Bug pattern `EI_EXPOSE_REP2` only.
- `<Method name="<init>"/>` only (constructors).
- Classes matching `com.example.urlshortener.(api|service|infra|repository|config).*` only.

The threshold stays Medium. All other patterns, including `EI_EXPOSE_REP` (getters returning
internal state) and `EI_EXPOSE_REP2` in non-constructor methods, still fail the build.

## Consequences
- A real constructor that stores a caller-supplied mutable object (not a bean) in these packages
  would no longer be reported. Mitigation: domain values are Java records with defensive copies
  (`List.copyOf`, `Map.copyOf`), and this is checked in code review.
- The exclusion must not be broadened without human approval (§10.5).
