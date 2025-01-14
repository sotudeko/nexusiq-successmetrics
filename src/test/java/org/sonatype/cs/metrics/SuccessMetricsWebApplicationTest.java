package org.sonatype.cs.metrics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.approvaltests.Approvals;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.namer.NamerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

// https://spring.io/guides/gs/testing-web/
@SpringBootTest(
        properties = {"data.dir=src/test/resources", "spring.profiles.active=web"},
        webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class SuccessMetricsWebApplicationTest {

    @Autowired private SuccessMetricsApplication controller;

    @LocalServerPort private int port;

    @Autowired private MockMvc mockMvc;

    private String removeLine(String inputString, int lineToRemove) {
        List<String> lineArray =
                new LinkedList<String>(
                        Arrays.asList(inputString.split(System.getProperty("line.separator"))));
        lineArray.remove(lineToRemove - 1);
        String outputString = String.join(System.getProperty("line.separator"), lineArray);
        return outputString;
    }

    @Test
    public void contextLoadsTest() throws Exception {
        assertNotNull(controller);
    }

    @Autowired private TestRestTemplate restTemplate;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "/home",
                "home.html",
                "summary",
                "summary.html",
                "applications",
                "applications.html",
                "securityviolations",
                "securityviolations.html",
                "licenseviolations",
                "licenseviolations.html",
                "violationsage",
                "violationsage.html",
                "evaluations",
                "evaluations.html",
                "waivers",
                "waivers.html",
                "analysis",
                "analysis.html",
                "appsummary?appname=webgoatj",
                "appsummary.html?appname=webgoatj"
            })
    public void checkPageIsReachable(String page) {
        try {
            this.mockMvc
                    .perform(MockMvcRequestBuilders.get("http://localhost:" + port + "/" + page))
                    .andExpect(MockMvcResultMatchers.status().isOk());

        } catch (Exception e) {
            fail();
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "home, 23",
                "summary.html, 23",
                "applications.html, 23",
                "securityviolations.html, 23",
                "licenseviolations.html, 23",
                "violationsage.html?date=2020-12-15, 24",
                "evaluations.html?date=2021-12-15, 23",
                "waivers.html, 24",
                "analysis.html, 23",
                "appsummary.html?appname=webgoatj, 23",
                "compare, 23"
            })
    public void checkPageContents(String page, String lineToRemove) throws Exception {
        try (NamedEnvironment en = NamerFactory.withParameters(page)) {
            String pageContents =
                    this.restTemplate.getForObject(
                            "http://localhost:" + port + "/" + page, String.class);
            pageContents = removeLine(pageContents, Integer.parseInt(lineToRemove));
            Approvals.verify(pageContents);
        }
    }
}
