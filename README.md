# Download Manager
Service which downloads and publishes messages from Zendesk as a stream.

## Start
Run `sbt run`. Head over to <http://localhost:8080> to see the OpenAPI documenation.

## Components
The service can be roughly said to consist of the following components:
* `StreamStateRepo`
  Responsibility: persisting state of the stream
* `StreamApi`
  Responsibility: starting and stopping of a streams. Ensures that only 1 stream max per domain is running.
  Note that the `StreamApi` is **not** dependent on the `StreamStateRepo`. This is by design, to not have coupling between these components.
  The invariant of 1 stream max per domain is enforced using Software Transactional Memory (STM), which is possible only because of this fact. 
* `DownloadManagerApi`
  Responsibility: starting and stopping of streams, persisting their state as they run.
  Streams can also be added or removed. Removal can be seen as a permanent stop of a stream, where the stream can no longer resume from a previous offset when started/added again.
  Dependent on `StreamStateRepo` and `StreamApi`

By 'api' we mean the same as 'service' in DDD (we don't use the name 'service' to prevent the confusion with
ZIO's `Service`).

## Stack
* <https://zio.dev/> effect management and streams
* <https://http4s.org/> http server
* <https://github.com/endpoints4s/endpoints4s> http routes and OpenAPI derivation
