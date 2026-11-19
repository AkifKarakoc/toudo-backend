package com.tourguide.common.config;

import com.tourguide.place.Place;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DemoMediaCatalogTest {

    @Test
    void resolvePlacePhotoUrl_matchesIconicIzmirLandmarksSpecifically() {
        Place clockTower = Place.builder().name("İzmir Saat Kulesi").category("kule").build();
        Place asansor = Place.builder().name("Tarihi Asansör").category("tarihi_yapi").build();
        Place agora = Place.builder().name("Agora Roma Hamamı").category("hamam").build();
        Place apikam = Place.builder().name("Ahmet Piriştina Kent Arşivi ve Müzesi(APİKAM)").category("müze").build();

        assertThat(DemoMediaCatalog.resolvePlacePhotoUrl(clockTower)).isEqualTo(DemoMediaCatalog.IMG_CLOCK_TOWER);
        assertThat(DemoMediaCatalog.resolvePlacePhotoUrl(asansor)).isEqualTo(DemoMediaCatalog.IMG_ASANSOR);
        assertThat(DemoMediaCatalog.resolvePlacePhotoUrl(agora)).isEqualTo(DemoMediaCatalog.IMG_AGORA);
        assertThat(DemoMediaCatalog.resolvePlacePhotoUrl(apikam)).isEqualTo(DemoMediaCatalog.IMG_APIKAM);
    }

    @Test
    void resolvePlacePhotoUrl_fallsBackCleanlyForAnyCategoryAndAssignsHigherRatingsToIconicLandmarks() {
        Place clockTower = Place.builder().name("İzmir Saat Kulesi").category("kule").build();
        clockTower.setId(UUID.randomUUID());
        Place neighborhoodHouse = Place.builder().name("Sıradan Tarihi Ev").category("konak").build();
        neighborhoodHouse.setId(UUID.randomUUID());

        assertThat(DemoMediaCatalog.resolvePlacePhotoUrl(neighborhoodHouse)).startsWith("https://");
        assertThat(DemoMediaCatalog.deterministicDemoRating(clockTower)).isBetween(4.7, 4.9);
        assertThat(DemoMediaCatalog.deterministicDemoRating(neighborhoodHouse)).isBetween(4.2, 4.6);
        assertThat(DemoMediaCatalog.resolveRouteThumbnailUrl("Historic Konak & Kemeraltı Trail"))
                .isEqualTo(DemoMediaCatalog.IMG_KEMERALTI);
        assertThat(DemoMediaCatalog.resolveQuestThumbnailUrl("Clock Tower Challenge"))
                .isEqualTo(DemoMediaCatalog.IMG_CLOCK_TOWER);
    }
}
