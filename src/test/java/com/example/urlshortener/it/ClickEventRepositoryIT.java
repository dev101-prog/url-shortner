package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.TestData;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClickEventRepositoryIT extends AbstractPostgresIT {

  private static final Instant T0 = Instant.parse("2026-10-07T21:00:00Z");
  private static final String HASH =
      "081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5";

  private final ClickEventRepository clicks;
  private final LinkRepository links;
  private final JdbcClient jdbc;
  private long linkA;
  private long linkB;

  @Autowired
  ClickEventRepositoryIT(ClickEventRepository clicks, LinkRepository links, JdbcClient jdbc) {
    this.clicks = clicks;
    this.jdbc = jdbc;
    this.links = links;
  }

  @BeforeEach
  void data() {
    jdbc.sql("TRUNCATE click_events, links, api_keys, owners RESTART IDENTITY").update();
    long owner = new TestData(jdbc).owner("alice");
    linkA =
        links
            .insertIfCodeFree("linkAAA", owner, "https://a.io", "https://a.io", false, null)
            .orElseThrow()
            .id();
    linkB =
        links
            .insertIfCodeFree("linkBBB", owner, "https://b.io", "https://b.io", false, null)
            .orElseThrow()
            .id();
  }

  private static ClickEvent click(long linkId, int secondsOffset, boolean bot) {
    return new ClickEvent(
        linkId, T0.plusSeconds(secondsOffset), bot ? null : "t.co", "curl/8.4.0", bot, HASH, null);
  }

  private Map<String, Object> counts() {
    return jdbc.sql(
            "SELECT (SELECT click_count FROM links WHERE id = :a) AS a_count,"
                + " (SELECT click_count FROM links WHERE id = :b) AS b_count,"
                + " (SELECT count(*) FROM click_events) AS events")
        .param("a", linkA)
        .param("b", linkB)
        .query()
        .singleRow();
  }

  @Test
  void fr7_2_saveBatchInsertsEventsAndIncrementsClickCountsTogether() {
    clicks.saveBatch(
        List.of(
            click(linkA, 0, false),
            click(linkB, 1, true),
            click(linkA, 2, true),
            click(linkA, 3, false),
            click(linkB, 4, false)));

    assertThat(counts())
        .containsEntry("a_count", 3L)
        .containsEntry("b_count", 2L)
        .containsEntry("events", 5L);
    Map<String, Object> first =
        jdbc.sql("SELECT * FROM click_events ORDER BY id LIMIT 1").query().singleRow();
    assertThat(first)
        .containsEntry("link_id", linkA)
        .containsEntry("referrer_host", "t.co")
        .containsEntry("user_agent", "curl/8.4.0")
        .containsEntry("is_bot", false)
        .containsEntry("ip_hash", HASH)
        .containsEntry("country", null);
    assertThat(
            jdbc.sql("SELECT clicked_at FROM click_events ORDER BY id LIMIT 1")
                .query(OffsetDateTime.class)
                .single()
                .toInstant())
        .isEqualTo(T0);

    clicks.saveBatch(List.of(click(linkA, 10, false)));

    assertThat(counts()).containsEntry("a_count", 4L).containsEntry("events", 6L);
  }

  @Test
  void fr7_6_failedBatchRollsBackEventsAndCounts() {
    List<ClickEvent> batch = List.of(click(linkA, 0, false), click(999_999L, 1, false));

    assertThatThrownBy(() -> clicks.saveBatch(batch)).isInstanceOf(DataAccessException.class);

    assertThat(counts()).containsEntry("a_count", 0L).containsEntry("events", 0L);
  }

  @Test
  void emptyBatchIsANoOp() {
    clicks.saveBatch(List.of());

    assertThat(counts()).containsEntry("a_count", 0L).containsEntry("events", 0L);
  }
}
