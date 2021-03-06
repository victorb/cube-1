(ns cube.cli
  (:require [com.stuartsierra.component :as c]
            [cube.system :refer [create-system]]
            [cube.gui :as gui]
            [clojure.tools.trace :as trace])
  (:gen-class))

(def running-system (atom nil))

(defn get-port
  "Returns the value of the env var PORT or defaults to port 0"
  []
  (let [port (System/getenv "PORT")]
    (if (nil? port)
      0
      (Integer/parseInt port))))

(defn get-db-path
  "Returns the value of the env var CUBE_PATH or defaults to ~/.cube/db.clj"
  []
  (let [path (System/getenv "CUBE_PATH")]
    (if (nil? path)
      (str (System/getProperty "user.home") "/.cube/db.clj")
      path)))

(defn gui?
  "Returns true if CUBE_GUI not set or set to 'true', otherwise returns false"
  []
  (let [cube-gui (System/getenv "CUBE_GUI")]
    (if (nil? cube-gui)
      true
      (Boolean/parseBoolean cube-gui))))

(defn open-browser?
  "Returns true if CUBE_OPEN_BROWSER not set or set to 'true', otherwise false"
  []
  (let [cube-browser (System/getenv "CUBE_OPEN_BROWSER")]
    (if (nil? cube-browser)
      true
      (Boolean/parseBoolean cube-browser))))

(defn tracing-enabled?
  "Returns true if CUBE_TRACING set to `true`, otherwise `false`"
  []
  (let [tracing? (System/getenv "CUBE_TRACING")]
    (if (nil? tracing?)
      false
      (Boolean/parseBoolean tracing?))))

(defn start-system! [params]
  (reset! running-system (c/start (create-system params))))

(defn stop-system! []
  (c/stop @running-system)
  (reset! running-system nil))

(defn get-port-from-system [live-system]
  (-> @live-system
      :web
      :server
      (meta)
      :local-port))

(defn get-setup-password-from-system [live-system]
  ;; (setup/get-password (:db @live-system))
  "random-password"
  )

(defn setup-complete? [live-system]
  ;; (setup/completed? (:db @live-system))
  true
  )

(defn get-cube-namespaces []
  (->> (all-ns)
       (filter #(clojure.string/starts-with? % "cube"))))

;; Trying to be clever and get all namespaces dynamically but trace-ns is
;; trying to trace the symbol as the actual namespace, instead of the value
;; of the symbol (which is the namespace to trace)
;; (defn trace-cube-namespaces []
;;   (doseq [n (get-cube-namespaces)]
;;     (trace/trace-ns n)))
;;
;; Easy solution for now is to just manually list the namespaces
(defn trace-cube-namespaces []
  (do (trace/trace-ns 'cube.cli)
      (trace/trace-ns 'cube.cluster)
      (trace/trace-ns 'cube.db)
      (trace/trace-ns 'cube.gui)
      (trace/trace-ns 'cube.instances)
      (trace/trace-ns 'cube.scheduler)
      (trace/trace-ns 'cube.system)
      (trace/trace-ns 'cube.web)
      ))

(defn -main [& args]
  (when (tracing-enabled?)
    (trace-cube-namespaces))
  (when (gui?)
    (gui/start-gui))
  (start-system! {:http-port (get-port)
                  :db-path (get-db-path)})
  (let [port (get-port-from-system running-system)
        password (get-setup-password-from-system running-system)]
    (when (gui?)
      (do
        (gui/set-port! port)
        (gui/set-password! password)
        (gui/server-started!)))
    (when (open-browser?)
      (if (setup-complete? running-system)
        (gui/open-dashboard port)
        (gui/open-setup port password)))
    (println "======")
    (println (str "Server running on http://localhost:" port))
    (println (str "Setup Password: " password))))
