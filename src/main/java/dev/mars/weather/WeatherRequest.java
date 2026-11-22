package dev.mars.weather;

import java.time.LocalDate;

/**
 * Describes a weather forecast request.
 *
 * <p>A record supplies a constructor, accessors such as city(), value-based
 * equality, and a readable toString. Each component is final. The component
 * types here, String and LocalDate, are themselves immutable, so the example
 * can safely capture this request inside a deferred lookup function.
 *
 * <p>The simulator produces a random forecast after an interruptible delay.
 * Provider names distinguish competing calls; country and date retain the
 * application's request shape. The examples use an in-process service.
 *
 * @param provider the provider name
 * @param country the ISO country code
 * @param city the city to forecast
 * @param date the forecast date
 */
public record WeatherRequest(
        String provider, String country, String city, LocalDate date) {
}
