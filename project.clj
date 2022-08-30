(defproject kafka-http-reporter "1.0.0"
  :description "Expose JMX metrics through a HTTP interface"
  :url "http://github.com/CloudKarafka/kafka-http-reporter"
  :license {:name "Apache License 2.0"
            :url "https://github.com/CloudKarafka/KafkaHttpReporter/blob/master/LICENSE"}
  :profiles {:uberjar {:aot :all}
             :provided {:dependencies [[org.apache.kafka/kafka_2.13 "3.2.0"]
                                       [org.apache.kafka/kafka-clients "3.2.0"]]}}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/java.jmx "1.0.0"]
                 [metosin/jsonista "0.3.6"]
                 [aleph "0.4.7"]
                 [compojure "1.7.0"]])
