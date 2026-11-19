package com.tourguide.common.config;

import com.tourguide.place.Place;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Provides verified open-licensed Wikimedia Commons imagery and deterministic
 * demo rating helpers for seeded Izmir places, routes, and quests.
 */
final class DemoMediaCatalog {

    static final String IMG_CLOCK_TOWER =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2e/Izmir_square_clock_tower.jpg/960px-Izmir_square_clock_tower.jpg";
    static final String IMG_ASANSOR =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/Asansor_yandan.JPG/960px-Asansor_yandan.JPG";
    static final String IMG_KEMERALTI =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/63/TR_Izmir_asv2020-02_img06_Kemeralt%C4%B1.jpg/960px-TR_Izmir_asv2020-02_img06_Kemeralt%C4%B1.jpg";
    static final String IMG_KORDON =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/50/Cumhuriyet_Square.jpg/960px-Cumhuriyet_Square.jpg";
    static final String IMG_KADIFEKALE =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0f/Kadifekale_IzmirTurkey_EUnluBlogspot.jpg/960px-Kadifekale_IzmirTurkey_EUnluBlogspot.jpg";
    static final String IMG_AGORA =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c3/Izmir_Agora.JPG/960px-Izmir_Agora.JPG";
    static final String IMG_EPHESUS =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c6/Library_of_Celsus_side_view.jpg/960px-Library_of_Celsus_side_view.jpg";
    static final String IMG_BASMANE =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/aa/TR_Izmir_asv2020-02_img46_Basmane_Station.jpg/960px-TR_Izmir_asv2020-02_img46_Basmane_Station.jpg";
    static final String IMG_ALSANCAK =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/15/TR_Izmir_asv2020-02_img19_Alsancak_Station.jpg/960px-TR_Izmir_asv2020-02_img19_Alsancak_Station.jpg";
    static final String IMG_ARCH_MUSEUM =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1e/Museo_archeologico_di_izmir%2C_veduta.JPG/960px-Museo_archeologico_di_izmir%2C_veduta.JPG";
    static final String IMG_ART_MUSEUM =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/37/%C4%B0zmir_Tarih_ve_Sanat_M%C3%BCzesi.jpg/960px-%C4%B0zmir_Tarih_ve_Sanat_M%C3%BCzesi.jpg";
    static final String IMG_APIKAM =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/69/Ahmet_Piri%C5%9Ftina_City_Archives_and_Museum_01.jpg/960px-Ahmet_Piri%C5%9Ftina_City_Archives_and_Museum_01.jpg";
    static final String IMG_ELHAMRA =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2e/Elhamra.jpg/960px-Elhamra.jpg";
    static final String IMG_KULTURPARK =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3e/K%C3%BClt%C3%BCrpark%2C_May_2019_%281%29.jpg/960px-K%C3%BClt%C3%BCrpark%2C_May_2019_%281%29.jpg";
    static final String IMG_LIBRARY =
            "https://upload.wikimedia.org/wikipedia/commons/a/a4/%C4%B0zmir_Milli_K%C3%BCt%C3%BCphanesi3.jpg";
    static final String IMG_KONAK_MOSQUE =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/aa/TR_Izmir_asv2020-02_img29_Konak_Mosque.jpg/960px-TR_Izmir_asv2020-02_img29_Konak_Mosque.jpg";
    static final String IMG_POLYCARP =
            "https://upload.wikimedia.org/wikipedia/commons/1/13/Polikarp_%282%29.jpg";
    static final String IMG_SYNAGOGUE =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/03/TR_Izmir_asv2020-02_img51_Bet_Israel_Synagogue.jpg/960px-TR_Izmir_asv2020-02_img51_Bet_Israel_Synagogue.jpg";
    static final String IMG_CESME =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7d/Cesme_Castle%2C_Turkey_-_panoramio.jpg/960px-Cesme_Castle%2C_Turkey_-_panoramio.jpg";
    static final String IMG_PERGAMON =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/41/Acropolis_-_Bergama_%28Pergamon%29_-_Turkey_-_10_%285747249729%29.jpg/960px-Acropolis_-_Bergama_%28Pergamon%29_-_Turkey_-_10_%285747249729%29.jpg";
    static final String IMG_KIZLARAGASI =
            "https://upload.wikimedia.org/wikipedia/commons/7/73/K%C4%B1zlara%C4%9Fas%C4%B1_Han%C4%B13.jpg";

    private record LandmarkRule(String imageUrl, boolean iconic, String... patterns) {}

    private static final List<LandmarkRule> LANDMARK_RULES = List.of(
            new LandmarkRule(IMG_CLOCK_TOWER, true, "saat kulesi", "clock tower"),
            new LandmarkRule(IMG_ASANSOR, true, "asansor"),
            new LandmarkRule(IMG_KIZLARAGASI, true, "kizlaragasi"),
            new LandmarkRule(IMG_KEMERALTI, true, "kemeralti"),
            new LandmarkRule(IMG_KORDON, true, "kordon"),
            new LandmarkRule(IMG_KADIFEKALE, true, "kadifekale"),
            new LandmarkRule(IMG_AGORA, true, "agora"),
            new LandmarkRule(IMG_EPHESUS, true, "efes", "ephes"),
            new LandmarkRule(IMG_BASMANE, true, "basmane gari", "basmane station"),
            new LandmarkRule(IMG_ALSANCAK, false, "alsancak gar", "tekel"),
            new LandmarkRule(IMG_APIKAM, true, "apikam", "piristina"),
            new LandmarkRule(IMG_ARCH_MUSEUM, true, "arkeoloji muzesi"),
            new LandmarkRule(IMG_ART_MUSEUM, true, "tarih ve sanat"),
            new LandmarkRule(IMG_ELHAMRA, true, "elhamra"),
            new LandmarkRule(IMG_KULTURPARK, true, "kulturpark"),
            new LandmarkRule(IMG_LIBRARY, true, "milli kutuphane"),
            new LandmarkRule(IMG_POLYCARP, false, "polikarp"),
            new LandmarkRule(IMG_SYNAGOGUE, false, "bet israel", "beth israel"),
            new LandmarkRule(IMG_CESME, false, "cesme kalesi"),
            new LandmarkRule(IMG_PERGAMON, false, "bergama", "pergamon")
    );

    private static final List<String> MUSEUM_POOL = List.of(IMG_ART_MUSEUM, IMG_ARCH_MUSEUM, IMG_APIKAM);
    private static final List<String> RELIGIOUS_POOL = List.of(IMG_KONAK_MOSQUE);
    private static final List<String> KALE_POOL = List.of(IMG_KADIFEKALE, IMG_CESME);
    private static final List<String> ANCIENT_POOL = List.of(IMG_AGORA, IMG_EPHESUS, IMG_PERGAMON);
    private static final List<String> BAZAAR_FOOD_POOL = List.of(IMG_KIZLARAGASI, IMG_KEMERALTI);
    private static final List<String> MANSION_HOTEL_POOL = List.of(IMG_ELHAMRA, IMG_ASANSOR, IMG_APIKAM, IMG_LIBRARY);
    private static final List<String> STATION_POOL = List.of(IMG_BASMANE, IMG_ALSANCAK);
    private static final List<String> DEFAULT_HERITAGE_POOL = List.of(
            IMG_ELHAMRA, IMG_KEMERALTI, IMG_ASANSOR, IMG_BASMANE, IMG_KIZLARAGASI
    );

    private static final Map<String, List<String>> CATEGORY_POOLS = Map.ofEntries(
            Map.entry("muze", MUSEUM_POOL),
            Map.entry("museum", MUSEUM_POOL),
            Map.entry("cami", RELIGIOUS_POOL),
            Map.entry("turbe", RELIGIOUS_POOL),
            Map.entry("religious", RELIGIOUS_POOL),
            Map.entry("mosque", RELIGIOUS_POOL),
            Map.entry("kilise", List.of(IMG_POLYCARP)),
            Map.entry("sinagog", List.of(IMG_SYNAGOGUE)),
            Map.entry("kale", KALE_POOL),
            Map.entry("antik_alan", ANCIENT_POOL),
            Map.entry("park", List.of(IMG_KULTURPARK)),
            Map.entry("sahil_seridi", List.of(IMG_KORDON)),
            Map.entry("beach", List.of(IMG_KORDON)),
            Map.entry("carsi", List.of(IMG_KEMERALTI, IMG_KIZLARAGASI)),
            Map.entry("kutuphane", List.of(IMG_LIBRARY)),
            Map.entry("tren_gari", STATION_POOL),
            Map.entry("sanayi_mirasi", List.of(IMG_ALSANCAK, IMG_BASMANE)),
            Map.entry("hamam", BAZAAR_FOOD_POOL),
            Map.entry("kiraathane", BAZAAR_FOOD_POOL),
            Map.entry("cafe", BAZAAR_FOOD_POOL),
            Map.entry("restaurant", BAZAAR_FOOD_POOL),
            Map.entry("konak", MANSION_HOTEL_POOL),
            Map.entry("tarihi_otel", MANSION_HOTEL_POOL),
            Map.entry("okul", MANSION_HOTEL_POOL),
            Map.entry("kule", List.of(IMG_CLOCK_TOWER))
    );

    private DemoMediaCatalog() {
    }

    static String resolvePlacePhotoUrl(Place place) {
        String normalizedNames = combinedPlaceNames(place);
        if (normalizedNames.contains("konak") && normalizedNames.contains("cam")) {
            return IMG_KONAK_MOSQUE;
        }
        for (LandmarkRule rule : LANDMARK_RULES) {
            if (matchesAny(normalizedNames, rule.patterns())) {
                return rule.imageUrl();
            }
        }

        String categoryKey = normalize(place.getCategory());
        List<String> pool = CATEGORY_POOLS.getOrDefault(categoryKey, DEFAULT_HERITAGE_POOL);
        int index = Math.floorMod(seedKey(place).hashCode(), pool.size());
        return pool.get(index);
    }

    static boolean isIconicLandmark(Place place) {
        String normalizedNames = combinedPlaceNames(place);
        if (normalizedNames.contains("konak") && normalizedNames.contains("cam")) {
            return true;
        }
        for (LandmarkRule rule : LANDMARK_RULES) {
            if (rule.iconic() && matchesAny(normalizedNames, rule.patterns())) {
                return true;
            }
        }
        return false;
    }

    static double deterministicDemoRating(Place place) {
        int hash = Math.floorMod(seedKey(place).hashCode(), 100);
        if (isIconicLandmark(place)) {
            return Math.round((4.7 + (hash % 3) * 0.1) * 10.0) / 10.0;
        }
        return Math.round((4.2 + (hash % 5) * 0.1) * 10.0) / 10.0;
    }

    static int deterministicReviewCount(Place place) {
        int hash = Math.floorMod(seedKey(place).hashCode(), 1000);
        if (isIconicLandmark(place)) {
            return 120 + (hash % 191);
        }
        return 18 + (hash % 68);
    }

    static int deterministicPopularityScore(Place place) {
        int hash = Math.floorMod(seedKey(place).hashCode(), 100);
        if (isIconicLandmark(place)) {
            return 90 + (hash % 9);
        }
        return 60 + (hash % 23);
    }

    static String resolveRouteThumbnailUrl(String routeName) {
        String key = normalize(routeName);
        if (key.contains("kordon") || key.contains("alsancak")) {
            return IMG_KORDON;
        }
        if (key.contains("panorama") || key.contains("asansor") || key.contains("kadifekale")) {
            return IMG_ASANSOR;
        }
        return IMG_KEMERALTI;
    }

    static String resolveQuestThumbnailUrl(String questTitle) {
        String key = normalize(questTitle);
        if (key.contains("han")) {
            return IMG_KIZLARAGASI;
        }
        if (key.contains("gate") || key.contains("agora") || key.contains("ancient")) {
            return IMG_AGORA;
        }
        if (key.contains("clock") || key.contains("saat")) {
            return IMG_CLOCK_TOWER;
        }
        if (key.contains("seaside") || key.contains("flavor") || key.contains("sunset")) {
            return IMG_KORDON;
        }
        return IMG_ASANSOR;
    }

    private static String combinedPlaceNames(Place place) {
        return normalize(String.join(" ",
                Objects.toString(place.getName(), ""),
                Objects.toString(place.getNameTr(), ""),
                Objects.toString(place.getNameEn(), "")
        ));
    }

    private static String seedKey(Place place) {
        if (place.getId() != null) {
            return place.getId().toString();
        }
        return combinedPlaceNames(place);
    }

    private static boolean matchesAny(String haystack, String... patterns) {
        for (String pattern : patterns) {
            if (haystack.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String lower = Objects.toString(value, "").toLowerCase(Locale.forLanguageTag("tr"));
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed
                .replace('ı', 'i')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ş', 's')
                .replace('ö', 'o')
                .replace('ç', 'c');
    }
}
