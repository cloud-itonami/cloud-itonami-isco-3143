(ns forestry-technician.store
  "SSoT for the ISCO-08 3143 forestry technician actor.
  Store is a protocol injected into the `forestry-technician.actor` StateGraph — `MemStore`
  is the default, deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or governor (itonami
  actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    technician — a registered forestry technician performing field assessments
                 (:technician-id, :name, :verified?)
    forest     — a registered forest stand under management
                 (:forest-id, :technician-id, :location, :registered?)
    record     — a committed technical record under a forest (assessment,
                 test result, risk flag, scheduled visit) — written ONLY via
                 commit-record!, never mutated in place
    ledger     — an append-only audit trail of every proposal/verdict/
                 disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (technician-by-id [s technician-id])
  (forest-by-id [s forest-id])
  (records-of [s forest-id])
  (ledger [s])
  (register-technician! [s technician])
  (register-forest! [s forest])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (technician-by-id [_ technician-id] (get-in @a [:technicians technician-id]))
  (forest-by-id [_ forest-id] (get-in @a [:forests forest-id]))
  (records-of [_ forest-id] (filter #(= forest-id (:forest-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-technician! [s technician]
    (swap! a assoc-in [:technicians (:technician-id technician)] technician) s)
  (register-forest! [s forest]
    (swap! a assoc-in [:forests (:forest-id forest)] forest) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:technicians {} :forests {} :records [] :ledger []} seed)))))
