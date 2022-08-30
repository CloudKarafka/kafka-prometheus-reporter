(ns cloudkarafka.kafka-http-reporter
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [clojure.java.io :as io]
            [aleph.http :as http]
            [jsonista.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :as params]
            [clojure.java.jmx :as jmx])
  (:gen-class
   :implements [org.apache.kafka.common.metrics.MetricsReporter]
   :constructors {[] []}))

(set! *warn-on-reflection* true)

(def mapper (json/object-mapper {:decode-key-fn true, :encode-key-fn true}))

(def state (atom nil))

(def bean-keys [:Value :Count :MeanRate :OneMinuteRate :FiveMinuteRate :FifteenMinuteRate
                :Max :Min :Mean :50thPercentile :99thPercentile :999thPercentile
                :connection-count :connection-creation-rate])

(defn key->attr [[k v]]
  (case k
    (:topic :partition :listener) [k v]
    :networkProcessor [:network_processor v]
    nil))

(defn keys->attrs [keys]
  (->> keys
       (map key->attr)
       (remove nil?)
       (into {})))

(defn transform-bean-name [name]
  (-> name
      str/lower-case
      (str/replace  #"[^a-zA-Z_:]" "_")))

(defn format-bean-prom [^javax.management.ObjectName mbean]
  (try
    (let [prefix (str/replace (.getDomain mbean) #"\." "_")
          keys (w/keywordize-keys (into {} (.getKeyPropertyList mbean)))
          attrs (keys->attrs keys)
          bean (jmx/objects->data (jmx/mbean mbean))]
      (for [[key value] (select-keys bean bean-keys)
            :let  [attrs (assoc attrs :key key)]]
        (str prefix
             "_"
             (transform-bean-name (:type keys))
             (when (:name keys)
               (str "_" (transform-bean-name (:name keys))))
             (when-not (empty? attrs)
               (str "{"
                    (str/join
                     ","
                     (for [[k v] attrs]
                       (str (name k) "=\"" (str/lower-case (name v)) "\"")))
                    "}"))
             " "
             value
             "\n")))
    (catch javax.management.InstanceNotFoundException _e
      (println "not found " (.getCanonicalName mbean)))))

(defn format-bean-json [^javax.management.ObjectName mbean]
  (try
    (let [keys (w/keywordize-keys (into {} (.getKeyPropertyList mbean)))
          attrs (keys->attrs keys)
          bean (jmx/objects->data (jmx/mbean mbean))]
      (for [[key value] (select-keys bean bean-keys)
            :let  [attrs (assoc attrs :key key)]]
        (assoc attrs
               :type (:type keys)
               :name (:name keys)
               :value value
               :key key)))
    (catch javax.management.InstanceNotFoundException _e
      (println "not found " (.getCanonicalName mbean)))))

(defn jmx-prometheus [^String bean]
  (try
    (if (.contains bean "*")
      (mapcat #(format-bean-prom  %) (jmx/mbean-names bean))
      (format-bean-prom (jmx/as-object-name bean)))
    (catch javax.management.InstanceNotFoundException _e
      (println "not found " bean))))


(defn jmx-json [^String bean]
  (try
    (if (.contains bean "*")
      (mapcat #(format-bean-json  %) (jmx/mbean-names bean))
      (format-bean-json (jmx/as-object-name bean)))
    (catch javax.management.InstanceNotFoundException _e
      (println "not found " bean))))

(def handler
  (params/wrap-params
   (routes
    (GET "/kafka-version" []
         {:status 200
          :headers {"content-type" "text/plain"}
          :body ""; (org.apache.kafka.common.utils.AppInfoParser/getVersion)
          })

    (GET "/metrics" []
         (if-let [beans (:metrics @state)]
           (try
             {:status 200
              :headers {"content-type" "text/plain"}
              :body (->> beans
                         (map jmx-prometheus)
                         flatten)}
             (catch Exception e
               (println e)
               {:status 500 :headers {"content-type" "text/plain"} :body "internal server error"}))
           (route/not-found "metrics disabled, no metrics file found")))


    (GET "/json" []
         (if-let [beans (:metrics @state)]
           (try
             {:status 200
              :headers {"content-type" "application/json"}
              :body (->> beans
                         (map jmx-json)
                         flatten
                         (remove nil?)
                         json/write-value-as-string)}
             (catch Exception e
               (println e)
               {:status 500 :headers {"content-type" "text/plain"} :body "internal server error"}))
           (route/not-found "metrics disabled, no metrics file found")))

    (route/not-found "not found"))))

(defn -configure [_this config]
  (let [parsed-config (into {} (map (fn [[k v]] [(keyword k) v]) config))]
    (reset! state {:kafka-config parsed-config})))

(defn -init [_this _metrics]
  (let [config (:kafka-config @state)
        port (Integer/parseInt (or (:kafka_http_reporter.port config) "19092"))
        metrics-file (:prometheus_metrics.file config)]
    (if metrics-file
      (let [metrics (with-open [rdr (io/reader metrics-file)] (reduce conj [] (line-seq rdr)))]
          (println (format "[INFO] KafkaHttpReporter: metrics exporter enabled, metrics_count=%d" (count metrics)))
          (swap! state assoc :metrics metrics))
      (println "[INFO] KafkaHttpReporter: metrics exporter disabled"))
    (println "[INFO] KafkaHttpReporter: Starting HTTP server on port " port )
    (swap! state assoc :http-server (http/start-server handler {:port port}))))

(defn -metricChange [_this _metric])
(defn -metricRemoval [_this _metric])
(defn -contextChange [_this _context])
(defn -reconfigurableConfigs [_this] #{})
(defn -validateReconfiguration [_this _config])
(defn -reconfigure [_this _config])

(defn -close [_this]
  (when-let [^java.io.Closeable s (:http-server @state)]
    (println "[INFO] KafkaHttpReporter: Closing HTTP server")
    (.close s)))
