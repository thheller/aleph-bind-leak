(ns aleph-bind-leak.core
  (:require [aleph.http :as http]
            [clojure.java.jmx :as jmx]
            [clojure.java.io :as io])

  (:import (java.net BindException)))


(def pid (jmx/read "java.lang:type=Runtime" :Name) )

(defn handler [req]
  (prn [pid :serve (:uri req)])
  {:status 200
   :body (str "this is pid:" pid)})

(defn start-server []
  (loop []
    (if-let [http
             (try
               (http/start-server handler {:port 4123})
               (catch BindException e
                 (prn [pid :socket-in-use])
                 ;; (touch-shutdown)
                 (Thread/sleep 250)
                 nil))]
      http
      (recur)
      )))

(defn -main [& args]
  (let [file (io/file "tmp/shutdown.txt")]
    (when-not (.exists file)
      (io/make-parents file))

    ;; this modifies the file, which in theory triggers a running app to shutdown
    (spit file pid)

    (let [start-mod (.lastModified file)]

      (with-open [server (start-server)]

        (prn [pid :ready])
        (loop []
          (if (> (.lastModified file) start-mod)
            (prn [pid :shutdown-request])

            ;; not modified
            (do (Thread/sleep 500)
                (recur))))

        (prn [pid :shutdown]))))

  ;; just to be sure there are no pending clojure threads
  (shutdown-agents)
  
  (prn [pid :shutdown-complete]))