package com.tourguide.common.config;

import com.tourguide.badge.Badge;
import com.tourguide.badge.BadgeRepository;
import com.tourguide.badge.BadgeTier;
import com.tourguide.badge.UserBadge;
import com.tourguide.badge.UserBadgeRepository;
import com.tourguide.common.enums.Role;
import com.tourguide.place.Place;
import com.tourguide.place.PlaceRepository;
import com.tourguide.quest.*;
import com.tourguide.review.Review;
import com.tourguide.review.ReviewRepository;
import com.tourguide.review.ReviewStatus;
import com.tourguide.route.Route;
import com.tourguide.route.RoutePlace;
import com.tourguide.route.RoutePlaceRepository;
import com.tourguide.route.RouteRepository;
import com.tourguide.todo.TodoItem;
import com.tourguide.todo.TodoRepository;
import com.tourguide.user.Favorite;
import com.tourguide.user.FavoriteRepository;
import com.tourguide.user.IUserService;
import com.tourguide.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final IUserService userService;
    private final PlaceRepository placeRepository;
    private final BadgeRepository badgeRepository;
    private final QuestRepository questRepository;
    private final QuestStepRepository questStepRepository;
    private final UserQuestRepository userQuestRepository;
    private final ReviewRepository reviewRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final RouteRepository routeRepository;
    private final RoutePlaceRepository routePlaceRepository;
    private final FavoriteRepository favoriteRepository;
    private final TodoRepository todoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    private static final String SUPERADMIN_EMAIL = "superadmin@tourguide.com";
    private static final String SUPERADMIN_PASSWORD = "Admin@1234";
    private static final String DEMO_EMAIL = "demo@toudo.dev";
    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final String MOCK_PASSWORD = "User@1234";

    @Override
    public void run(ApplicationArguments args) {
        seedSuperAdmin();

        if (seedDemoData) {
            seedMockData();
        }
    }

    private void seedSuperAdmin() {
        if (!userService.existsByEmail(SUPERADMIN_EMAIL)) {
            User admin = User.builder()
                    .email(SUPERADMIN_EMAIL)
                    .passwordHash(passwordEncoder.encode(SUPERADMIN_PASSWORD))
                    .firstName("Super")
                    .lastName("Admin")
                    .role(Role.SUPERADMIN)
                    .preferredLanguage("tr")
                    .build();
            userService.createUser(admin);
            log.info("SuperAdmin user created: {}", SUPERADMIN_EMAIL);
        } else {
            log.debug("SuperAdmin user already exists, skipping seed.");
        }
    }

    // ═══════════════════════════════════════════
    //  Demo & Mock Data Seeding
    // ═══════════════════════════════════════════

    private void seedMockData() {
        log.info("Checking/seeding Izmir demo dataset...");
        LocalDateTime now = LocalDateTime.now();

        User demoUser = ensureDemoUser(now);
        List<User> mockUsers = seedUsers(passwordEncoder.encode(MOCK_PASSWORD), now);
        List<User> allUsers = new ArrayList<>();
        allUsers.add(demoUser);
        allUsers.addAll(mockUsers);

        List<Place> places = seedPlaces();
        List<Badge> badges = seedBadges();
        List<Quest> quests = seedQuests(badges, places);
        seedRoutes(places);
        enrichDemoMediaAndRatings();
        seedUserQuests(allUsers, quests, now);
        seedReviews(allUsers, places, now);
        seedUserBadges(allUsers, badges, now);
        seedDemoFavoritesAndTodos(demoUser, places);

        log.info("Demo dataset ready: {} places, {} badges, {} quests, {} routes",
                places.size(), badges.size(), quests.size(), routeRepository.count());
    }

    private void enrichDemoMediaAndRatings() {
        List<Place> updatedPlaces = new ArrayList<>();
        for (Place place : placeRepository.findByIsActiveTrue()) {
            boolean dirty = false;
            if (place.getPhotoUrl() == null || place.getPhotoUrl().isBlank()) {
                place.setPhotoUrl(DemoMediaCatalog.resolvePlacePhotoUrl(place));
                dirty = true;
            }
            if (place.getAvgRating() == null || place.getAvgRating() <= 0.0) {
                place.setAvgRating(DemoMediaCatalog.deterministicDemoRating(place));
                if (place.getReviewCount() == null || place.getReviewCount() <= 0) {
                    place.setReviewCount(DemoMediaCatalog.deterministicReviewCount(place));
                }
                if (place.getPopularityScore() == null || place.getPopularityScore() <= 10) {
                    place.setPopularityScore(DemoMediaCatalog.deterministicPopularityScore(place));
                }
                dirty = true;
            }
            if (dirty) {
                updatedPlaces.add(place);
            }
        }
        if (!updatedPlaces.isEmpty()) {
            placeRepository.saveAll(updatedPlaces);
            log.info("  Enriched media & ratings for {} active places", updatedPlaces.size());
        }

        List<Route> updatedRoutes = new ArrayList<>();
        for (Route route : routeRepository.findByIsActiveTrue()) {
            if (route.getThumbnailUrl() == null || route.getThumbnailUrl().isBlank()) {
                route.setThumbnailUrl(DemoMediaCatalog.resolveRouteThumbnailUrl(route.getName()));
                updatedRoutes.add(route);
            }
        }
        if (!updatedRoutes.isEmpty()) {
            routeRepository.saveAll(updatedRoutes);
            log.info("  Enriched thumbnails for {} active routes", updatedRoutes.size());
        }

        List<Quest> updatedQuests = new ArrayList<>();
        for (Quest quest : questRepository.findByIsActiveTrue()) {
            if (quest.getThumbnailUrl() == null || quest.getThumbnailUrl().isBlank()) {
                quest.setThumbnailUrl(DemoMediaCatalog.resolveQuestThumbnailUrl(quest.getTitle()));
                updatedQuests.add(quest);
            }
        }
        if (!updatedQuests.isEmpty()) {
            questRepository.saveAll(updatedQuests);
            log.info("  Enriched thumbnails for {} active quests", updatedQuests.size());
        }
    }

    private User ensureDemoUser(LocalDateTime now) {
        return userService.findByEmailActive(DEMO_EMAIL).orElseGet(() -> {
            User demo = User.builder()
                    .email(DEMO_EMAIL)
                    .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .firstName("Demo")
                    .lastName("Traveler")
                    .role(Role.TOURIST)
                    .level(5)
                    .expPoints(520)
                    .preferredLanguage("tr")
                    .build();
            User saved = userService.createUser(demo);
            jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?",
                    Timestamp.valueOf(now.minusDays(14)), saved.getId());
            log.info("  Seeded demo tourist user: {}", DEMO_EMAIL);
            return saved;
        });
    }

    private List<User> seedUsers(String encodedPassword, LocalDateTime now) {
        record UserSeed(String email, String firstName, String lastName, Role role,
                        int level, int expPoints, int daysAgo) {}

        List<UserSeed> seeds = List.of(
                new UserSeed("enes.b@example.com", "Enes", "Bilgic", Role.TOURIST, 4, 4821, 28),
                new UserSeed("aras.yilmaz@izmirguide.com", "Aras", "Yilmaz", Role.TOURIST, 12, 48250, 25),
                new UserSeed("selen.d@provider.net", "Selen", "Deniz", Role.TOURIST, 5, 12400, 20),
                new UserSeed("polat_can@gmail.com", "Can", "Polat", Role.TOURIST, 2, 1200, 15),
                new UserSeed("derya@aksoy.co", "Derya", "Aksoy", Role.TOURIST, 25, 125400, 30),
                new UserSeed("mkaya@business.com", "Murat", "Kaya", Role.TOURIST, 8, 18700, 22),
                new UserSeed("zeynep.l@mail.com", "Zeynep", "Ler", Role.TOURIST, 20, 82000, 18),
                new UserSeed("arda.k@outlook.com", "Arda", "Koc", Role.TOURIST, 3, 2500, 10),
                new UserSeed("elif.s@university.edu", "Elif", "Sahin", Role.TOURIST, 6, 15000, 7),
                new UserSeed("burak.d@tech.io", "Burak", "Demir", Role.TOURIST, 1, 200, 3),
                new UserSeed("ayse.y@mail.com", "Ayse", "Yurt", Role.TOURIST, 15, 55000, 12),
                new UserSeed("ali.v@corp.com", "Ali", "Veli", Role.MODERATOR, 10, 30000, 26),
                new UserSeed("fatma.o@editor.com", "Fatma", "Oz", Role.CONTENT_EDITOR, 7, 20000, 24),
                new UserSeed("hasan.t@example.com", "Hasan", "Tuncer", Role.TOURIST, 1, 50, 0)
        );

        List<User> users = new ArrayList<>();
        for (UserSeed s : seeds) {
            User existing = userService.findByEmailActive(s.email()).orElse(null);
            if (existing != null) {
                users.add(existing);
                continue;
            }

            User user = User.builder()
                    .email(s.email())
                    .passwordHash(encodedPassword)
                    .firstName(s.firstName())
                    .lastName(s.lastName())
                    .role(s.role())
                    .level(s.level())
                    .expPoints(s.expPoints())
                    .preferredLanguage("tr")
                    .build();

            User saved = userService.createUser(user);
            users.add(saved);

            LocalDateTime createdAt = now.minusDays(s.daysAgo());
            jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?",
                    Timestamp.valueOf(createdAt), saved.getId());
        }

        return users;
    }

    private List<Place> seedPlaces() {
        List<Place> existing = placeRepository.findAll();
        if (!existing.isEmpty()) {
            return existing;
        }

        record PlaceSeed(
                String externalId,
                String nameEn,
                String nameTr,
                String category,
                double lat,
                double lng,
                double avgRating,
                int reviewCount,
                String address,
                String openingHours,
                int popularityScore,
                String description,
                List<String> keywords
        ) {}

        List<PlaceSeed> seeds = List.of(
                new PlaceSeed(
                        "seed-kemeralti-bazaar",
                        "Kemeralti Bazaar",
                        "Kemeraltı Çarşısı",
                        "landmark",
                        38.4186, 27.1282, 4.8, 120,
                        "Konak, Izmir",
                        "Mon-Sat: 09:00-20:00",
                        95,
                        "Historic 17th-century open-air bazaar in the heart of Izmir filled with inns, spice shops, and traditional cafes.",
                        List.of("kemeralti", "bazaar", "carsi", "konak", "shopping", "historical", "kizlaragasi")
                ),
                new PlaceSeed(
                        "seed-kordon-promenade",
                        "Kordon Promenade",
                        "Kordonboyu",
                        "park",
                        38.4381, 27.1437, 4.7, 250,
                        "Alsancak, Konak, Izmir",
                        "Open 24 hours",
                        94,
                        "Iconic seaside promenade along the Gulf of Izmir, famous for sunset walks, lawns, cycling paths, and cafes.",
                        List.of("kordon", "promenade", "alsancak", "seafront", "park", "sunset", "sahil")
                ),
                new PlaceSeed(
                        "seed-deniz-restaurant",
                        "Deniz Restaurant",
                        "Deniz Restoran",
                        "restaurant",
                        38.4350, 27.1400, 4.6, 84,
                        "1. Kordon, Alsancak, Izmir",
                        "Daily: 12:00-00:00",
                        82,
                        "Classic Aegean seafood restaurant on 1. Kordon offering fresh fish, mezes, and waterfront views.",
                        List.of("deniz", "restaurant", "seafood", "aegean", "kordon", "alsancak", "balik")
                ),
                new PlaceSeed(
                        "seed-historical-elevator",
                        "Historical Elevator",
                        "Tarihi Asansör",
                        "landmark",
                        38.4089, 27.1173, 4.9, 310,
                        "Turgut Reis, Karataş, Izmir",
                        "Daily: 08:00-23:00",
                        96,
                        "1907 brick elevator tower built by Nesim Levi linking Mithatpaşa Street to the hillside neighborhood with panoramic bay views.",
                        List.of("asansor", "historical elevator", "karatas", "panorama", "viewpoint", "landmark", "dario moreno")
                ),
                new PlaceSeed(
                        "seed-bostanli-coast",
                        "Bostanli Coast",
                        "Bostanlı Sahili",
                        "park",
                        38.4556, 27.0956, 4.7, 172,
                        "Bostanlı, Karşıyaka, Izmir",
                        "Open 24 hours",
                        86,
                        "Lively northern waterfront park with wooden sunset terrace, cycling tracks, and ferry pier.",
                        List.of("bostanli", "karsiyaka", "coast", "sahil", "sunset", "park")
                ),
                new PlaceSeed(
                        "seed-agora-museum",
                        "Agora Open Air Museum",
                        "Agora Ören Yeri",
                        "museum",
                        38.4195, 27.1388, 4.5, 95,
                        "Namazgah, Konak, Izmir",
                        "Daily: 08:30-19:00",
                        89,
                        "Well-preserved Roman marketplace of ancient Smyrna featuring vaulted basilica arches, colonnades, and Ottoman tombstones.",
                        List.of("agora", "smyrna", "roman", "museum", "ancient", "ruins", "oren yeri")
                ),
                new PlaceSeed(
                        "seed-clock-tower",
                        "Izmir Clock Tower",
                        "İzmir Saat Kulesi",
                        "landmark",
                        38.4189, 27.1287, 4.8, 420,
                        "Konak Square, Izmir",
                        "Open 24 hours",
                        98,
                        "25-meter Ottoman clock tower built in 1901 by Raymond Charles Pere, the centerpiece landmark of Konak Square.",
                        List.of("clock tower", "saat kulesi", "konak", "landmark", "ottoman", "izmir")
                ),
                new PlaceSeed(
                        "seed-alsancak-cafe",
                        "Alsancak Roastery Cafe",
                        "Alsancak Kahve",
                        "cafe",
                        38.4340, 27.1420, 4.4, 68,
                        "Kıbrıs Şehitleri Cd., Alsancak, Izmir",
                        "Daily: 08:30-23:30",
                        78,
                        "Cozy specialty coffee house tucked inside a restored Levantine stone building in Alsancak.",
                        List.of("cafe", "coffee", "alsancak", "kahve", "kibris sehitleri")
                ),
                new PlaceSeed(
                        "seed-kadifekale",
                        "Kadifekale (Velvet Castle)",
                        "Kadifekale",
                        "landmark",
                        38.4143, 27.1460, 4.4, 110,
                        "Kadifekale, Konak, Izmir",
                        "Daily: 09:00-18:00",
                        84,
                        "Hilltop citadel founded by Lysimachus overlooking the entire Gulf of Izmir and the ancient theater slopes.",
                        List.of("kadifekale", "castle", "kale", "citadel", "view", "smyrna", "landmark")
                ),
                new PlaceSeed(
                        "seed-ephesus-ancient-city",
                        "Ephesus Ancient City",
                        "Efes Antik Kenti",
                        "museum",
                        37.9409, 27.3414, 4.9, 640,
                        "Selçuk, Izmir",
                        "Daily: 08:00-20:00",
                        99,
                        "UNESCO World Heritage archaeological site renowned for the Library of Celsus, Great Theatre, and Marble Street.",
                        List.of("ephesus", "efes", "celsus", "library of celsus", "unesco", "selcuk", "ancient", "museum")
                )
        );

        List<Place> places = new ArrayList<>();
        for (PlaceSeed s : seeds) {
            Place place = Place.builder()
                    .externalId(s.externalId())
                    .sourceApi("SEED")
                    .name(s.nameEn())
                    .nameTr(s.nameTr())
                    .nameEn(s.nameEn())
                    .category(s.category())
                    .latitude(s.lat())
                    .longitude(s.lng())
                    .avgRating(s.avgRating())
                    .reviewCount(s.reviewCount())
                    .address(s.address())
                    .openingHours(s.openingHours())
                    .popularityScore(s.popularityScore())
                    .description(s.description())
                    .keywords(s.keywords())
                    .build();
            places.add(placeRepository.save(place));
        }

        log.info("  Seeded {} Izmir places", places.size());
        return places;
    }

    private List<Badge> seedBadges() {
        List<Badge> existing = badgeRepository.findAll();
        if (!existing.isEmpty()) {
            return existing;
        }

        List<Badge> badges = List.of(
                Badge.builder()
                        .name("History Master")
                        .description("Complete heritage quests across Konak and Smyrna")
                        .iconName("emoji_events")
                        .iconColor("#F59E0B")
                        .tier(BadgeTier.GOLD)
                        .build(),
                Badge.builder()
                        .name("Local Foodie")
                        .description("Discover Izmir's culinary spots along Kordon and Kemeraltı")
                        .iconName("restaurant")
                        .iconColor("#10B981")
                        .tier(BadgeTier.SILVER)
                        .build(),
                Badge.builder()
                        .name("Agora Explorer")
                        .description("Verify your visit to ancient Agora and Konak Square")
                        .iconName("explore")
                        .iconColor("#CD7F32")
                        .tier(BadgeTier.BRONZE)
                        .build(),
                Badge.builder()
                        .name("Coastal Voyager")
                        .description("Walk the Aegean waterfront trails from Alsancak to Bostanlı")
                        .iconName("waves")
                        .iconColor("#3B82F6")
                        .tier(BadgeTier.GOLD)
                        .build()
        );

        List<Badge> saved = badgeRepository.saveAll(badges);
        log.info("  Seeded {} badges", saved.size());
        return saved;
    }

    private List<Quest> seedQuests(List<Badge> badges, List<Place> places) {
        List<Quest> existing = questRepository.findAll();
        if (!existing.isEmpty()) {
            return existing;
        }
        if (places.size() < 8 || badges.size() < 4) {
            return List.of();
        }

        Quest q1 = questRepository.save(Quest.builder()
                .title("Find the Hidden Han")
                .description("Explore Kemeraltı Bazaar's historic inns and continue to the Roman Agora.")
                .expReward(150)
                .region("Konak District")
                .badgeId(badges.get(0).getId())
                .gpsThresholdMeters(250)
                .build());
        Quest q2 = questRepository.save(Quest.builder()
                .title("Ancient Gate Explorer")
                .description("Trace ancient Smyrna from Agora Open Air Museum to Konak Clock Tower.")
                .expReward(200)
                .region("Old Town")
                .badgeId(badges.get(2).getId())
                .gpsThresholdMeters(250)
                .build());
        Quest q3 = questRepository.save(Quest.builder()
                .title("Clock Tower Challenge")
                .description("Check in at Izmir's signature Clock Tower and climb the Historical Elevator.")
                .expReward(100)
                .region("Konak & Karataş")
                .gpsThresholdMeters(250)
                .build());
        Quest q4 = questRepository.save(Quest.builder()
                .title("Seaside Flavors Trail")
                .description("Sample local Aegean seafood and coffee culture in Alsancak.")
                .expReward(80)
                .region("Kordon")
                .badgeId(badges.get(1).getId())
                .gpsThresholdMeters(250)
                .build());
        Quest q5 = questRepository.save(Quest.builder()
                .title("Coastal Sunset Route")
                .description("Catch sunset across the bay from Kordon Promenade to Bostanlı Coast.")
                .expReward(250)
                .region("Waterfront")
                .badgeId(badges.get(3).getId())
                .gpsThresholdMeters(250)
                .build());

        List<Quest> quests = List.of(q1, q2, q3, q4, q5);

        addSteps(q1, places.get(0).getId(), places.get(5).getId(),
                "Walk inside Kemeraltı Bazaar near Kızlarağası Han",
                "Head east to Agora Open Air Museum");
        addSteps(q2, places.get(5).getId(), places.get(6).getId(),
                "Stand near the Roman basilica arches at Agora",
                "Walk down to Konak Square Clock Tower");
        addSteps(q3, places.get(6).getId(), places.get(3).getId(),
                "Check in at Konak Clock Tower",
                "Ride the Historical Elevator to the terrace");
        addSteps(q4, places.get(2).getId(), places.get(7).getId(),
                "Start along 1. Kordon at Deniz Restaurant",
                "Walk toward Kıbrıs Şehitleri for a coffee break");
        addSteps(q5, places.get(1).getId(), places.get(4).getId(),
                "Begin your sunset stroll at Kordon Promenade",
                "Take the ferry across to Bostanlı Coast");

        log.info("  Seeded {} quests with steps", quests.size());
        return quests;
    }

    private void addSteps(Quest quest, UUID placeId1, UUID placeId2, String hint1, String hint2) {
        questStepRepository.save(QuestStep.builder()
                .quest(quest).placeId(placeId1).stepOrder(1).hint(hint1).requiresPhoto(false).build());
        questStepRepository.save(QuestStep.builder()
                .quest(quest).placeId(placeId2).stepOrder(2).hint(hint2).requiresPhoto(false).build());
    }

    private void seedRoutes(List<Place> places) {
        if (routeRepository.count() > 0 || places.size() < 9) {
            return;
        }

        Route historicTrail = routeRepository.save(Route.builder()
                .name("Historic Konak & Kemeraltı Trail")
                .description("Discover Ottoman Konak Square, traditional Kemeraltı inns, and the Roman Agora in one walkable loop.")
                .centerLatitude(38.4189)
                .centerLongitude(27.1310)
                .radiusMeters(50000)
                .estimatedMinutes(120)
                .expReward(200)
                .gpsThresholdMeters(200)
                .build());
        saveRouteStops(historicTrail, List.of(
                new StopSpec(places.get(6).getId(), 1, 20, "Start at Konak Clock Tower facing the sea."),
                new StopSpec(places.get(0).getId(), 2, 55, "Explore Kızlarağası Han and Kemeraltı alleys."),
                new StopSpec(places.get(5).getId(), 3, 45, "Finish among the ancient colonnades of Smyrna Agora.")
        ));

        Route waterfrontStroll = routeRepository.save(Route.builder()
                .name("Kordon & Alsancak Waterfront Stroll")
                .description("A relaxed afternoon along the Aegean seafront paired with Izmir seafood and specialty coffee.")
                .centerLatitude(38.4360)
                .centerLongitude(27.1420)
                .radiusMeters(50000)
                .estimatedMinutes(90)
                .expReward(150)
                .gpsThresholdMeters(200)
                .build());
        saveRouteStops(waterfrontStroll, List.of(
                new StopSpec(places.get(1).getId(), 1, 35, "Stroll along Kordon Promenade lawns."),
                new StopSpec(places.get(2).getId(), 2, 35, "Pause by the bay at 1. Kordon."),
                new StopSpec(places.get(7).getId(), 3, 20, "Enjoy a coffee break in Alsancak.")
        ));

        Route panoramaRoute = routeRepository.save(Route.builder()
                .name("Izmir Panorama & Heritage Route")
                .description("Take in the city's best viewpoints from Karataş Historical Elevator to Kadifekale and the Karşıyaka shoreline.")
                .centerLatitude(38.4250)
                .centerLongitude(27.1250)
                .radiusMeters(50000)
                .estimatedMinutes(150)
                .expReward(250)
                .gpsThresholdMeters(250)
                .build());
        saveRouteStops(panoramaRoute, List.of(
                new StopSpec(places.get(3).getId(), 1, 40, "Take the 1907 Historical Elevator up to the terrace."),
                new StopSpec(places.get(8).getId(), 2, 50, "Look out over the Gulf of Izmir from Kadifekale."),
                new StopSpec(places.get(4).getId(), 3, 60, "Cross the bay to enjoy sunset at Bostanlı Coast.")
        ));

        log.info("  Seeded 3 curated Izmir walking routes");
    }

    private record StopSpec(UUID placeId, int order, int minutes, String notes) {}

    private void saveRouteStops(Route route, List<StopSpec> stops) {
        List<RoutePlace> entities = stops.stream()
                .map(s -> RoutePlace.builder()
                        .route(route)
                        .placeId(s.placeId())
                        .stopOrder(s.order())
                        .estimatedMinutes(s.minutes())
                        .notes(s.notes())
                        .build())
                .toList();
        routePlaceRepository.saveAll(entities);
    }

    private void seedUserQuests(List<User> users, List<Quest> quests, LocalDateTime now) {
        if (users.isEmpty() || quests.isEmpty() || userQuestRepository.count() > 0) return;

        int count = 0;

        // Give demo user 1 completed quest and 1 active quest for showcase
        User demoUser = users.get(0);
        if (quests.size() >= 2) {
            userQuestRepository.save(UserQuest.builder()
                    .userId(demoUser.getId())
                    .questId(quests.get(2).getId())
                    .status(QuestStatus.COMPLETED)
                    .startedAt(now.minusDays(5))
                    .completedAt(now.minusDays(4))
                    .build());
            userQuestRepository.save(UserQuest.builder()
                    .userId(demoUser.getId())
                    .questId(quests.get(0).getId())
                    .status(QuestStatus.IN_PROGRESS)
                    .startedAt(now.minusDays(1))
                    .build());
            count += 2;
        }

        // Distribute completions across last 14 days for dashboard charts
        for (int day = 13; day >= 0; day--) {
            int completionsPerDay = 2 + (day % 2);
            for (int c = 0; c < completionsPerDay && count < users.size() * quests.size(); c++) {
                int userIdx = 1 + (count % Math.max(1, users.size() - 1));
                if (userIdx >= users.size()) userIdx = 0;
                int questIdx = (count / users.size()) % quests.size();

                UUID userId = users.get(userIdx).getId();
                UUID questId = quests.get(questIdx).getId();
                if (userQuestRepository.existsByUserIdAndQuestId(userId, questId)) {
                    count++;
                    continue;
                }

                LocalDateTime startedAt = now.minusDays(day + 2);
                LocalDateTime completedAt = now.minusDays(day).withHour(10 + c).withMinute(30);

                UserQuest uq = UserQuest.builder()
                        .userId(userId)
                        .questId(questId)
                        .status(QuestStatus.COMPLETED)
                        .startedAt(startedAt)
                        .completedAt(completedAt)
                        .build();
                userQuestRepository.save(uq);
                count++;
            }
        }

        for (int i = 0; i < 4 && i < users.size(); i++) {
            UUID userId = users.get(users.size() - 1 - i).getId();
            UUID questId = quests.get(i % quests.size()).getId();
            if (userQuestRepository.existsByUserIdAndQuestId(userId, questId)) continue;

            QuestStatus status = (i % 2 == 0) ? QuestStatus.IN_PROGRESS : QuestStatus.ABANDONED;
            UserQuest uq = UserQuest.builder()
                    .userId(userId)
                    .questId(questId)
                    .status(status)
                    .startedAt(now.minusDays(i + 1))
                    .build();
            userQuestRepository.save(uq);
            count++;
        }

        log.info("  Seeded {} user-quest records", count);
    }

    private void seedReviews(List<User> users, List<Place> places, LocalDateTime now) {
        if (users.isEmpty() || places.isEmpty() || reviewRepository.count() > 0) return;

        record ReviewSeed(int userIdx, int placeIdx, int rating, ReviewStatus status, int daysAgo, String comment) {}

        List<ReviewSeed> seeds = List.of(
                new ReviewSeed(0, 6, 5, ReviewStatus.APPROVED, 6, "Must-see symbol of Konak Square, especially lively around sunset."),
                new ReviewSeed(0, 3, 5, ReviewStatus.APPROVED, 5, "Incredible view over the Gulf of Izmir from the upper terrace."),
                new ReviewSeed(1, 0, 5, ReviewStatus.APPROVED, 4, "Amazing atmosphere in Kızlarağası Han with Turkish coffee."),
                new ReviewSeed(1, 3, 5, ReviewStatus.APPROVED, 3, "Historic architecture and Dario Moreno Street right below."),
                new ReviewSeed(2, 1, 4, ReviewStatus.APPROVED, 3, "Best place in Izmir for an evening walk by the sea."),
                new ReviewSeed(2, 4, 5, ReviewStatus.PENDING, 2, "Wooden sunset terrace in Bostanlı is super relaxing."),
                new ReviewSeed(3, 6, 4, ReviewStatus.APPROVED, 1, "Easy to reach by ferry, tram, or metro."),
                new ReviewSeed(4, 5, 5, ReviewStatus.APPROVED, 1, "Fascinating Roman ruins tucked right in the city center."),
                new ReviewSeed(5, 2, 4, ReviewStatus.PENDING, 0, "Great Aegean mezes right on 1. Kordon."),
                new ReviewSeed(6, 9, 5, ReviewStatus.APPROVED, 0, "The Library of Celsus is unforgettable.")
        );

        int count = 0;
        for (ReviewSeed s : seeds) {
            if (s.userIdx() >= users.size() || s.placeIdx() >= places.size()) continue;

            UUID userId = users.get(s.userIdx()).getId();
            UUID placeId = places.get(s.placeIdx()).getId();
            if (reviewRepository.existsByUserIdAndPlaceId(userId, placeId)) continue;

            Review review = Review.builder()
                    .userId(userId)
                    .placeId(placeId)
                    .rating(s.rating())
                    .comment(s.comment())
                    .status(s.status())
                    .build();

            Review saved = reviewRepository.save(review);
            count++;

            jdbcTemplate.update("UPDATE reviews SET created_at = ? WHERE id = ?",
                    Timestamp.valueOf(now.minusDays(s.daysAgo())), saved.getId());
        }

        log.info("  Seeded {} reviews", count);
    }

    private void seedUserBadges(List<User> users, List<Badge> badges, LocalDateTime now) {
        if (users.isEmpty() || badges.isEmpty() || userBadgeRepository.count() > 0) return;

        record BadgeSeed(int userIdx, int badgeIdx, int daysAgo) {}

        List<BadgeSeed> seeds = List.of(
                new BadgeSeed(0, 2, 6),
                new BadgeSeed(0, 3, 2),
                new BadgeSeed(1, 0, 5),
                new BadgeSeed(2, 0, 10),
                new BadgeSeed(2, 1, 8),
                new BadgeSeed(4, 2, 3),
                new BadgeSeed(4, 3, 1),
                new BadgeSeed(6, 0, 2)
        );

        int count = 0;
        for (BadgeSeed s : seeds) {
            if (s.userIdx() >= users.size() || s.badgeIdx() >= badges.size()) continue;

            UUID userId = users.get(s.userIdx()).getId();
            UUID badgeId = badges.get(s.badgeIdx()).getId();
            if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badgeId)) continue;

            UserBadge ub = UserBadge.builder()
                    .userId(userId)
                    .badgeId(badgeId)
                    .earnedAt(now.minusDays(s.daysAgo()))
                    .build();
            userBadgeRepository.save(ub);
            count++;
        }

        log.info("  Seeded {} user-badge records", count);
    }

    private void seedDemoFavoritesAndTodos(User demoUser, List<Place> places) {
        if (places.size() < 7) return;

        if (favoriteRepository.findByUserId(demoUser.getId()).isEmpty()) {
            favoriteRepository.save(Favorite.builder()
                    .userId(demoUser.getId())
                    .placeId(places.get(6).getId())
                    .build());
            favoriteRepository.save(Favorite.builder()
                    .userId(demoUser.getId())
                    .placeId(places.get(3).getId())
                    .build());
        }

        if (todoRepository.findByUserIdAndIsActiveTrue(demoUser.getId()).isEmpty()) {
            todoRepository.save(TodoItem.builder()
                    .userId(demoUser.getId())
                    .placeId(places.get(0).getId())
                    .note("Try Turkish coffee at Kızlarağası Han in Kemeraltı")
                    .isCompleted(false)
                    .build());
            todoRepository.save(TodoItem.builder()
                    .userId(demoUser.getId())
                    .placeId(places.get(1).getId())
                    .note("Watch sunset along Kordon Promenade")
                    .isCompleted(true)
                    .build());
        }
    }
}
