(ns ferry.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean sailing through
  voyage-record logging -> sailing-schedule coordination (escalate/
  approve/commit) -> maritime-safety-concern filing (ALWAYS escalates/
  approve/commit) -> maintenance coordination (escalate/approve/commit),
  a low-confidence-but-governor-clean schedule proposal (fault-reported
  + weather-hold-active, still escalates/approve/commit -- proving the
  advisor's own rationale never self-trips the scope-exclusion check
  even when describing an unresolved fault/hold), then shows HARD-hold
  scenarios: a jurisdiction with no spec-basis, an invalid IMO number,
  an unverified/unregistered certification record (on schedule, on
  concern-flagging, and on maintenance coordination), and a double
  schedule.

  Like every sibling actor's checks, this actor's checks
  (`imo-number-valid?`, plus the direct certification/spec-basis/
  already-scheduled checks) are evaluated directly at
  `:schedule-sailing-operation` rather than via a separate screening op
  -- a real scheduling decision validates the IMO number, the
  certification record and the spec-basis at the point of the act
  itself, not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one sailing per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline this fleet
  establishes."
  (:require [langgraph.graph :as g]
            [ferry.store :as store]
            [ferry.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :ferry-operations-dispatcher :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== log-voyage-record sail-1 (auto-commits) ==")
    (println (exec-op actor "t1" {:op :log-voyage-record :subject "sail-1"
                                  :patch {:id "sail-1" :passenger-manifest-count 118}} operator))

    (println "== schedule-sailing-operation sail-1 (escalates -- human approves) ==")
    (let [r (exec-op actor "t2" {:op :schedule-sailing-operation :subject "sail-1"} operator)]
      (println r)
      (println "-- human ferry-operations dispatcher approves --")
      (println (approve! actor "t2")))

    (println "== flag-maritime-safety-concern sail-1 (ALWAYS escalates -- human approves) ==")
    (let [r (exec-op actor "t3" {:op :flag-maritime-safety-concern :subject "sail-1"} operator)]
      (println r)
      (println "-- human ferry-operations dispatcher approves the filing --")
      (println (approve! actor "t3")))

    (println "== coordinate-maintenance sail-1 (escalates -- human approves) ==")
    (let [r (exec-op actor "t4" {:op :coordinate-maintenance :subject "sail-1"} operator)]
      (println r)
      (println "-- human ferry-operations dispatcher approves --")
      (println (approve! actor "t4")))

    (println "== schedule-sailing-operation sail-6 (fault-reported + weather-hold-active -- low confidence, still escalates/approve/commit; rationale never self-trips scope-exclusion) ==")
    (let [r (exec-op actor "t5" {:op :schedule-sailing-operation :subject "sail-6"} operator)]
      (println r)
      (println "-- human ferry-operations dispatcher reviews and approves --")
      (println (approve! actor "t5")))

    (println "== schedule-sailing-operation sail-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :schedule-sailing-operation :subject "sail-2"} operator))

    (println "== schedule-sailing-operation sail-3 (invalid IMO number -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :schedule-sailing-operation :subject "sail-3"} operator))

    (println "== schedule-sailing-operation sail-4 (certification incomplete -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :schedule-sailing-operation :subject "sail-4"} operator))

    (println "== flag-maritime-safety-concern sail-4 (certification incomplete -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :flag-maritime-safety-concern :subject "sail-4"} operator))

    (println "== coordinate-maintenance sail-4 (certification incomplete -> HARD hold) ==")
    (println (exec-op actor "t10" {:op :coordinate-maintenance :subject "sail-4"} operator))

    (println "== schedule-sailing-operation sail-5 (already scheduled -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :schedule-sailing-operation :subject "sail-5"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft sailing-schedule records ==")
    (doseq [r (store/schedule-history db)] (println r))

    (println "== draft maritime-safety-concern records ==")
    (doseq [r (store/concern-history db)] (println r))

    (println "== draft maintenance-coordination records ==")
    (doseq [r (store/maintenance-history db)] (println r))))
