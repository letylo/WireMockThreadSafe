package com.github.letylo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author leticia
 */
@RunWith(Parameterized.class)
public class StubForSyncTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(0);

    private ExecutorService executor;
    private Client client;

    @Parameter(0)
    public int size;

    @Before
    public void setUp() {
        
        List<String> states = IntStream.rangeClosed(2, size)
                .mapToObj(String::valueOf).collect(Collectors.toList());
        String current = Scenario.STARTED;

        for (String nextState : states) {

            wireMock.stubFor(stubState(current).willSetStateTo(nextState));
            current = nextState;
        }

        wireMock.stubFor(stubState(current));
        client = ClientBuilder.newClient();
        executor = Executors.newCachedThreadPool();
    }

    private ScenarioMappingBuilder stubState(String current) {

        return get(urlMatching("/")).inScenario("Scenario")
                .whenScenarioStateIs(current)
                .willReturn(aResponse().withBody(current).withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_PLAIN));
    }

    @After
    public void tearDown() {

        executor.shutdown();
        client.close();
    }

    private long shouldThrowMuchRequests(int size) throws InterruptedException {
        
        String url = "http://localhost:" + wireMock.port();
        WebTarget target = client.target(url);
        CountDownLatch latch = new CountDownLatch(size);
        Queue<String> queue = new ConcurrentLinkedDeque<>();

        long start = System.nanoTime();

        for (int i = 0; i < size; i++) {

            executor.submit(() -> {

                try {
                    String response = target.request(MediaType.TEXT_PLAIN)
                            .get(String.class);
                    queue.add(response);
                } catch (Exception e) {
                    // e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        long end = System.nanoTime();
        long difference = end - start;
        latch.await(5, TimeUnit.SECONDS);

        then(queue).doesNotHaveDuplicates().hasSize(size);

        return difference;
    }

    @Parameters(name = "count= {0}")
    public static List<Integer> data() {
        
        List<Integer> values = new ArrayList<>();
        
        for (int i = 10; i < 1000; i *= 10) {
            
            for (int j = 1; j < 10; j++) {
               
                values.add(i*j);
            }
        }

        values.add(1000);
        
        return values;
    }

    @Test
    public void runTimes() throws InterruptedException {

//        System.out.println("Size: " + size);
        List<Long> times = new ArrayList<>();
        
        // to warm up
        for (int i = 0; i < 5; i++) {
            
            shouldThrowMuchRequests(size);
            wireMock.resetRequests();
            wireMock.resetScenarios();
        }

        for (int i = 0; i < 10; i++) {

            long difference = shouldThrowMuchRequests(size);
            long timeToDoARequest = TimeUnit.NANOSECONDS.toMicros(difference);
//            System.out.println(timeToDoARequest);
            times.add(difference);
            wireMock.resetRequests();
            wireMock.resetScenarios();
            Thread.sleep(1000L);
        }

        long average = TimeUnit.NANOSECONDS.toMicros((long) times.stream()
                .mapToLong(Long::longValue).average().getAsDouble());
        
        System.out.println(size + " - " + average);
    }
}
