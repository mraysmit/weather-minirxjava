package dev.mars.client;

import dev.mars.minirx.Observable;
import dev.mars.minirx.Schedulers;
import dev.mars.weather.WeatherRequest;
import dev.mars.weather.WeatherResponse;
import dev.mars.weather.WeatherService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Composes weather lookups using the generic MiniRxJava operators. */
public final class WeatherClient {
    /** Ordinary blocking service adapted at the reactive boundary. */
    private final WeatherService service;

    /**
     * Creates a client.
     * @param weatherService the service to adapt
     */
    public WeatherClient(final WeatherService weatherService) {
        service = Objects.requireNonNull(weatherService, "weatherService");
    }

    /**
     * Fetches requests sequentially in input order.
     * @param requests the requests
     * @return timed responses in input order
     * @throws InterruptedException if the caller is interrupted
     */
    public List<TimedResponse> fetchAll(final List<WeatherRequest> requests)
            throws InterruptedException {
        List<Observable<TimedResponse>> sources = List.copyOf(requests).stream()
                .map(this::lookup).toList();
        return Observable.concat(sources).toList().blockingSingle();
    }

    /**
     * Fetches all requests concurrently; any failure cancels the group.
     * @param requests the requests
     * @return timed responses in input order
     * @throws InterruptedException if the caller is interrupted
     */
    public List<TimedResponse> fetchAllConcurrent(
            final List<WeatherRequest> requests) throws InterruptedException {
        return Observable.sequence(concurrentSources(requests))
                .blockingSingle();
    }

    /**
     * Races providers, ignoring failures until one produces a value.
     * @param providerRequests one request per provider
     * @return the first successful response after losing tasks are cleaned up
     * @throws InterruptedException if the caller is interrupted
     */
    public TimedResponse fetchFirst(final List<WeatherRequest> providerRequests)
            throws InterruptedException {
        return Observable.firstSuccessful(concurrentSources(providerRequests))
                .blockingSingle();
    }

    /**
     * Keeps successes until one shared deadline and cancels unfinished work.
     * @param requests the requests
     * @param timeout the non-negative budget for the whole operation
     * @return optionals in input order; unfinished or failed entries are empty
     * @throws InterruptedException if the caller is interrupted
     */
    public List<Optional<TimedResponse>> fetchAllWithTimeout(
            final List<WeatherRequest> requests, final Duration timeout)
            throws InterruptedException {
        List<WeatherRequest> inputs = List.copyOf(requests);
        List<Observable<IndexedResponse>> sources = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            int index = i;
            sources.add(concurrentLookup(inputs.get(i))
                    .map(response -> new IndexedResponse(index, response))
                    .onErrorResumeNext(error -> Observable.empty()));
        }
        return Observable.merge(sources).take(timeout).toList().map(values -> {
            List<Optional<TimedResponse>> results = new ArrayList<>(
                    Collections.nCopies(inputs.size(), Optional.empty()));
            for (IndexedResponse value : values) {
                results.set(value.index(), Optional.of(value.response()));
            }
            return List.copyOf(results);
        }).blockingSingle();
    }

    /**
     * Collects successful responses without failing on individual errors.
     * @param requests the requests
     * @return successes in completion order
     * @throws InterruptedException if the caller is interrupted
     */
    public List<TimedResponse> fetchSuccessful(
            final List<WeatherRequest> requests) throws InterruptedException {
        List<Observable<TimedResponse>> sources = concurrentSources(requests)
                .stream().map(source -> source.onErrorResumeNext(
                        error -> Observable.empty())).toList();
        return Observable.merge(sources).toList().blockingSingle();
    }

    /**
     * Starts cities together and races their providers independently.
     * @param cityRequests the city requests
     * @param providers the provider names to race for each city
     * @return one winning response per city in input order
     * @throws InterruptedException if the caller is interrupted
     */
    public List<TimedResponse> fetchFastestForEachCity(
            final List<WeatherRequest> cityRequests,
            final List<String> providers) throws InterruptedException {
        List<String> names = List.copyOf(providers);
        List<Observable<TimedResponse>> cities = List.copyOf(cityRequests)
                .stream().map(city -> {
                    List<WeatherRequest> requests = names.stream()
                            .map(provider -> new WeatherRequest(provider,
                                    city.country(), city.city(), city.date()))
                            .toList();
                    return Observable.firstSuccessful(
                            concurrentSources(requests));
                }).toList();
        return Observable.sequence(cities).blockingSingle();
    }

    private Observable<TimedResponse> lookup(final WeatherRequest request) {
        return Observable.fromCallable(() -> {
            long start = System.nanoTime();
            WeatherResponse response = service.fetch(request);
            return new TimedResponse(request.provider(), response,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        });
    }

    private Observable<TimedResponse> concurrentLookup(
            final WeatherRequest request) {
        return lookup(request).subscribeOn(Schedulers.virtualThreads());
    }

    private List<Observable<TimedResponse>> concurrentSources(
            final List<WeatherRequest> requests) {
        return List.copyOf(requests).stream().map(this::concurrentLookup)
                .toList();
    }

    /**
     * Associates a completed response with the original request slot.
     * @param index the request position
     * @param response the successful response
     */
    private record IndexedResponse(int index, TimedResponse response) {
    }
}
