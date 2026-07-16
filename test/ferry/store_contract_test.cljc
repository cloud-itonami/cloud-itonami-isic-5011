(ns ferry.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [ferry.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/sailing s "sail-1"))))
      (is (= "9074729" (:vessel-imo (store/sailing s "sail-1"))))
      (is (true? (:certification-verified? (store/sailing s "sail-1"))))
      (is (= "ATL" (:jurisdiction (store/sailing s "sail-2"))))
      (is (= "9074728" (:vessel-imo (store/sailing s "sail-3"))) "sail-3 invalid IMO")
      (is (false? (:certification-verified? (store/sailing s "sail-4"))) "sail-4 certification unverified")
      (is (true? (:scheduled? (store/sailing s "sail-5"))) "sail-5 pre-scheduled")
      (is (true? (:seaworthiness-fault-reported? (store/sailing s "sail-6"))) "sail-6 fault reported")
      (is (true? (:weather-hold-active? (store/sailing s "sail-6"))) "sail-6 weather hold active")
      (is (false? (:scheduled? (store/sailing s "sail-1"))))
      (is (false? (:maintenance-coordinated? (store/sailing s "sail-1"))))
      (is (= ["sail-1" "sail-2" "sail-3" "sail-4" "sail-5" "sail-6"]
             (mapv :id (store/all-sailings s))))
      (is (= [] (store/ledger s)))
      (is (= [] (store/schedule-history s)))
      (is (= [] (store/concern-history s)))
      (is (= [] (store/maintenance-history s)))
      (is (zero? (store/next-schedule-sequence s "JPN")))
      (is (zero? (store/next-concern-sequence s)))
      (is (zero? (store/next-maintenance-sequence s)))
      (is (false? (store/sailing-already-scheduled? s "sail-1")))
      (is (true? (store/sailing-already-scheduled? s "sail-5"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:op :log-voyage-record
                                 :value {:id "sail-1" :passenger-manifest-count 999}})
        (is (= 999 (:passenger-manifest-count (store/sailing s "sail-1"))))
        (is (= "JPN" (:jurisdiction (store/sailing s "sail-1"))) "unrelated field preserved"))
      (testing "sailing-schedule drafts a record and advances the schedule sequence"
        (store/commit-record! s {:op :schedule-sailing-operation :path ["sail-1"]})
        (is (= "JPN-SCHEDULE-000000" (get (first (store/schedule-history s)) "record_id")))
        (is (= "sailing-schedule-draft" (get (first (store/schedule-history s)) "kind")))
        (is (true? (:scheduled? (store/sailing s "sail-1"))))
        (is (= 1 (count (store/schedule-history s))))
        (is (= 1 (store/next-schedule-sequence s "JPN")))
        (is (true? (store/sailing-already-scheduled? s "sail-1"))))
      (testing "maritime-safety-concern filing drafts a record and advances the concern sequence"
        (store/commit-record! s {:op :flag-maritime-safety-concern :path ["sail-1"]})
        (is (= "CONCERN-000000" (get (first (store/concern-history s)) "record_id")))
        (is (= "maritime-safety-concern-report" (get (first (store/concern-history s)) "kind")))
        (is (= 1 (count (store/concern-history s))))
        (is (= 1 (store/next-concern-sequence s))))
      (testing "maintenance coordination drafts a record and advances the maintenance sequence"
        (store/commit-record! s {:op :coordinate-maintenance :path ["sail-1"]})
        (is (= "MAINT-000000" (get (first (store/maintenance-history s)) "record_id")))
        (is (= "maintenance-coordination-draft" (get (first (store/maintenance-history s)) "kind")))
        (is (true? (:maintenance-coordinated? (store/sailing s "sail-1"))))
        (is (= 1 (count (store/maintenance-history s))))
        (is (= 1 (store/next-maintenance-sequence s))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/sailing s "nope")))
    (is (= [] (store/all-sailings s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/schedule-history s)))
    (is (= [] (store/concern-history s)))
    (is (= [] (store/maintenance-history s)))
    (is (zero? (store/next-schedule-sequence s "JPN")))
    (is (zero? (store/next-concern-sequence s)))
    (is (zero? (store/next-maintenance-sequence s)))
    (store/with-sailings s {"x" {:id "x" :vessel-name "Test Ferry" :vessel-imo "9074729"
                                 :route "A-B" :jurisdiction "JPN"
                                 :passenger-manifest-count 10
                                 :certification-verified? true :certification-record-id "JPN-PSC-X"
                                 :seaworthiness-fault-reported? false :weather-hold-active? false
                                 :scheduled-departure "2026-08-01T09:00:00+09:00"
                                 :scheduled? false :maintenance-coordinated? false :status :intake}})
    (is (= "9074729" (:vessel-imo (store/sailing s "x"))))))
