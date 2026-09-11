(ns ferry.registry-test
  (:require [clojure.test :refer [deftest is]]
            [ferry.registry :as r]))

;; ----------------------------- IMO-number check-digit validation -----------------------------

(deftest imo-valid-numbers-pass
  (is (r/imo-number-valid? "9074729") "valid IMO (check digit 9) -> ok")
  (is (r/imo-number-valid? "9302578") "valid IMO (check digit 8) -> ok")
  (is (r/imo-number-valid? 9074729) "numeric input coerced to 7-digit string -> ok"))

(deftest imo-wrong-check-digit-fails
  (is (not (r/imo-number-valid? "9074728")) "wrong check digit (8 vs expected 9) -> invalid"))

(deftest imo-structural-failures
  (is (not (r/imo-number-valid? "907472")) "6 digits -> invalid")
  (is (not (r/imo-number-valid? "90747290")) "8 digits -> invalid")
  (is (not (r/imo-number-valid? "9074abc")) "non-numeric -> invalid")
  (is (not (r/imo-number-valid? nil)) "nil -> invalid")
  (is (not (r/imo-number-valid? "")) "empty -> invalid"))

;; ----------------------------- register-schedule-record -----------------------------

(deftest schedule-is-a-draft-not-a-real-clearance
  (let [result (r/register-schedule-record "sail-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest schedule-assigns-schedule-number
  (let [result (r/register-schedule-record "sail-1" "JPN" 7)]
    (is (= (get result "schedule_number") "JPN-SCHEDULE-000007"))
    (is (= (get-in result ["record" "sailing_id"]) "sail-1"))
    (is (= (get-in result ["record" "kind"]) "sailing-schedule-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest schedule-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-schedule-record "" "JPN" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-schedule-record "sail-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-schedule-record "sail-1" "JPN" -1))))

;; ----------------------------- register-concern-record -----------------------------

(deftest concern-is-a-draft-not-a-resolution
  (let [result (r/register-concern-record "sail-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))
    (is (= (get-in result ["record" "kind"]) "maritime-safety-concern-report"))))

(deftest concern-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-concern-record "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-concern-record "sail-1" -1))))

;; ----------------------------- register-maintenance-record -----------------------------

(deftest maintenance-is-a-draft-not-a-release
  (let [result (r/register-maintenance-record "sail-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))
    (is (= (get-in result ["record" "kind"]) "maintenance-coordination-draft"))))

(deftest maintenance-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance-record "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance-record "sail-1" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-schedule-record "sail-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-schedule-record "sail-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-SCHEDULE-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-SCHEDULE-000001" (get-in hist2 [1 "record_id"])))))
