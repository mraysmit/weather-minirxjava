# MiniRxJava Weather

> **Note:** This project is a set of examples demonstrating the use of the
> MiniRxJava library.

Run seven weather examples to see sequential execution, concurrent lookups,
provider races, deadlines, partial results, and cancellation using MiniRxJava.

Forecasts and response delays are simulated in process, so you can run the
examples without API credentials.

## Quick start

Open a terminal in **`weather-minirxjava`**, the directory containing this README
and [pom.xml](pom.xml). Use JDK 27 and Maven 3.9 or newer. Both version commands
below should report Java 27:

```powershell
java --version
mvn --version
```

This application requires the Maven dependency
`dev.mars:minirxjava:1.0-SNAPSHOT`, declared in [pom.xml](pom.xml). Make that
version available in your local or configured Maven repository before building.
If building MiniRxJava from source, run this command in its source checkout:

```powershell
mvn clean install
```

Then, from this application's root directory, run all seven examples:

```powershell
mvn compile exec:exec
```

This compiles the application and runs `dev.mars.App`. Use the same command
for subsequent runs. After changing library code, install its updated JAR
before rebuilding the application.

The examples include real simulated delays, so a complete run takes several
seconds. Temperatures, conditions, provider winners, and timings vary between
runs. No preview flags are required.

## What the console shows

`App.main()` runs all seven examples in order. Each prints an `=== Example N:`
heading, its results, and elapsed time in milliseconds.

| Example | What to observe | Library features |
| --- | --- | --- |
| 1. Sequential baseline | Five cities run one at a time; durations add up. | `concat` |
| 2. Concurrent fan-out | Cities run together; results keep input order. | `sequence`, `subscribeOn` |
| 3. Race per city | Three providers compete; the first success wins. | `firstSuccessful` |
| 4. Shared timeout | One deadline keeps completed results and cancels the rest. | `merge`, `take`, `toList` |
| 5. Partial results | Paris and Madrid fail; the other three results are kept. | `onErrorResumeNext`, `merge` |
| 6. Nested composition | Cities run together, each with its own provider race. | `sequence` of `firstSuccessful` |
| 7. Failure cancellation | One failed lookup interrupts unfinished siblings. | `sequence` failure handling |

Example 4 prints `timed out` for unfinished requests and a completed-result
count. Its one-second deadline applies to the whole group, rather than starting
a new budget for each city.

Example 7 deliberately triggers and catches a failure. It ends with output like:

```text
Lookup group failed after 1 ms; unfinished subtasks were cancelled.
Failure type: IllegalStateException
```

This is the expected demonstration of cancellation. The application exits
normally and Maven reports `BUILD SUCCESS`.

## IntelliJ IDEA

Import this repository's [pom.xml](pom.xml) as a Maven project. Select JDK 27
for the project and Maven runner, resolve the dependency described above, and
run `dev.mars.App`.

## Read the implementation

- [App](src/main/java/dev/mars/App.java) defines and prints the seven examples.
- [WeatherClient](src/main/java/dev/mars/client/WeatherClient.java) adapts the
  blocking service with `fromCallable`, schedules concurrent calls on virtual
  threads, and composes them using library operators. Its public methods wait
  for results through `blockingSingle()`.
- [SimulatedWeatherService](src/main/java/dev/mars/weather/sim/SimulatedWeatherService.java)
  uses interruptible delays from 200 ms to just under two seconds and random
  forecasts. Its seeded constructor supports repeatable sequential tests.
- [TimedResponse](src/main/java/dev/mars/client/TimedResponse.java) carries the
  provider, forecast, and lookup duration measured with the monotonic clock.

Concurrent all-results and nested operations retain input order. Partial
successes use completion order. Timeout results retain an `Optional` slot for
each input, including repeated cities; failed or unfinished slots are empty.

Provider races ignore errors while another provider can still succeed. If all
providers fail, the race reports their collected failures. Losing, timed-out,
and failed-group work is interrupted, and the client waits for cleanup before
returning. Services must cooperate with interruption. The deadline limits result
collection; waiting for cleanup can take longer than the configured budget.

## Run the checks

After setting up the dependency, run the application checks from this
repository's root directory:

```powershell
mvn clean verify
```

This runs Checkstyle and behavior tests covering the client, simulator,
duplicate cities, zero deadlines, nested races, and cancellation cleanup. The
[console integration test](src/test/java/dev/mars/AppTest.java) runs all seven
real examples with their delays and captures the output for assertions.
Maven prints its test result after the examples finish.

Use `mvn compile exec:exec` to watch the demo. Combining `verify exec:exec` also
runs the examples inside the integration test before launching the visible demo.

Production Java follows Sun coding conventions and an 80-character line limit.

## Troubleshooting

- **MiniRxJava dependency cannot be resolved:** make
  `dev.mars:minirxjava:1.0-SNAPSHOT` available in your Maven repository as
  described in the quick start, then reload Maven in your IDE.
- **Java release or class-version error:** ensure `java --version` and
  `mvn --version` both report Java 27. Check the IDE's project SDK and Maven
  runner JDK as well.
- **Only the old single-London demo appears:** run `mvn clean compile exec:exec`
  from `weather-minirxjava` and check that the IDE run configuration uses this
  project's `dev.mars.App`.
