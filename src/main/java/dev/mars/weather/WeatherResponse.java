package dev.mars.weather;

/**
 * Describes a weather forecast response.
 *
 * <p>This record is the payload delivered through onNext. It contains weather
 * data only; success or failure of the sequence is represented separately by
 * onComplete and onError. Keeping data and lifecycle signals separate lets
 * the same observer protocol work with other payload types later.
 *
 * @param city the forecast city
 * @param temperature the temperature in degrees Celsius
 * @param condition the forecast condition
 */
public record WeatherResponse(String city, int temperature, String condition) {
}
