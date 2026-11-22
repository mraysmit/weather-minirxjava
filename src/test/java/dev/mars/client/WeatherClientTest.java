package dev.mars.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mars.weather.WeatherRequest;
import dev.mars.weather.WeatherResponse;
import dev.mars.weather.WeatherService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for WeatherClient.
 */
@Timeout(5)
class WeatherClientTest {

    @Test
    void fetchAllConcurrentPreservesRequestOrder()
            throws InterruptedException {
        CountDownLatch secondStarted = new CountDownLatch(1);
        WeatherClient client = new WeatherClient(request -> {
            if ("London".equals(request.city())) {
                secondStarted.await();
            } else {
                secondStarted.countDown();
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        List<WeatherRequest> requests =
                List.of(request("London"), request("Paris"));

        List<TimedResponse> responses = client.fetchAllConcurrent(requests);

        assertEquals(List.of("London", "Paris"), responses.stream()
                .map(response -> response.response().city()).toList());
    }

    @Test
    void fetchAllConcurrentCancelsUnfinishedLookupsOnFailure() {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        IllegalStateException failure =
                new IllegalStateException("provider failed");
        WeatherClient client = new WeatherClient(request -> {
            if ("Paris".equals(request.city())) {
                slowStarted.await();
                throw failure;
            }
            slowStarted.countDown();
            try {
                blocked.await();
            } catch (InterruptedException ex) {
                interrupted.set(true);
                throw ex;
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> client.fetchAllConcurrent(
                        List.of(request("London"), request("Paris"))));

        assertEquals(failure, thrown.getCause());
        assertTrue(interrupted.get());
    }

    @Test
    void fetchFirstCancelsLosingProvider() throws InterruptedException {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        WeatherClient client = new WeatherClient(request -> {
            if ("winner".equals(request.provider())) {
                slowStarted.await();
                return new WeatherResponse(request.city(), 20, "sunny");
            }
            slowStarted.countDown();
            try {
                blocked.await();
            } catch (InterruptedException ex) {
                interrupted.set(true);
                throw ex;
            }
            return new WeatherResponse(request.city(), 10, "rain");
        });
        LocalDate today = LocalDate.now();

        TimedResponse response = client.fetchFirst(List.of(
                new WeatherRequest("slow", "GB", "London", today),
                new WeatherRequest("winner", "GB", "London", today)));

        assertEquals("winner", response.provider());
        assertTrue(interrupted.get());
    }

    @Test
    void fetchAllWithTimeoutKeepsSuccessesAndCancelsSlowLookups()
            throws InterruptedException {
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        WeatherClient client = new WeatherClient(request -> {
            if ("Paris".equals(request.city())) {
                try {
                    blocked.await();
                } catch (InterruptedException ex) {
                    interrupted.set(true);
                    throw ex;
                }
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });

        var responses = client.fetchAllWithTimeout(
                List.of(request("London"), request("Paris")),
                Duration.ofMillis(500));

        assertEquals(2, responses.size());
        assertEquals("London",
                responses.get(0).orElseThrow().response().city());
        assertFalse(responses.get(1).isPresent());
        assertTrue(interrupted.get());
    }

    @Test
    void fetchFastestForEachCityReturnsOneResponsePerCity()
            throws InterruptedException {
        WeatherClient client = new WeatherClient(
                request -> new WeatherResponse(request.city(), 20, "sunny"));

        List<TimedResponse> responses = client.fetchFastestForEachCity(
                List.of(request("London"), request("Paris")),
                List.of("provider1", "provider2"));

        assertEquals(List.of("London", "Paris"), responses.stream()
                .map(response -> response.response().city()).toList());
        assertTrue(responses.stream().allMatch(response -> Set.of(
                "provider1", "provider2").contains(response.provider())));
    }

    private static WeatherRequest request(final String city) {
        return new WeatherRequest("default", "GB", city, LocalDate.now());
    }

    @Test
    void sequentialBaselinePreservesDuplicatesAndUsesTheCallerThread()
            throws InterruptedException {
        Thread caller = Thread.currentThread();
        List<String> calls = new ArrayList<>();
        WeatherClient client = new WeatherClient(request -> {
            assertSame(caller, Thread.currentThread());
            calls.add(request.city());
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        var results = client.fetchAll(List.of(request("London"),
                request("Paris"), request("London")));
        assertEquals(List.of("London", "Paris", "London"), calls);
        assertEquals(calls, results.stream().map(r -> r.response().city()).toList());
        assertTrue(results.stream().allMatch(r -> r.elapsedMs() >= 0));
    }

    @Test
    void firstSuccessfulProviderIgnoresFailedProviders()
            throws InterruptedException {
        WeatherClient client = new WeatherClient(request -> {
            if ("failed".equals(request.provider())) {
                throw new IllegalArgumentException("provider failed");
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        LocalDate date = LocalDate.now();
        var result = client.fetchFirst(List.of(
                new WeatherRequest("failed", "GB", "London", date),
                new WeatherRequest("winner", "GB", "London", date)));
        assertEquals("winner", result.provider());
    }

    @Test
    void nestedRacesStartCitiesTogetherAndCleanUpEveryLoser()
            throws InterruptedException {
        CountDownLatch slowStarted = new CountDownLatch(2);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicInteger cleaned = new AtomicInteger();
        WeatherClient client = new WeatherClient(request -> {
            if ("slow".equals(request.provider())) {
                slowStarted.countDown();
                try {
                    blocked.await();
                } finally {
                    cleaned.incrementAndGet();
                }
            } else {
                slowStarted.await();
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        var results = client.fetchFastestForEachCity(
                List.of(request("London"), request("Paris")),
                List.of("slow", "winner"));
        assertEquals(List.of("London", "Paris"), results.stream()
                .map(r -> r.response().city()).toList());
        assertTrue(results.stream().allMatch(r -> r.provider().equals("winner")));
        assertEquals(2, cleaned.get());
    }

    @Test
    void nestedFailureCancelsOtherCityProviderTrees() {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicInteger cleaned = new AtomicInteger();
        WeatherClient client = new WeatherClient(request -> {
            if ("Paris".equals(request.city())) {
                started.await();
                throw new IllegalArgumentException("all providers failed");
            }
            started.countDown();
            try {
                blocked.await();
            } finally {
                cleaned.incrementAndGet();
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> client.fetchFastestForEachCity(
                        List.of(request("London"), request("Paris")),
                        List.of("provider1", "provider2")));
        assertInstanceOf(NoSuchElementException.class, thrown.getCause());
        assertEquals(2, cleaned.get());
    }

    @Test
    void sharedDeadlinePreservesSlotsForDuplicateCities()
            throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicInteger cleaned = new AtomicInteger();
        WeatherClient client = new WeatherClient(request -> {
            if ("slow".equals(request.provider())) {
                started.countDown();
                try {
                    blocked.await();
                } finally {
                    cleaned.incrementAndGet();
                }
            } else {
                started.await();
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        LocalDate date = LocalDate.now();
        var results = client.fetchAllWithTimeout(List.of(
                new WeatherRequest("slow", "GB", "London", date),
                new WeatherRequest("fast", "GB", "London", date)),
                Duration.ofMillis(500));
        assertTrue(results.getFirst().isEmpty());
        assertEquals("fast", results.get(1).orElseThrow().provider());
        assertEquals(1, cleaned.get());
    }

    @Test
    void emptyInputsAndZeroBudgetDoNotFetch() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        WeatherClient client = new WeatherClient(request -> {
            calls.incrementAndGet();
            return new WeatherResponse(request.city(), 20, "sunny");
        });
        assertEquals(List.of(), client.fetchAll(List.of()));
        assertEquals(List.of(), client.fetchAllConcurrent(List.of()));
        assertEquals(List.of(), client.fetchSuccessful(List.of()));
        assertEquals(List.of(), client.fetchFastestForEachCity(
                List.of(), List.of("provider")));
        assertTrue(client.fetchAllWithTimeout(List.of(request("London")),
                Duration.ZERO).getFirst().isEmpty());
        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, () -> client.fetchFirst(List.of()));
    }

    @Test
    void fetchSuccessfulReturnsOnlySuccessfulResponses()
            throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        WeatherService service = request -> {
            attempts.incrementAndGet();
            if ("Paris".equals(request.city())) {
                throw new IllegalStateException("simulated failure");
            }
            return new WeatherResponse(request.city(), 20, "sunny");
        };
        WeatherClient client = new WeatherClient(service);
        LocalDate today = LocalDate.now();
        List<WeatherRequest> requests = List.of(
                new WeatherRequest("default", "GB", "London", today),
                new WeatherRequest("default", "FR", "Paris", today),
                new WeatherRequest("default", "DE", "Berlin", today));

        List<TimedResponse> responses = client.fetchSuccessful(requests);

        Set<String> cities = responses.stream()
                .map(response -> response.response().city())
                .collect(Collectors.toSet());
        assertEquals(Set.of("London", "Berlin"), cities);
        assertEquals(requests.size(), attempts.get());
    }
}
