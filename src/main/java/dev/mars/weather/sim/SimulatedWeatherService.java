package dev.mars.weather.sim;

import dev.mars.weather.WeatherRequest;
import dev.mars.weather.WeatherResponse;
import dev.mars.weather.WeatherService;
import java.util.List;
import java.util.Random;

/**
 * Simulated weather service that returns randomised results after a randomised
 * delay. Use the seeded constructor for deterministic results in tests.
 */
public final class SimulatedWeatherService implements WeatherService {

    /** Possible forecast conditions. */
    private static final List<String> CONDITIONS = List.of(
            "sunny", "partly cloudy", "cloudy", "overcast",
            "drizzle", "rain", "heavy rain", "thunderstorm", "snow", "fog");
    /** Inclusive lower bound for forecast temperatures. */
    private static final int MIN_TEMP_CELSIUS = -10;
    /** Inclusive upper bound for forecast temperatures. */
    private static final int MAX_TEMP_CELSIUS = 40;
    /** Inclusive lower bound for simulated delays in milliseconds. */
    private static final long MIN_DELAY_MS = 200L;
    /** Exclusive upper bound for simulated delays in milliseconds. */
    private static final long MAX_DELAY_MS = 2000L;

    /** Random source shared by the simulated lookups. */
    private final Random random;

    /**
     * Constructs a service with a non-deterministic random source.
     */
    public SimulatedWeatherService() {
        this.random = new Random();
    }

    /**
     * Constructs a service with a seeded random source for deterministic
     * results in tests.
     *
     * @param seed the random seed
     */
    public SimulatedWeatherService(final long seed) {
        this.random = new Random(seed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WeatherResponse fetch(final WeatherRequest request)
            throws InterruptedException {
        long delay = MIN_DELAY_MS
                + (long) (random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        Thread.sleep(delay);
        String condition = CONDITIONS.get(random.nextInt(CONDITIONS.size()));
        int temperature = MIN_TEMP_CELSIUS
                + random.nextInt(MAX_TEMP_CELSIUS - MIN_TEMP_CELSIUS + 1);
        return new WeatherResponse(request.city(), temperature, condition);
    }
}
