(ns forestry-technician.technician
  "ForestryTechnician — proposes a forest field action (log a site assessment,
  run a test protocol, flag a pest/disease/wildfire risk, schedule a site visit)
  for a registered technician's assigned forest. The technician is swappable:
  `mock-technician` (deterministic, default in dev/tests/CI) or `llm-technician`
  (wraps a real `langchain.model/ChatModel`). Either way the technician ONLY
  produces a PROPOSAL — it never writes to the store and has no notion of
  technician/forest provenance or risk; `forestry-technician.governor` is the
  independent system that decides whether the proposal may proceed, per the
  itonami actor pattern.

  A proposal is a map:
    {:op :log-site-assessment|:run-test-protocol|:flag-pest-disease-risk|:schedule-site-visit
     :effect :propose        ; the technician NEVER emits a raw store write
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}
  LLM parse failures always yield `:confidence 0.0` (never fabricate
  confidence), which forces the governor to escalate/hold."
  (:require [clojure.string :as str]))

(defprotocol Technician
  (-propose [technician store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence."
  [_store {:keys [op stake] :as request}]
  {:op op
   :effect :propose
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for forest " (:forest-id request))})

(defn mock-technician []
  (reify Technician
    (-propose [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a forestry technician. Given a technical request, propose an :op,
   an honest :confidence (0.0-1.0), and a :stake (:low/:medium/:high).
   Never fabricate confidence you don't have.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-technician
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Technician
    (-propose [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "technical request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
