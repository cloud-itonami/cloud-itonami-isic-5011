# ADR-0001: FerryOperationsAdvisor ⊣ Maritime Safety Governor architecture

## Status

Accepted. `cloud-itonami-isic-5011` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry. The repository
already existed as a published `:blueprint`-tier scaffold (boilerplate
docs + `blueprint.edn` only, no `deps.edn`/`src`/`test`) from an earlier
bulk-scaffolding pass; this ADR records filling it in, not creating it
fresh.

## Context

`cloud-itonami-isic-5011` publishes an OSS business blueprint for
community sea and coastal PASSENGER water transport (scheduled ferry
service): voyage-record logging, per-jurisdiction passenger-vessel
safety-certification-aware sailing-schedule coordination, maritime-
safety-concern filing, and vessel-maintenance coordination for a
community operator. Like every prior actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied
across the fleet.

This is a SEPARATE business from `cloud-itonami-isic-5020`'s marine-
cargo / tanker actor -- scheduled PASSENGER ferry service is a distinct,
independently regulated activity (passenger-vessel safety certification,
SOLAS Chapter III life-saving appliances and passenger-capacity limits,
is a materially different regime from cargo-vessel certification, SOLAS
Chapter II-2 inert-gas / fire protection, in every jurisdiction checked).
See README `Scope note`.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:maritime-safety-governor`, was already declared in the pre-existing
`blueprint.edn` from the original scaffolding pass.

## Decision

### Decision 1: operations-COORDINATION actor, not a maritime-safety authority

Unlike `cloud-itonami-isic-5020` (which drafts and, on human approval,
commits real dispatch/discharge draft records for a REAL laden voyage),
this actor is deliberately scoped narrower: it never proposes to
directly finalize a sail-clearance or maritime-safety override. Every
proposal's `:effect` is HARD-required to be the literal keyword
`:propose` (`ferry.governor/effect-not-propose-violations`) -- there is
no domain-specific mutation-effect keyword (contrast `cloud-itonami-
isic-5020`'s `:vessel/mark-dispatched` / `:vessel/mark-discharged`).
Commit-record dispatch in `ferry.store` therefore switches on the
request's `:op` rather than the proposal's `:effect` (documented
explicitly in `ferry.operation`'s docstring), since `:effect` carries no
discriminating information in this domain by design.

### Decision 2: closed op-allowlist, enforced independently by the governor

The four governed ops (`:log-voyage-record`, `:schedule-sailing-
operation`, `:flag-maritime-safety-concern`, `:coordinate-maintenance`)
are enforced as a closed allowlist at TWO independent points: `ferry.
ferryadvisor/infer`'s own `case` dispatch (which only knows how to draft
these four), AND `ferry.governor/op-not-allowed-violations` (which HARD-
blocks any request whose `:op` falls outside `ferry.governor/allowed-
ops`, independent of whatever the advisor itself would ever route to).
The governor-level check exists so a future/malicious advisor
implementation, or a request built for a different actor entirely,
cannot reach `:commit` by constructing an out-of-allowlist `:op`.

### Decision 3: the scope-exclusion self-trip bug class, fixed by construction

Multiple sibling actors in this fleet independently discovered and fixed
the SAME bug class: a governor's scope-exclusion term list phrased as a
bare noun (e.g. "safety", "clearance", "weather-hold") accidentally
matches inside the actor's own DEFAULT advisor rationale/disclaimer
text, because an honest disclaimer explaining what the actor does NOT do
("this does not clear the vessel to sail") necessarily uses the bare
noun it is disclaiming -- causing the actor to self-block on its own
happy path.

This build's fix: `ferry.governor/scope-exclusion-phrases` is a set of
full FINALIZATION/EXECUTION ACTIONS (verb + object, e.g. "finalize the
sail-clearance override", "override the weather-hold"), never a bare
noun. `ferry.ferryadvisor`'s own rationale/disclaimer text (e.g. for
`:schedule-sailing-operation` on a sailing with a reported fault or
active weather hold, and for `:flag-maritime-safety-concern`) is written
in DIFFERENT words than any of those phrases -- it attributes final
departure-clearance judgment to "the vessel master and the maritime-
safety authority" rather than restating what the actor itself does not
do, so no negation of the exact trigger phrase ever appears either.
`test/ferry/scope_exclusion_test.cljc` is a dedicated regression test
sweeping every op x every demo sailing (including the fault-reported +
weather-hold-active one) through the DEFAULT mock advisor and asserting
none of the resulting proposals trip `:scope-exclusion-sail-clearance`;
`test/ferry/governor_test.cljc` separately proves the check is not
vacuous (a hand-crafted proposal containing each phrase verbatim IS
hard-blocked).

### Decision 4: self-contained domain logic (no `kotoba-lang/maritime` to wrap)

Like `cloud-itonami-isic-5020`, this vertical has NO pre-existing
maritime capability library to delegate passenger-vessel safety
validation to. `ferry.registry/imo-number-valid?` honestly reapplies
`cloud-itonami-isic-5020`'s own reapplication of the SOLAS / IMO
resolution A.600(15) seven-digit check-digit scheme -- the scheme
applies to every SOLAS-class ship's IMO Ship Identification Number
(including passenger vessels), not only tankers, so reusing it here is
the SAME 'reuse a validated structural check' discipline, not a new
invention.

### Decision 5: `kotoba-lang/langchain-store` store seam (ADR-2607141600 adopter)

Unlike `cloud-itonami-isic-5020` (which predates ADR-2607141600 and
hand-rolls its own `enc`/`dec*` codec + schema/pull wiring), `ferry.
store`'s `DatomicStore` is a field-spec entity-store adopter of
`kotoba-lang/langchain-store` (the same pattern `cloud-itonami-isic-
6511`'s `underwriting.store` establishes as the reference adopter) --
this repo is a NEW store as of 2026-07, so skill `build-actor`'s
"do not hand-roll the enc/dec + schema/pull boilerplate" directive
applies to it directly.

### Decision 6: portable `.cljc` tests (cljs-first)

All source AND test files are `.cljc` (not `.clj`), including a
`ferry.portable-cljs-test-runner` + `:cljs` deps.edn alias, mirroring
`cloud-itonami-isic-6511`'s current-generation convention and this
workspace's CLAUDE.md runtime priority (`kotoba wasm > clojurewasm >
ClojureScript > nbb`, JVM/bb as last-resort compat) -- a deliberate
departure from `cloud-itonami-isic-5020`'s `.clj`-only test suite, which
predates that mandate.

### Decision 7: dedicated double-schedule guard, no double-guard on flag/maintain

`:scheduled?` is a dedicated boolean on the `sailing` record (never a
`:status` value), guarding `:schedule-sailing-operation` against
double-scheduling the same sailing -- the same discipline every prior
governor's guards establish. `:flag-maritime-safety-concern` and
`:coordinate-maintenance` deliberately have NO analogous double-guard:
concerns can recur (separate incidents) and maintenance can recur
(separate windows) over a vessel's life, unlike a one-time-per-sailing
schedule commitment.

### Decision 8: Phase 0->3, only `:log-voyage-record` ever auto-commits

`ferry.phase`'s phase table puts `:log-voyage-record` (no capital/safety
risk, pure data logging) in phase 3's `:auto` set as its ONLY member;
`:flag-maritime-safety-concern` is additionally kept OUT of every
phase's `:writes` set until phase 3, and is NEVER a member of ANY
phase's `:auto` set, including phase 3 -- a permanent structural fact.
`ferry.governor`'s `high-stakes` gate (`#{:flag-maritime-safety-
concern}`) enforces the same invariant independently: two layers agree
that a maritime-safety-concern filing is always a human call.
`:schedule-sailing-operation` and `:coordinate-maintenance` are likewise
never in any phase's `:auto` set (structurally, by simply never being
added), even though they are not in the governor's `high-stakes` set --
this actor's design keeps every write beyond routine logging on a human
approval path.

## Alternatives considered

- **Reusing `:effect` for domain-specific mutation-effect keywords**
  (matching `cloud-itonami-isic-5020`'s `:vessel/mark-dispatched` style).
  Rejected: this actor's design invariant is that it NEVER performs
  anything beyond a propose-only coordination note, and a constant,
  governor-checked `:effect :propose` makes that structurally explicit
  and independently verifiable, rather than merely conventional.
- **A single global scope-exclusion bare-noun blocklist** (e.g.
  `#{"safety" "clearance" "weather-hold"}`). Rejected: this is exactly
  the self-tripping bug class this fleet has repeatedly hit and fixed;
  full action-phrases are the fix (Decision 3).
- **Hand-rolling the DatomicStore `enc`/`dec*` + schema/pull wiring**
  (matching `cloud-itonami-isic-5020`'s pre-ADR-2607141600 style).
  Rejected for a NEW store: `kotoba-lang/langchain-store` is the current
  shared seam (Decision 5).

## Consequences

- Sea and coastal passenger water transport (ISIC 5011) actor on the
  same governed-actor architecture as the rest of the cloud-itonami
  fleet, filling in a pre-existing `:blueprint`-tier repo rather than
  scaffolding a new one.
- `MemStore` || `DatomicStore` parity is proven by
  `test/ferry/store_contract_test.cljc`.
- 46 tests / 272 assertions pass; lint is clean (0 errors, 0 warnings);
  the demo (`clojure -M:dev:run`) walks the happy paths plus every
  HARD-hold scenario (no spec-basis, invalid IMO, certification
  incomplete on all three non-logging ops, already scheduled) and a
  low-confidence-but-governor-clean escalation, end-to-end.
- `blueprint.edn` required no field-sync fixes (already correct from the
  original scaffolding pass) -- only the `:maturity` flip itself in the
  `kotoba-lang/industry` registry.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (current-
  generation governed-actor architecture + `langchain-store` reference
  adopter this build follows)
- `cloud-itonami-isic-5020/docs/adr/0001-architecture.md` (nearest
  transport-sector sibling; contrast: marine CARGO, not passenger, no
  `:effect :propose` constant, pre-`langchain-store`, `.clj` tests)
- 船舶安全法 (Ship Safety Act, 旅客船), 国土交通省 (MLIT) 海事局; SOLAS
  Chapter III (life-saving appliances) (Japan)
- Passenger Vessels, 46 C.F.R. Subchapter H/K; SOLAS Chapter III (US,
  U.S. Coast Guard)
- Merchant Shipping (Passenger Ship Construction) Regulations, SOLAS
  Chapter III (UK, Maritime and Coastguard Agency)
- Norwegian Ship Safety and Security Act; SOLAS (Norway, Norwegian
  Maritime Authority / Sjøfartsdirektoratet)
- IMO resolution A.600(15): IMO Ship Identification Number Scheme
  (7-digit check-digit scheme, weights 7, 6, 5, 4, 3, 2)
