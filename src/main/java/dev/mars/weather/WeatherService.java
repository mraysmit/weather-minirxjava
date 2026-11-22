package dev.mars.weather;

/**
 * Fetches forecasts for weather requests.
 *
 * <p>This is the ordinary application boundary. It knows about weather data
 * but not about observables, observers, or subscription. App adapts its fetch
 * method into a reactive source with Observable.fromCallable.
 *
 * <p>A single abstract method lets the example supply the implementation as a
 * lambda. Calling fetch executes that lambda directly on the caller's thread.
 * The interface does not provide scheduling or make a blocking operation
 * asynchronous. MiniRxJava supplies scheduling and cancellation at the
 * reactive boundary in WeatherClient.
 */
@FunctionalInterface
public interface WeatherService {

    /**
     * Fetches the requested forecast.
     *
     * <p>The checked exception leaves room for an interruptible lookup.
     * Callable can carry that exception across the adapter boundary;
     * fromCallable then reports it through the observer's onError callback.
     *
     * @param request the forecast request
     * @return the forecast response
     * @throws InterruptedException if a blocking lookup is interrupted
     */
    WeatherResponse fetch(WeatherRequest request) throws InterruptedException;
}
