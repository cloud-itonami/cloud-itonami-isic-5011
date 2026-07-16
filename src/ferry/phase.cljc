(ns ferry.phase
  "Phase 0->3 staged rollout for the passenger-ferry operations-
  coordination actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- voyage-record logging allowed, every
                                 write needs human approval.
    Phase 2  assisted-schedule -- adds sailing-schedule coordination
                                 writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:log-voyage-record` (no capital / safety
                                 risk, pure data logging) may auto-commit.
                                 `:schedule-sailing-operation` and
                                 `:coordinate-maintenance` become
                                 writable but NEVER auto-commit at any
                                 phase; `:flag-maritime-safety-concern`
                                 is deliberately absent from every
                                 phase's `:writes` set until phase 3 AND
                                 is NEVER a member of ANY phase's `:auto`
                                 set, including phase 3.

  `:flag-maritime-safety-concern` is deliberately ABSENT from every
  phase's `:auto` set, including phase 3 -- a permanent structural fact,
  not a rollout milestone still to come. Surfacing a seaworthiness /
  weather / passenger-overcrowding concern is always a human sign-off
  act -- see README `Actuation`. `ferry.governor`'s `:flag-maritime-
  safety-concern` high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this. Like every prior
  sibling's phase 3 `:auto` set, this domain has only ONE member
  (`:log-voyage-record`) -- no separate no-capital-risk 'file' lifecycle
  distinct from the sailing itself."
  (:require [ferry.governor :as governor]))

(def read-ops  #{})
(def write-ops governor/allowed-ops)

;; NOTE the invariant: `:flag-maritime-safety-concern` is a member of
;; `write-ops` (governor-gated like any write, once phase 3 makes it
;; writable) but is NEVER a member of any phase's `:auto` set below. Do
;; not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"          :writes #{}                                                              :auto #{}}
   1 {:label "assisted-intake"    :writes #{:log-voyage-record}                                             :auto #{}}
   2 {:label "assisted-schedule"  :writes #{:log-voyage-record :schedule-sailing-operation}                 :auto #{}}
   3 {:label "supervised-auto"    :writes write-ops
      :auto #{:log-voyage-record}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:flag-maritime-safety-concern` is never auto-eligible at any phase,
    so it always escalates once the governor clears it (or holds if the
    governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Maritime Safety Governor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
