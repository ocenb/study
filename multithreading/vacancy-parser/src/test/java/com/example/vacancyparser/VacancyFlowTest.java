package com.example.vacancyparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vacancy-flow;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class VacancyFlowTest {
    private static final String FIRST = "https://career.habr.com/vacancies/101";
    private static final String SECOND = "https://career.habr.com/vacancies/102";
    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestTemplate http;

    @Test
    void scheduledBatchDeduplicatesLinksAndPreservesResultsAfterFailedRefresh() throws Exception {
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(http).ignoreExpectOrder(true).build();
        upstream.expect(requestTo(FIRST)).andRespond(withSuccess(
                vacancyHtml("Java разработчик", "2026-08-12", "\"Москва\"", "от 160 000 ₽", """
                        <div>
                          <h3>Ожидания от кандидата</h3>
                          <ul><li>Уверенное знание Java 21</li><li>Опыт разработки от двух лет</li></ul>
                          <p><strong>Что предстоит делать:</strong></p>
                          <ul><li>Разрабатывать сервисы</li></ul>
                        </div>
                        """), HTML));
        upstream.expect(requestTo(SECOND)).andRespond(withSuccess(
                vacancyHtml("QA инженер", "2026-08-13", "null", "", """
                        <div><div>
                          <p><strong>Мы ждём от тебя:</strong></p>
                          <ul><li><p>Знание HTTP</p></li></ul>
                          <p><strong>Что мы обеспечим:</strong></p>
                          <p>Гибкий график</p>
                        </div></div>
                        """), HTML));

        JsonNode jobs = submit("""
                {"urls":["%s", "%s?from=list#details", "%s"]}
                """.formatted(FIRST, FIRST, SECOND));
        String firstJobId = jobs.get(0).path("id").asText();
        String secondJobId = jobs.get(2).path("id").asText();
        assertThat(jobs.get(1).path("id").asText()).isEqualTo(firstJobId);
        assertThat(jobs.get(1).path("url").asText()).isEqualTo(FIRST);
        awaitStatus(firstJobId, "SUCCEEDED");
        awaitStatus(secondJobId, "SUCCEEDED");
        upstream.verify();

        mvc.perform(get("/answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("102"))
                .andExpect(jsonPath("$[0].city").isEmpty())
                .andExpect(jsonPath("$[0].salary").isEmpty())
                .andExpect(jsonPath("$[0].requirements").value("Java, Git\n\n- Знание HTTP"))
                .andExpect(jsonPath("$[1].title").value("Java разработчик"))
                .andExpect(jsonPath("$[1].company").value("Учебная компания"))
                .andExpect(jsonPath("$[1].salary").value("от 160 000 ₽"))
                .andExpect(jsonPath("$[1].requirements").value(
                        "Java, Git\n\n- Уверенное знание Java 21\n- Опыт разработки от двух лет"))
                .andExpect(jsonPath("$[1].publishedAt").value("2026-08-12"));
        mvc.perform(get("/answer").param("sortBy", "city").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Москва"));

        upstream.reset();
        upstream.expect(requestTo(FIRST)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        String retryId = submit("{\"urls\":[\"" + FIRST + "\"]}").get(0).path("id").asText();
        assertThat(retryId).isNotEqualTo(firstJobId);
        awaitStatus(retryId, "FAILED");
        mvc.perform(get("/answer/{id}", retryId)).andExpect(jsonPath("$.error").value("Хабр Карьера вернула HTTP 404"));
        mvc.perform(get("/answer").param("sortBy", "title").param("direction", "asc"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("101"));
        upstream.verify();
    }

    @Test
    void rejectsLinksToOtherHostsAndUnknownSortParameters() throws Exception {
        mvc.perform(post("/post").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"urls":["https://career.habr.com.example.org/vacancies/101"]}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/answer").param("sortBy", "salary")).andExpect(status().isBadRequest());
    }

    private JsonNode submit(String body) throws Exception {
        return json.readTree(mvc.perform(post("/post").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsByteArray());
    }

    private void awaitStatus(String jobId, String expected) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                mvc.perform(get("/answer/{id}", jobId))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected)));
    }

    private String vacancyHtml(String title, String date, String cityJson, String salary, String description) {
        return """
                <!doctype html>
                <meta charset="UTF-8">
                <script type="application/ld+json">
                  {"@type":"JobPosting","title":"%s","datePosted":"%s",
                   "hiringOrganization":{"name":"Учебная компания"},"jobLocation":{"address":%s}}
                </script>
                <div class="vacancy-header__salary"><div class="basic-salary">%s</div></div>
                <section class="content-section"><h2>Требования</h2><div class="vacancy-meta">
                  <div class="basic-chip">Java</div><div class="basic-chip">Git</div>
                </div></section>
                <div class="vacancy-description__text">%s</div>
                """.formatted(title, date, cityJson, salary, description);
    }
}
