# Business Model: Community Passenger Ferry Operations

## Classification
- Repository: `cloud-itonami-5011`
- ISIC Rev.5: `5011` — sea and coastal passenger water transport
- Social impact: maritime safety, island connectivity, passenger
  rights

## Customer
- independent/regional ferry operators needing an auditable vessel-
  certification and operations platform
- island and coastal communities needing verifiable scheduled-service
  records
- regulators needing verifiable vessel-certificate and maintenance
  records
- programs that cannot accept closed, unauditable maritime-operations
  platforms

## Offer
- passenger vessel-certificate scope management
- robotics-assisted hull/vessel inspection and maintenance
- booking and sailing-dispatch records
- reconciliation and disclosure records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per vessel/route
- support retainer with SLA
- hull/vessel inspection robot integration and maintenance

## Trust Controls
- a robot action the governor refuses is never dispatched
- safety-critical actions (sailing outside verified passenger-
  certificate scope, maintenance release without inspection) require
  human sign-off
- a sailing cannot be dispatched outside its verified certificate
  scope
- reconciliation records require verified evidence
- sensitive passenger and operations data stays outside Git
