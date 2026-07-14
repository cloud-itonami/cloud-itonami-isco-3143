(ns forestry-technician.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [forestry-technician.actor :as actor]
            [forestry-technician.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-technician! st {:technician-id "tech-1" :name "Alice Forest" :verified? true})
    (store/register-forest! st {:forest-id "forest-1" :technician-id "tech-1" :location "North Stand" :registered? true})
    st))

(deftest commits-a-clean-low-risk-assessment
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:technician-id "tech-1" :forest-id "forest-1" :op :log-site-assessment :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "forest-1"))))))

(deftest holds-on-unregistered-technician-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:technician-id "no-such-tech" :forest-id "forest-1" :op :log-site-assessment :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "forest-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval-pest-flag
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; pest disease flag always escalates (governor invariant)
        request {:technician-id "tech-1" :forest-id "forest-1" :op :flag-pest-disease-risk :stake :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "forest-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "forest-1")))))))

(deftest interrupts-schedule-site-visit
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:technician-id "tech-1" :forest-id "forest-1" :op :schedule-site-visit :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "forest-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "forest-1")))))))
