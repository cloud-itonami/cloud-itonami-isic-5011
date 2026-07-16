(ns ferry.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:flag-maritime-safety-concern` must NEVER be a member of
  any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [ferry.phase :as phase]))

(deftest flag-concern-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a maritime-safety-concern filing"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :flag-maritime-safety-concern))
          (str "phase " n " must not auto-commit :flag-maritime-safety-concern")))))

(deftest schedule-and-maintenance-never-auto-at-any-phase
  (testing "structural invariant: only :log-voyage-record is ever auto-eligible in this domain"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :schedule-sailing-operation))
          (str "phase " n " must not auto-commit :schedule-sailing-operation"))
      (is (not (contains? auto :coordinate-maintenance))
          (str "phase " n " must not auto-commit :coordinate-maintenance")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":log-voyage-record carries no direct capital/safety risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:log-voyage-record} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :log-voyage-record} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :schedule-sailing-operation} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-maritime-safety-concern} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :coordinate-maintenance} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :log-voyage-record} :commit))))
  (is (= :hold (:disposition (phase/gate 1 {:op :flag-maritime-safety-concern} :commit)))
      "flag-maritime-safety-concern is not writable until phase 3"))
