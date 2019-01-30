package com.github.letylo;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author leticia
 */
public class StubForSyncTest {

    @Rule public WireMockRule wireMock = new WireMockRule(0);
    
    private ExecutorService executor;
    private Client client;
    private WebTarget target;
    private String url;
    private Queue<String> queue;
    private CountDownLatch latch;
    
    @Before
    public void setUp() {
        
        url = "http://localhost:" + wireMock.port();
        queue = new ConcurrentLinkedDeque<>();
        
        wireMock.stubFor(get(urlMatching("/"))
                .inScenario("Scenario")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("FirstState")
                .willReturn(
                        WireMock.aResponse()
                                .withBody("1")
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.TEXT_PLAIN)
        ));

        wireMock.stubFor(get(urlMatching("/"))
                .inScenario("Scenario")
                .whenScenarioStateIs("FirstState")
                .willReturn(
                        WireMock.aResponse()
                                .withBody("2")
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.TEXT_PLAIN)
        ));
       
        wireMock.start();

        executor = Executors.newCachedThreadPool();   
    }
    
    @After
    public void tearDown() {
        
        executor.shutdown();
    }
     
    private long shouldThrowMuchRequests() throws InterruptedException {

        client = ClientBuilder.newClient();
        target = client.target(url);
        
        int size = 1000;
        latch = new CountDownLatch(size);
        
        long start = System.nanoTime();
        
        for (int i = 0; i < size; i++) {
            
            executor.submit(() -> {
                
                try {
                    String response = target
                            .request(MediaType.TEXT_PLAIN)
                            .get(String.class);
                    
                    queue.add(response);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        long end = System.nanoTime();
        long difference = end - start;
        latch.await(5, TimeUnit.SECONDS);

       
        then(queue).containsOnlyOnce("1");
        
        return difference;
    }
    
    @Test
    public void runTwoHundredTimes() throws InterruptedException {
        
        long difference;
        long timeToDoARequest;
        
        for (int i = 0; i < 10; i++) {
            
            difference = shouldThrowMuchRequests();
            Thread.sleep(1000L);
        }  
        
        for (int i = 0; i < 100; i++) {
            
            difference = shouldThrowMuchRequests();
            Thread.sleep(1000L);
            timeToDoARequest = TimeUnit.NANOSECONDS.toMicros(difference);
            System.out.println(timeToDoARequest);
        } 
    }
}
