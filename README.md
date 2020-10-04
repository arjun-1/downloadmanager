# Download Manager
Service which downloads and publishes messages from Zendesk as a stream.

## Start
Run `sbt run`. Head over to <http://localhost:8080> to see the OpenAPI documenation.

## Components
The service can be roughly said to consist of the following components:
* `StreamStateRepo`
  *Responsibility*:
  Persisting state of the stream.

* `StreamApi`
  *Responsibility*:
  Starting and stopping of a streams. Ensures that only 1 stream max per domain is running.
  Note that the `StreamApi` is **not** dependent on the `StreamStateRepo`. This is by design, to not have coupling between these components.
  The invariant of 1 stream max per domain is enforced using Software Transactional Memory (STM), which prevents 
  race conditions when checking if a stream is already running.
  *Tradeoffs*:
  Arguably, it is possible to let the `StreamApi` be directly dependent on the persistence layer (`StreamStateRepo`).
  If the persistence layer is implemented using a transactional database, that could be used instead of
  STM to prevent race conditions.
  But as mentioned above, that introduces tight coupling between the persistence layer and the rest of the application, which might be unwanted. It also causes the updating of the state of the stream to be spread out
  over different places in the application, which could also be undesirable (now only `DownloadManagerApi` updates
  the state of the stream).
  
  Currently, the `StreamApi` only exposes `start` and `stop` methods, which behave statefull:
  if `start` was already called before, the second invocation of `start` results in an error. The advantage of 
  this behaviour, is that is becomes easier to detect programmer mistakes (i.e. unintentional calls to start/stop).
  This is opposed to idempotent behaviour, where calling `start` first stops any existing stream, then starts a new one.
  Ideally, `StreamApi` should expose methods for both behaviours: e.g. `start` and `stop` act statefull, while
  there could be a method `restart` which idempotently starts a stream. Only `start` and `stop` are implemented,
  and not `restart` due to time constraints.

* `DownloadManagerApi`
  *Responsibility*:
  Starting and stopping of streams, persisting their state as they run.
  Streams can also be added or removed. Removal can be seen as a permanent stop of a stream, where the stream can no longer resume from a previous offset when started/added again.
  Dependent on `StreamStateRepo` and `StreamApi`

By 'api' we mean the same as 'service' in DDD (we don't use the name 'service' to prevent the confusion with
ZIO's `Service`).

## Improvements
There are some improvements which could have been implemented given more time:

* Retrying of the requests to Zendesk. This can be achieved very easily at http client level. Since we are using using
  <https://sttp.softwaremill.com>, it is done straightforwardly by creating a `RetryingBackend` (<https://sttp.softwaremill.com/en/v2.0.0-rc6/backends/custom.html>)
* Input sanitization of the http requests. Right now `startTime` could be negative, `token` empty etc.
  These can be sanitized using e.g. <https://github.com/fthomas/refined>
* As mentioned above, endpoints to idempotently start new streams.
* `error` could be part of the `StreamState`. I.e. when a stream fails, it is stopped but not due to a user command.
  This field would be optional and show the error status. A stream which was stopped due to an error, could be restarted like a regular stopped stream.

## Stack
* <https://zio.dev/> effect management and streams
* <https://http4s.org/> http server
* <https://github.com/endpoints4s/endpoints4s> http routes and OpenAPI derivation
* <https://sttp.softwaremill.com> wraps the http client to unified interface
