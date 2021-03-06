(ns cube.db
  (:require [com.stuartsierra.component :as c]
            [clojure.java.io :refer [make-parents]]
            [clojure.spec.alpha :as s]
            [shared.db :as shared-db]
            [clojure.pprint :refer [pprint]]))

;; TODO - DB under test currently overrides locally saved DB...

(defn load-db [path]
  (try
    (read-string (slurp path))
    (catch java.io.FileNotFoundException _ nil)))

(def initial-state {})

(defn new-or-existing-db [path]
  (if-let [new-db (load-db path)]
    (atom (merge initial-state new-db))
    (atom initial-state)))

(defn validate-new-state [new-state]
  (if (s/valid? :cube/db new-state)
    true
    (do (println "Explanation:")
        (s/explain :cube/db new-state)
        (println "Tried new state:")
        (pprint new-state)
        (println "From:")
        (pprint (.getStackTrace (Thread/currentThread))))))

(defrecord DB [path]
  c/Lifecycle
  (start [this]
    (println (str "[db] Starting with db " path))
    (let [new-state (new-or-existing-db path)]
      (set-validator! new-state validate-new-state)
      (-> this
          (assoc :db-path path)
          (assoc :state new-state))))

  (stop [this]
    (println "[db] Stopping")
    (assoc this :state nil)))

(defn persist! [db]
  (make-parents (:db-path db))
  (spit (:db-path db) (with-out-str (pprint @(:state db)))))

(defn put [db k v]
  (do
    (swap! (:state db) assoc k v)
    (persist! db)))

(defn put-in [db ks v]
  (do
    (swap! (:state db) assoc-in ks v)
    (persist! db)))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (assoc m k newmap))
      m)
    (dissoc m k)))

(defn remove [db ks]
  (do
    (swap! (:state db) dissoc ks)
    (persist! db)))

(defn remove-in [db ks]
  (do
    (swap! (:state db) dissoc-in ks)
    (persist! db)))

(defn access [db k]
  (k @(:state db)))

(defn access-in [db k]
  (get-in @(:state db) k nil))

(defn add-to [db k v]
  (swap! (:state db) update-in k conj v))

(defn on-change [db f]
  (add-watch (:state db) nil (fn [key atom old new]
                               (when (not (= old new))
                                 (f new)))))

(defn new [path]
  (map->DB {:path path}))
