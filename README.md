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

## License

AGPL-3.0-or-later.
