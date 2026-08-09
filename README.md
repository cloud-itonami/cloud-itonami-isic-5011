# cloud-itonami-5011

Open Business Blueprint for **ISIC Rev.5 5011**: sea and coastal
passenger water transport (scheduled ferry and coastal passenger
vessel operations).

This repository designs a forkable OSS business for community
passenger ferry operations: vessel-certificate scope management,
robotics-assisted hull/vessel inspection and maintenance, and booking/
reconciliation records — run by a qualified operator so a ferry
operator keeps its own certification and vessel-maintenance history
instead of renting a closed maritime-operations platform.

## Scope note: passenger water transport, not marine cargo

`cloud-itonami-isic-5020` (a marine-cargo/tanker actor) already covers
freight/cargo shipping. This repository is deliberately scoped to the
SEPARATE business of scheduled PASSENGER ferry service -- a distinct,
independently regulated activity (passenger-vessel safety certification
is a materially different regime from cargo-vessel certification in
every jurisdiction checked).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (hull inspection,
vessel maintenance, safety-equipment testing) operate under an actor
that proposes actions and an independent **Maritime Safety Governor**
that gates them. The governor never dispatches a vessel itself;
`:high`/`:safety-critical` actions (any sailing outside the vessel's
own verified passenger-certificate scope, any maintenance release that
has not passed inspection) require human sign-off.

## Core Contract

```text
intake + identity + vessel-certificate scope + booking
        |
        v
Ferry Operations Advisor -> Maritime Safety Governor -> certificate record, dispatch, reconciliation record, or human approval
        |
        v
robot actions (gated) + sailing/maintenance record + reconciliation record + audit ledger
```

No automated advice can dispatch a sailing the governor refuses,
approve a maintenance release outside its verified inspection scope, or
publish a reconciliation record without governor approval and audit
evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `5011`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/logistics`](https://github.com/kotoba-lang/logistics) — booking, transit, delivery/reconciliation contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## R0 implementation: FerryOperationsAdvisor ⊣ Maritime Safety Governor

The R0 slice implements a passenger-ferry OPERATIONS COORDINATION
actor -- NOT a maritime-safety authority and NOT vessel control. Every
proposal this actor makes is `:effect :propose`; it never issues a real
mutation or a sail-clearance decision. Four governed ops, closed
allowlist:

- `:log-voyage-record` -- sailing / passenger-manifest / incident data
  logging. The only op that may ever auto-commit (no capital/safety
  risk).
- `:schedule-sailing-operation` -- sailing-schedule / berth coordination
  proposal, HARD-gated on the sailing's own vessel/voyage certification
  record and vessel IMO-number structural validity, and citing an
  official jurisdiction spec-basis (`ferry.facts`). Always needs human
  approval.
- `:flag-maritime-safety-concern` -- surfaces a seaworthiness / weather
  / passenger-overcrowding concern. ALWAYS escalates to human sign-off,
  at every phase, by two independent layers (`ferry.governor`'s
  high-stakes gate AND `ferry.phase`'s phase table never puts it in any
  `:auto` set) -- this is a REPORT, never a resolution or an override.
- `:coordinate-maintenance` -- vessel maintenance coordination proposal.
  Always needs human approval.

Any proposal that reads as directly finalizing a sail-clearance /
weather-hold / maritime-safety override is a HARD, PERMANENT block
(`:scope-exclusion-sail-clearance`) -- this actor structurally never
holds that authority. See
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full design record, including the fleet-wide scope-exclusion self-trip
bug class this build fixes by construction.

## Run

```bash
clojure -M:dev:run          # walk the happy paths + every HARD-hold scenario through the actor
clojure -M:dev:render-html  # build-time operator console via REAL actor (flagship item 2)
clojure -M:dev:test         # governor contract · phase invariants · store parity · registry conformance · facts coverage · scope-exclusion regression
clojure -M:lint             # clj-kondo (errors fail; CI mirrors this)
```

## Layout

| File | Role |
|---|---|
| `src/ferry/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db` via `kotoba-lang/langchain-store`) + append-only audit ledger + schedule/concern/maintenance history |
| `src/ferry/registry.cljc` | Sailing-schedule / concern-filing / maintenance-coordination draft records, plus the self-contained `imo-number-valid?` structural check (honest reapplication of `cloud-itonami-isic-5020`'s SOLAS / IMO A.600(15) scheme) |
| `src/ferry/facts.cljc` | Per-jurisdiction passenger-vessel safety-certification catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/ferry/ferryadvisor.cljc` | **FerryOperationsAdvisor** -- `mock-advisor` ‖ `llm-advisor`; voyage-record / schedule / concern / maintenance proposals |
| `src/ferry/governor.cljc` | **Maritime Safety Governor** -- 7 HARD checks (op-not-allowed · effect-not-propose · scope-exclusion-sail-clearance (permanent) · certification-incomplete · imo-number-invalid · no-spec-basis · already-scheduled) + 1 high-stakes gate + 1 soft confidence gate |
| `src/ferry/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted schedule → supervised (only voyage-record logging ever auto; concern-flagging always human) |
| `src/ferry/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/ferry/sim.cljc` | demo driver |
| `src/ferry/render_html.clj` | build-time `docs/samples/operator-console.html` via the real actor (flagship item 2) |
| `test/ferry/*_test.cljc` | governor contract · phase invariants · store parity · registry conformance · facts coverage · scope-exclusion self-trip regression |

## Maturity

`:implemented` -- `FerryOperationsAdvisor` + `Maritime Safety Governor`
run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold (this repo pre-existed
this R0 build with only boilerplate docs + `blueprint.edn`), following
the SAME governed-actor architecture as the other actors across this
fleet. See [`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md)
for the history and design.

## License

AGPL-3.0-or-later.
