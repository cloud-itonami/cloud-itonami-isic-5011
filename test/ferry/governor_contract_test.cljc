(ns ferry.governor-contract-test
  "The governor contract as executable tests, run through the FULL
  `ferry.operation` langgraph StateGraph. The single invariant under
  test:

    FerryOperationsAdvisor never commits a coordination proposal the
    Maritime Safety Governor would reject, `:flag-maritime-safety-
    concern` NEVER auto-commits at any phase, `:log-voyage-record` (no
    capital/safety risk) MAY auto-commit when clean, and every decision
    (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [ferry.store :as store]
            [ferry.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :ferry-operations-dispatcher :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(deftest clean-log-voyage-record-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :log-voyage-record :subject "sail-1"
                   :patch {:id "sail-1" :passenger-manifest-count 118}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= 118 (:passenger-manifest-count (store/sailing db "sail-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest schedule-sailing-operation-always-needs-approval
  (testing "schedule-sailing-operation is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :schedule-sailing-operation :subject "sail-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:scheduled? (store/sailing db "sail-1"))))
        (is (= 1 (count (store/schedule-history db))))))))

(deftest flag-maritime-safety-concern-always-escalates-then-human-decides
  (testing "a clean, fully-certified sailing's concern filing still ALWAYS interrupts for human approval -- flag-maritime-safety-concern is never auto"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t3" {:op :flag-maritime-safety-concern :subject "sail-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, concern record filed"
        (let [r2 (approve! actor "t3")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (= 1 (count (store/concern-history db)))))))))

(deftest coordinate-maintenance-always-needs-approval
  (let [[db actor] (fresh)
        res (exec-op actor "t4" {:op :coordinate-maintenance :subject "sail-1"} operator)]
    (is (= :interrupted (:status res)))
    (let [r2 (approve! actor "t4")]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (true? (:maintenance-coordinated? (store/sailing db "sail-1"))))
      (is (= 1 (count (store/maintenance-history db)))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a schedule proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t5" {:op :schedule-sailing-operation :subject "sail-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (empty? (store/schedule-history db)) "no schedule record written"))))

(deftest imo-number-invalid-is-held-and-unoverridable
  (testing "an invalid IMO number (failed 7-digit check-digit) -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :schedule-sailing-operation :subject "sail-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:imo-number-invalid} (-> (store/ledger db) last :basis)))
      (is (empty? (store/schedule-history db))))))

(deftest certification-incomplete-is-held-on-schedule
  (testing "an unverified/unregistered certification record -> HOLD on schedule, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :schedule-sailing-operation :subject "sail-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:certification-incomplete} (-> (store/ledger db) last :basis)))
      (is (empty? (store/schedule-history db))))))

(deftest certification-incomplete-is-held-on-flag-concern
  (testing "an unverified/unregistered certification record -> HOLD on concern-flagging too"
    (let [[db actor] (fresh)
          res (exec-op actor "t8" {:op :flag-maritime-safety-concern :subject "sail-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:certification-incomplete} (-> (store/ledger db) last :basis)))
      (is (empty? (store/concern-history db))))))

(deftest certification-incomplete-is-held-on-coordinate-maintenance
  (testing "an unverified/unregistered certification record -> HOLD on maintenance coordination too"
    (let [[db actor] (fresh)
          res (exec-op actor "t9" {:op :coordinate-maintenance :subject "sail-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:certification-incomplete} (-> (store/ledger db) last :basis)))
      (is (empty? (store/maintenance-history db))))))

(deftest already-scheduled-is-held
  (testing "scheduling a sailing that is already scheduled -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t10" {:op :schedule-sailing-operation :subject "sail-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-scheduled} (-> (store/ledger db) last :basis)))
      (is (empty? (store/schedule-history db)) "no NEW schedule record written"))))

(deftest low-confidence-clean-schedule-still-escalates-and-can-commit
  (testing "fault-reported + weather-hold-active on an otherwise-clean, certified sailing -> low confidence -> ESCALATE (not HOLD); human may still approve"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :schedule-sailing-operation :subject "sail-6"} operator)]
      (is (= :interrupted (:status res)) "escalates for human review, not a hard hold")
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= 1 (count (store/schedule-history db))))))))

(deftest op-not-allowed-is-held
  (testing "a request whose op is outside the closed allowlist -> HOLD, independent of the advisor's own routing"
    (let [[db actor] (fresh)
          res (exec-op actor "t12" {:op :clear-vessel-to-sail :subject "sail-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:op-not-allowed} (-> (store/ledger db) last :basis))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :log-voyage-record :subject "sail-1"
                          :patch {:id "sail-1" :passenger-manifest-count 100}} operator)
      (exec-op actor "b" {:op :schedule-sailing-operation :subject "sail-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
