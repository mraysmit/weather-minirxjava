# MiniRxJava Weather

Seven reactive weather examples equivalent to the scenarios in
[`weather-structured-concurrency`](../weather-structured-concurrency).
The application uses the separate [MiniRxJava library](../minirxjava/README.md)
for scheduling, composition, deadlines, and cancellation.

## Projects

```text
idea-projects/
  minirxjava/                       Generic reactive library and tests
  weather-minirxjava/               Reactive weather client and seven demos
  weather-structured-concurrency/   Reference implementation
```

The library and application have independent Maven POMs. This application uses
`dev.mars:minirxjava:1.0-SNAPSHOT` as a normal dependency.

## Run all seven examples

Use JDK 27 and Maven 3.9 or newer. From this project directory:

```powershell
mvn -f ../minirxjava/pom.xml clean install
mvn clean verify exec:exec
```

For subsequent demo runs:

```powershell
mvn compile exec:exec
```

After library changes, run `mvn install` in the library project first.
Both projects can be imported into IntelliJ IDEA as Maven projects; run
`dev.mars.App` in the application. No preview features are required.

## Scenarios

`App.main()` runs every example and prints its heading, results, and timing:

| Example | Behavior | Reactive composition |
| --- | --- | --- |
| 1. Sequential baseline | Five cities, one lookup at a time | `concat` |
| 2. Concurrent fan-out | All cities together, fail as a group | `sequence` with `subscribeOn` |
| 3. Race per city | First successful provider wins; losers stop | `firstSuccessful` |
| 4. Shared timeout | Retain successes within one second | `merge`, `take`, `toList` |
| 5. Partial results | Keep successes despite failed cities | `onErrorResumeNext`, `merge` |
| 6. Nested composition | Concurrent cities, provider race inside each | `sequence` of `firstSuccessful` |
| 7. Failure cancellation | Failed lookup interrupts unfinished siblings | `sequence` failure handling |

The simulator and request/response contracts match the reference. The simulator
uses interruptible delays from 200 ms to just under 2 seconds and randomized
forecasts. Its seeded constructor supports repeatable sequential tests.
Console values, winning providers, and elapsed times vary between runs.

## Implementation

`WeatherService` is the ordinary blocking boundary. `WeatherClient` adapts calls
with `fromCallable`, schedules concurrent calls on virtual threads, and composes
them with library operators. Blocking occurs only at the public client boundary
through `blockingSingle()`. The client contains no executors or task scopes.

`TimedResponse` carries the provider, forecast, and elapsed time measured with
the monotonic clock. Concurrent all-results and nested operations retain input
order. Partial successes use completion order. Timeout results retain an
`Optional` slot for each input, including repeated cities. Failed or unfinished
slots are empty.

Provider races ignore errors while another provider can still succeed. If all
providers fail, the race reports the collected failures. Losing, timed-out, and
failed-group work is interrupted, and the client waits for cleanup before
returning. As in the reference, cancellation requires interruptible services.

## Checks

`mvn verify` runs Checkstyle and behavior tests. The reference's weather-client
and simulator fixtures also run against this implementation. Additional tests
cover sequential ordering, duplicate cities, zero deadlines, nested concurrency,
and cancellation of provider trees. The console integration test executes all
seven real examples and checks their output. It takes several seconds because
it exercises the simulator's real delays.

Production Java follows Sun coding conventions and an 80-character line limit.
