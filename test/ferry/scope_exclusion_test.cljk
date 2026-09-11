(ns ferry.scope-exclusion-test
  "Dedicated regression test for the known self-tripping bug class this
  actor family has repeatedly hit: a governor's scope-exclusion term
  list phrased as a bare noun accidentally matches inside the mock
  advisor's own DEFAULT rationale/disclaimer text for a legitimate,
  allowed proposal, causing the actor to self-block on its own happy
  path. `ferry.governor/scope-exclusion-phrases` is deliberately phrased
  as full finalization/execution ACTIONS (not bare nouns like 'safety'
  or 'clearance') for exactly this reason -- see that def's docstring.

  This test asserts the DEFAULT mock advisor's proposals, across EVERY
  op in the closed allowlist and EVERY demo sailing (including the
  fault-reported / weather-hold-active one, whose rationale explicitly
  DISCUSSES those conditions), never trip
  `ferry.governor/scope-exclusion-violations`. If a future edit to
  `ferry.ferryadvisor`'s rationale/disclaimer wording ever reintroduces
  one of `ferry.governor/scope-exclusion-phrases` verbatim (even inside
  a negation, e.g. 'does not override the weather-hold' still literally
  contains 'override the weather-hold'), this test fails."
  (:require [clojure.test :refer [deftest is testing]]
            [ferry.ferryadvisor :as ferryadvisor]
            [ferry.governor :as governor]
            [ferry.store :as store]))

(def ^:private ops-and-requests
  "One representative request per closed-allowlist op. `:log-voyage-record`
  additionally needs a :patch."
  [[:log-voyage-record {:patch {:id "sail-1" :passenger-manifest-count 100}}]
   [:schedule-sailing-operation {}]
   [:flag-maritime-safety-concern {}]
   [:coordinate-maintenance {}]])

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "through the full governor/check (op-not-allowed and effect-not-propose must also stay clean on every default proposal, but the focus of this regression is :scope-exclusion-sail-clearance specifically)"
    (let [db (store/seed-db)]
      (doseq [sailing-id (map :id (store/all-sailings db))
              [op extra] ops-and-requests]
        (testing (str op " / " sailing-id)
          (let [request (merge {:op op :subject sailing-id} extra)
                proposal (ferryadvisor/infer db request)
                verdict (governor/check request {} proposal db)]
            (is (not (some #{:scope-exclusion-sail-clearance} (map :rule (:violations verdict))))
                (str "default advisor proposal for " op " on " sailing-id
                     " must never self-trip scope-exclusion. rationale="
                     (pr-str (:rationale proposal))
                     " summary=" (pr-str (:summary proposal))
                     " value=" (pr-str (:value proposal))))))))))

(deftest default-mock-advisor-proposals-never-trip-op-or-effect-defense-checks-either
  (testing "same demo sweep, asserting the two defense-in-depth checks also stay clean on every default proposal"
    (let [db (store/seed-db)]
      (doseq [sailing-id (map :id (store/all-sailings db))
              [op extra] ops-and-requests]
        (let [request (merge {:op op :subject sailing-id} extra)
              proposal (ferryadvisor/infer db request)
              verdict (governor/check request {} proposal db)]
          (is (not (some #{:op-not-allowed :effect-not-propose} (map :rule (:violations verdict))))
              (str op " / " sailing-id " verdict=" (pr-str verdict))))))))
