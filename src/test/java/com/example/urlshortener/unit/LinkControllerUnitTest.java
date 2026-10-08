package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.urlshortener.api.LinkController;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.StatsService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.EffectiveStatus;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.port.RateLimiter;
import com.example.urlshortener.support.TestProperties;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** B4: short_url never gets a double slash, whether or not app.base-url ends with "/". */
class LinkControllerUnitTest {

  @ParameterizedTest
  @ValueSource(strings = {"https://sho.rt", "https://sho.rt/"})
  void fr1_7_shortUrlIsBaseUrlPlusCode(String baseUrl) {
    AppProperties d = TestProperties.defaults();
    AppProperties props =
        new AppProperties(
            baseUrl,
            d.links(),
            d.cache(),
            d.rateLimit(),
            d.analytics(),
            d.http(),
            d.stats(),
            d.health());
    LinkService links = mock(LinkService.class);
    AuthenticatedOwner owner = new AuthenticatedOwner(1L, 1L);
    Link link =
        new Link(
            1L,
            "aB3dE7x",
            1L,
            "https://a.io",
            "https://a.io",
            false,
            LinkStatus.ACTIVE,
            Instant.parse("2026-10-07T21:00:00Z"),
            null,
            null,
            6L);
    when(links.get(owner, "aB3dE7x")).thenReturn(link);
    when(links.statusOf(link)).thenReturn(EffectiveStatus.ACTIVE);
    LinkController controller =
        new LinkController(links, mock(StatsService.class), mock(RateLimiter.class), props);

    assertThat(controller.get(owner, "aB3dE7x").shortUrl()).isEqualTo("https://sho.rt/aB3dE7x");
  }
}
