package dev.mars.client;

import dev.mars.weather.WeatherResponse;

/**
 * A weather response paired with the elapsed time taken to retrieve it.
 *
 * @param provider the provider that returned the forecast
 * @param response the weather forecast
 * @param elapsedMs the elapsed lookup time in milliseconds
 */
public record TimedResponse(
        String provider, WeatherResponse response, long elapsedMs) {
}
