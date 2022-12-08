# kafka-prometheus-reporter

Prometheus reporter for Kafka

It exposes an HTTP endpoint `/metrics` that returns the metrics in prometheus format

## Usage

* Download the latest version from the releases
* Put the jar file in the `libs/` folder of your kafka deployment
* Add this line to `server.properties`: `metric.reporters=cloudkarafka.kafka_http_reporter`
* (Re)start the broker

## Configuration

Which metrics to return is specified by a properties file
you tell the plugin where the file is by adding `` to the Kafka configuration file.

The file should look something like this:

```
kafka.cluster:type=Partition,topic=*,name=UnderMinIsr,partition=*
kafka.controller:type=KafkaController,name=OfflinePartitionsCount
kafka.controller:type=KafkaController,name=ActiveControllerCount
kafka.controller:type=KafkaController,name=GlobalPartitionCount
kafka.controller:type=ControllerStats,name=LeaderElectionRateAndTimeMs
kafka.controller:type=ControllerStats,name=UncleanLeaderElectionsPerSec
```

The plugin supports wildcards `*` for some parameters, so you can get metrics for all topics but without listing the names of all topics.

## Development

* `lein uberjar` produces a standalone jar file in `target/`
* Follow the same procedure as above to install it

## Release

* Update the version number in `project.clj`
* Commit the change and tag it with the same version
* Push to Github

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/CloudKarafka/KafkaHttpReporter/tags).

## Authors

* **Magnus Landerblom** - *Initial work* - [snichme](https://github.com/snichme)
