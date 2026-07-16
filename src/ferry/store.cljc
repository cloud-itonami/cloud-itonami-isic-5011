(ns ferry.store
  "SSoT for the passenger-ferry operations-coordination actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/ferry/store_contract_test.cljc), which is the whole point: the
  actor, the Maritime Safety Governor and the audit ledger never know
  which SSoT they run on.

  The schema, the EDN-blob codec (enc/dec*) and the sailing entity
  map<->tx<->pull are the shared `kotoba-lang/langchain-store` machinery
  (ADR-2607141600) -- the seam this fleet's stores now share instead of
  hand-rolling the identical `enc`/`dec*` two-liner and schema map per
  repo (skill `build-actor`).

  The ledger stays append-only on every backend: 'which sailing was
  logged, scheduled, flagged for a maritime-safety concern, or had
  maintenance coordinated, on what jurisdictional basis, approved by
  whom' is always a query over an immutable log -- the audit trail a
  regulator, a passenger, or an operator trusting this actor needs."
  (:require [ferry.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (sailing [s id])
  (all-sailings [s])
  (ledger [s])
  (schedule-history [s] "the append-only sailing-schedule history (ferry.registry drafts)")
  (concern-history [s] "the append-only maritime-safety-concern filing history")
  (maintenance-history [s] "the append-only maintenance-coordination history")
  (next-schedule-sequence [s jurisdiction] "next schedule-number sequence for a jurisdiction")
  (next-concern-sequence [s] "next global concern-number sequence")
  (next-maintenance-sequence [s] "next global maintenance-number sequence")
  (sailing-already-scheduled? [s sailing-id] "has a sailing-schedule already been committed for this sailing?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-sailings [s sailings] "replace/seed the sailing directory (map id->sailing)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained sailing set covering the governor's own
  checks, so the actor + tests run offline. Each violation sailing
  isolates exactly ONE failure mode (the rest stay clean), following the
  'exercise the failure mode directly, never only via a happy-path
  actuation' discipline every sibling governor's demo data establishes.

  `:vessel-imo` carries the SOLAS / IMO resolution A.600(15) seven-digit
  ship number `ferry.registry/imo-number-valid?` validates (9074729 is
  structurally valid; 9074728 deliberately fails the check digit, the
  same pair `cloud-itonami-isic-5020`'s demo data uses -- the SAME real
  scheme, honestly reapplied to a passenger vessel). `:certification-
  verified?` + `:certification-record-id` are the vessel/voyage
  certification record this actor's HARD invariant requires to be
  independently verified/registered before any coordination op beyond
  routine voyage-record logging."
  []
  {:sailings
   {"sail-1" {:id "sail-1" :vessel-name "Akita Maru" :vessel-imo "9074729"
              :route "Akita-Oga" :jurisdiction "JPN"
              :passenger-manifest-count 120
              :certification-verified? true :certification-record-id "JPN-PSC-000123"
              :seaworthiness-fault-reported? false :weather-hold-active? false
              :scheduled-departure "2026-08-01T09:00:00+09:00"
              :scheduled? false :maintenance-coordinated? false :status :intake}
    "sail-2" {:id "sail-2" :vessel-name "Atlantis Star" :vessel-imo "9074729"
              :route "Atlantis-Yokohama" :jurisdiction "ATL"
              :passenger-manifest-count 80
              :certification-verified? true :certification-record-id "ATL-PSC-000001"
              :seaworthiness-fault-reported? false :weather-hold-active? false
              :scheduled-departure "2026-08-02T09:00:00+09:00"
              :scheduled? false :maintenance-coordinated? false :status :intake}
    "sail-3" {:id "sail-3" :vessel-name "Akita Maru II" :vessel-imo "9074728"
              :route "Akita-Oga" :jurisdiction "JPN"
              :passenger-manifest-count 60
              :certification-verified? true :certification-record-id "JPN-PSC-000456"
              :seaworthiness-fault-reported? false :weather-hold-active? false
              :scheduled-departure "2026-08-03T09:00:00+09:00"
              :scheduled? false :maintenance-coordinated? false :status :intake}
    "sail-4" {:id "sail-4" :vessel-name "Akita Maru III" :vessel-imo "9074729"
              :route "Akita-Oga" :jurisdiction "JPN"
              :passenger-manifest-count 40
              :certification-verified? false :certification-record-id nil
              :seaworthiness-fault-reported? false :weather-hold-active? false
              :scheduled-departure "2026-08-04T09:00:00+09:00"
              :scheduled? false :maintenance-coordinated? false :status :intake}
    "sail-5" {:id "sail-5" :vessel-name "Akita Maru IV" :vessel-imo "9074729"
              :route "Akita-Oga" :jurisdiction "JPN"
              :passenger-manifest-count 90
              :certification-verified? true :certification-record-id "JPN-PSC-000789"
              :seaworthiness-fault-reported? false :weather-hold-active? false
              :scheduled-departure "2026-08-05T09:00:00+09:00"
              :scheduled? true :maintenance-coordinated? false :status :intake}
    "sail-6" {:id "sail-6" :vessel-name "Akita Maru V" :vessel-imo "9074729"
              :route "Akita-Oga" :jurisdiction "JPN"
              :passenger-manifest-count 100
              :certification-verified? true :certification-record-id "JPN-PSC-000999"
              :seaworthiness-fault-reported? true :weather-hold-active? true
              :scheduled-departure "2026-08-06T09:00:00+09:00"
              :scheduled? false :maintenance-coordinated? false :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- schedule-sailing! [s sailing-id]
  (let [sl (sailing s sailing-id)
        seq-n (next-schedule-sequence s (:jurisdiction sl))
        result (registry/register-schedule-record sailing-id (:jurisdiction sl) seq-n)]
    {:result   result
     :sl-patch {:scheduled? true :schedule-number (get result "schedule_number")}}))

(defn- flag-concern! [s sailing-id]
  (let [seq-n (next-concern-sequence s)
        result (registry/register-concern-record sailing-id seq-n)]
    {:result result}))

(defn- coordinate-maintenance! [s sailing-id]
  (let [seq-n (next-maintenance-sequence s)
        result (registry/register-maintenance-record sailing-id seq-n)]
    {:result   result
     :sl-patch {:maintenance-coordinated? true}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (sailing [_ id] (get-in @a [:sailings id]))
  (all-sailings [_] (sort-by :id (vals (:sailings @a))))
  (ledger [_] (:ledger @a))
  (schedule-history [_] (:schedules @a))
  (concern-history [_] (:concerns @a))
  (maintenance-history [_] (:maintenances @a))
  (next-schedule-sequence [_ jurisdiction] (get-in @a [:schedule-sequences jurisdiction] 0))
  (next-concern-sequence [_] (get @a :concern-sequence 0))
  (next-maintenance-sequence [_] (get @a :maintenance-sequence 0))
  (sailing-already-scheduled? [_ sailing-id] (boolean (get-in @a [:sailings sailing-id :scheduled?])))
  (commit-record! [s {:keys [op path value payload]}]
    (case op
      :log-voyage-record
      (swap! a update-in [:sailings (:id value)] merge value)

      :schedule-sailing-operation
      (let [sailing-id (first path)
            {:keys [result sl-patch]} (schedule-sailing! s sailing-id)
            jurisdiction (:jurisdiction (sailing s sailing-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:schedule-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sailings sailing-id] merge sl-patch)
                       (update :schedules registry/append result))))
        result)

      :flag-maritime-safety-concern
      (let [sailing-id (first path)
            {:keys [result]} (flag-concern! s sailing-id)]
        (swap! a (fn [state]
                   (-> state
                       (update :concern-sequence (fnil inc 0))
                       (update :concerns registry/append result))))
        result)

      :coordinate-maintenance
      (let [sailing-id (first path)
            {:keys [result sl-patch]} (coordinate-maintenance! s sailing-id)]
        (swap! a (fn [state]
                   (-> state
                       (update :maintenance-sequence (fnil inc 0))
                       (update-in [:sailings sailing-id] merge sl-patch)
                       (update :maintenances registry/append result))))
        result)

      (throw (ex-info "unrecognized op for commit-record!" {:op op :path path :value value :payload payload})))
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sailings [s sailings] (when (seq sailings) (swap! a assoc :sailings sailings)) s))

(defn seed-db
  "A MemStore seeded with the demo sailing set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :ledger [] :schedules [] :concerns [] :maintenances []
                           :schedule-sequences {} :concern-sequence 0 :maintenance-sequence 0))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------
;;
;; Schema, the EDN-blob codec (enc/dec*) and the sailing entity
;; map<->tx<->pull are the shared `kotoba-lang/langchain-store` machinery
;; (ADR-2607141600) -- this store is a field-spec entity-store adopter
;; (the same pattern `cloud-itonami-isic-6511`'s `underwriting.store`
;; establishes as the reference adopter). The ledger/schedule/concern/
;; maintenance/sequence attrs (custom query shapes) keep their own
;; wiring below, still using the shared enc/dec*.

(def ^:private schema
  (ls/identity-schema [:sailing/id :ledger/seq :schedule/seq :concern/seq
                       :maintenance/seq :schedule-sequence/jurisdiction]))

(defn- enc [v] (ls/enc v))
(defn- dec* [s] (ls/dec* s))

(def ^:private sailing-spec
  {:id {:attr :sailing/id}
   :vessel-name {:attr :sailing/vessel-name}
   :vessel-imo {:attr :sailing/vessel-imo}
   :route {:attr :sailing/route}
   :jurisdiction {:attr :sailing/jurisdiction}
   :passenger-manifest-count {:attr :sailing/passenger-manifest-count}
   :certification-verified? {:attr :sailing/certification-verified? :coerce boolean}
   :certification-record-id {:attr :sailing/certification-record-id}
   :seaworthiness-fault-reported? {:attr :sailing/seaworthiness-fault-reported? :coerce boolean}
   :weather-hold-active? {:attr :sailing/weather-hold-active? :coerce boolean}
   :scheduled-departure {:attr :sailing/scheduled-departure}
   :scheduled? {:attr :sailing/scheduled? :coerce boolean}
   :schedule-number {:attr :sailing/schedule-number}
   :maintenance-coordinated? {:attr :sailing/maintenance-coordinated? :coerce boolean}
   :status {:attr :sailing/status}})

(defn- sailing->tx [m] (ls/map->tx sailing-spec m))
(def ^:private sailing-pull (ls/pull-pattern sailing-spec))
(defn- pull->sailing [m] (ls/pull->map sailing-spec :id m))

(defrecord DatomicStore [conn]
  Store
  (sailing [_ id]
    (pull->sailing (d/pull (d/db conn) sailing-pull [:sailing/id id])))
  (all-sailings [_]
    (->> (d/q '[:find [?id ...] :where [?e :sailing/id ?id]] (d/db conn))
         (map #(pull->sailing (d/pull (d/db conn) sailing-pull [:sailing/id %])))
         (sort-by :id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (schedule-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :schedule/seq ?s] [?e :schedule/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (concern-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :concern/seq ?s] [?e :concern/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (maintenance-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :maintenance/seq ?s] [?e :maintenance/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-schedule-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :schedule-sequence/jurisdiction ?j] [?e :schedule-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-concern-sequence [s] (count (concern-history s)))
  (next-maintenance-sequence [s] (count (maintenance-history s)))
  (sailing-already-scheduled? [s sailing-id]
    (boolean (:scheduled? (sailing s sailing-id))))
  (commit-record! [s {:keys [op path value payload]}]
    (case op
      :log-voyage-record
      (d/transact! conn [(sailing->tx value)])

      :schedule-sailing-operation
      (let [sailing-id (first path)
            {:keys [result sl-patch]} (schedule-sailing! s sailing-id)
            jurisdiction (:jurisdiction (sailing s sailing-id))
            next-n (inc (next-schedule-sequence s jurisdiction))]
        (d/transact! conn
                     [(sailing->tx (assoc sl-patch :id sailing-id))
                      {:schedule-sequence/jurisdiction jurisdiction :schedule-sequence/next next-n}
                      {:schedule/seq (count (schedule-history s)) :schedule/record (enc (get result "record"))}])
        result)

      :flag-maritime-safety-concern
      (let [sailing-id (first path)
            {:keys [result]} (flag-concern! s sailing-id)]
        (d/transact! conn
                     [{:concern/seq (count (concern-history s)) :concern/record (enc (get result "record"))}])
        result)

      :coordinate-maintenance
      (let [sailing-id (first path)
            {:keys [result sl-patch]} (coordinate-maintenance! s sailing-id)]
        (d/transact! conn
                     [(sailing->tx (assoc sl-patch :id sailing-id))
                      {:maintenance/seq (count (maintenance-history s)) :maintenance/record (enc (get result "record"))}])
        result)

      (throw (ex-info "unrecognized op for commit-record!" {:op op :path path :value value :payload payload})))
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-sailings [s sailings]
    (when (seq sailings) (d/transact! conn (mapv sailing->tx (vals sailings)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:sailings ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [sailings]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-sailings s sailings))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo sailing set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
