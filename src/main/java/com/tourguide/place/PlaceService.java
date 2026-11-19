package com.tourguide.place;

import com.tourguide.admin.contenteditor.dto.PlaceImageResponse;
import com.tourguide.chat.places.GooglePlacesApiClient;
import com.tourguide.common.exception.ResourceNotFoundException;
import com.tourguide.common.util.CoordinateUtil;
import com.tourguide.common.util.GeohashUtil;
import com.tourguide.common.util.MinioUtil;
import com.tourguide.admin.contenteditor.dto.PlaceMapPointResponse;
import com.tourguide.place.dto.PlaceDetailResponse;
import com.tourguide.place.dto.PlaceResponse;
import com.tourguide.place.places.PlaceImportService;
import com.tourguide.review.ReviewRepository;
import com.tourguide.review.ReviewStatus;
import com.tourguide.user.IUserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService implements IPlaceService {

    private final PlaceRepository placeRepository;
    private final IUserService userService;
    private final PlaceMapper placeMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReviewRepository reviewRepository;
    private final MinioUtil minioUtil;
    private final GooglePlacesApiClient googlePlacesApiClient;
    private final PlaceImportService placeImportService;

    private static final String CACHE_PREFIX = "query:geohash:";
    private static final String PLACE_DETAIL_CACHE_PREFIX = "place:detail:";
    private static final int GEOHASH_PREFIX_LEN = 5;
    private static final long CACHE_TTL = 3600;
    private static final long PLACE_DETAIL_CACHE_TTL = 1800;
    private static final int DEFAULT_RADIUS = 50000;
    private static final int DEFAULT_LIMIT = 20;
    private static final String PLACE_IMAGES_BUCKET = "place-images";

    private static final List<String> RELIGIOUS_ALIASES = List.of(
            "religious", "mosque", "cami", "kilise", "sinagog", "türbe"
    );
    private static final List<String> LANDMARK_ALIASES = List.of(
            "landmark", "monument", "tourist_attraction", "tarihi_yapi", "kale",
            "antik_alan", "konak", "kule", "çarşı", "hamam", "tarihi_otel", "sanayi_mirası"
    );
    private static final Map<String, List<String>> CATEGORY_ALIASES = Map.of(
            "museum", List.of("museum", "müze"),
            "religious", RELIGIOUS_ALIASES,
            "mosque", RELIGIOUS_ALIASES,
            "landmark", LANDMARK_ALIASES,
            "monument", LANDMARK_ALIASES,
            "tourist_attraction", LANDMARK_ALIASES,
            "park", List.of("park"),
            "beach", List.of("beach", "sahil_şeridi", "plaj"),
            "cafe", List.of("cafe", "kafe", "kıraathane"),
            "restaurant", List.of("restaurant", "restoran")
    );

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<PlaceResponse> findNearby(double lat, double lng, Integer radius, Integer limit, UUID userId) {
        return findNearby(lat, lng, radius, limit, null, userId);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<PlaceResponse> findNearby(double lat, double lng, Integer radius, Integer limit, String category, UUID userId) {
        int searchRadius = radius != null ? radius : DEFAULT_RADIUS;
        int searchLimit = limit != null ? limit : DEFAULT_LIMIT;

        String geohash = GeohashUtil.encode(lat, lng);
        String geohashPrefix = geohash.length() > GEOHASH_PREFIX_LEN
                ? geohash.substring(0, GEOHASH_PREFIX_LEN)
                : geohash;
        String cacheKey = CACHE_PREFIX + geohashPrefix + ":" + searchRadius + ":" + (category != null ? category : "all") + ":" + searchLimit;

        List<PlaceResponse> cached = null;
        try {
            cached = (List<PlaceResponse>) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to read nearby places cache: {}", e.getMessage());
        }
        if (cached != null) {
            if (userId != null) {
                cached.forEach(p -> p.setIsFavorited(userService.isFavorited(userId, p.getId())));
            }
            return cached;
        }

        List<Place> places = fetchNearbyFromDbOrPlaces(lat, lng, searchRadius, searchLimit, category);

        List<PlaceResponse> responses = places.stream()
                .map(place -> {
                    PlaceResponse response = placeMapper.toResponse(place);
                    response.setDistance(CoordinateUtil.haversineDistance(lat, lng, place.getLatitude(), place.getLongitude()));
                    if (userId != null) {
                        response.setIsFavorited(userService.isFavorited(userId, place.getId()));
                    }
                    convertImageUrlsToPresigned(response.getImages());
                    return response;
                })
                .collect(Collectors.toList());

        try {
            redisTemplate.opsForValue().set(cacheKey, responses, CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache nearby places: {}", e.getMessage());
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse findById(UUID placeId, UUID userId) {
        String cacheKey = PLACE_DETAIL_CACHE_PREFIX + placeId.toString();
        PlaceDetailResponse cached = null;
        try {
            cached = (PlaceDetailResponse) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to read place detail cache: {}", e.getMessage());
        }
        if (cached != null) {
            if (userId != null) {
                cached.setIsFavorited(userService.isFavorited(userId, placeId));
            }
            Map<Integer, Integer> distribution = new HashMap<>();
            for (int i = 1; i <= 5; i++) distribution.put(i, 0);
            List<Object[]> rows = reviewRepository.countRatingDistributionByPlaceId(placeId);
            for (Object[] row : rows) {
                distribution.put(((Number) row[0]).intValue(), ((Long) row[1]).intValue());
            }
            cached.setRatingDistribution(distribution);
            return cached;
        }

        Place place = placeRepository.findByIdAndIsActiveTrue(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place", "id", placeId));

        place.getImages().size();

        PlaceDetailResponse response = placeMapper.toDetailResponse(place);

        try {
            redisTemplate.opsForValue().set(cacheKey, response, PLACE_DETAIL_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache place detail: {}", e.getMessage());
        }

        convertImageUrlsToPresigned(response.getImages());

        if (userId != null) {
            response.setIsFavorited(userService.isFavorited(userId, placeId));
        }

        Map<Integer, Integer> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0);
        List<Object[]> rows = reviewRepository.countRatingDistributionByPlaceId(placeId);
        for (Object[] row : rows) {
            distribution.put(((Number) row[0]).intValue(), ((Long) row[1]).intValue());
        }
        response.setRatingDistribution(distribution);

        return response;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> search(String query, UUID userId) {
        if (googlePlacesApiClient.isConfigured()) {
            try {
                List<Map<String, Object>> googlePlaces = googlePlacesApiClient.textSearch(
                        query, Optional.empty(), Optional.empty(), Optional.empty());
                placeImportService.importPlaces(googlePlaces);
            } catch (Exception e) {
                log.warn("Failed to fetch search results from Google Places API: {}", e.getMessage());
            }
        }

        List<Place> places = placeRepository.search(query);

        return places.stream()
                .map(place -> {
                    PlaceResponse response = placeMapper.toResponse(place);
                    if (userId != null) {
                        response.setIsFavorited(userService.isFavorited(userId, place.getId()));
                    }
                    convertImageUrlsToPresigned(response.getImages());
                    return response;
                })
                .collect(Collectors.toList());
    }

    private void convertImageUrlsToPresigned(List<PlaceImageResponse> images) {
        if (images == null) return;
        for (PlaceImageResponse image : images) {
            String presignedUrl = minioUtil.getPresignedUrl(PLACE_IMAGES_BUCKET, image.getImageUrl());
            image.setImageUrl(presignedUrl);
        }
    }

            @Override
            @Transactional(readOnly = true)
            public List<PlaceMapPointResponse> findMapPoints() {
            return placeRepository.findByIsActiveTrue().stream()
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .map(place -> PlaceMapPointResponse.builder()
                    .id(place.getId())
                    .name(place.getName())
                    .nameTr(place.getNameTr())
                    .nameEn(place.getNameEn())
                    .category(place.getCategory())
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .isActive(place.getIsActive())
                    .build())
                .collect(Collectors.toList());
            }

    private List<Place> fetchNearbyFromDbOrPlaces(double lat, double lng, int radius, int limit, String category) {
        List<Place> places = queryNearbyFromDb(lat, lng, radius, limit, category);
        log.info("findNearby DB results: lat={}, lng={}, radius={}, category={}, count={}", lat, lng, radius, category, places.size());

        if (!googlePlacesApiClient.isConfigured()) {
            return places;
        }

        try {
            int placesRadius = Math.min(radius, 50000);
            List<String> types = category != null ? categoryToPlacesTypes(category) : List.of();
            log.info("Fetching from Google Places API: radius={}, types={}", placesRadius, types);
            List<Map<String, Object>> googlePlaces = googlePlacesApiClient.searchNearby(lat, lng, placesRadius, types);
            log.info("Google Places API returned {} places", googlePlaces.size());
            List<Place> imported = placeImportService.importPlaces(googlePlaces);
            log.info("Imported {} places into DB", imported.size());
        } catch (Exception e) {
            log.warn("Failed to fetch nearby places from Google Places API: {}", e.getMessage(), e);
            return places;
        }

        places = queryNearbyFromDb(lat, lng, radius, limit, category);
        log.info("findNearby DB results after import: count={}", places.size());
        return places;
    }

    private List<Place> queryNearbyFromDb(double lat, double lng, int radius, int limit, String category) {
        if (category != null && !category.isBlank()) {
            return placeRepository.findNearbyByCategories(lat, lng, radius, limit, expandCategoryAliases(category));
        }
        return placeRepository.findNearby(lat, lng, radius, limit);
    }

    List<String> expandCategoryAliases(String category) {
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return CATEGORY_ALIASES.getOrDefault(normalized, List.of(normalized));
    }

    private List<String> categoryToPlacesTypes(String category) {
        if (category == null) return List.of();
        return switch (category.toUpperCase()) {
            case "RESTAURANT" -> List.of("restaurant");
            case "CAFE" -> List.of("cafe");
            case "BAR" -> List.of("bar");
            case "TOURIST_ATTRACTION" -> List.of("tourist_attraction", "museum", "historical_landmark");
            case "HOTEL" -> List.of("lodging", "hotel");
            case "PARK" -> List.of("park");
            case "SHOPPING" -> List.of("shopping_mall", "store");
            default -> List.of();
        };
    }

    @Override
    public Place getPlaceEntity(UUID placeId) {
        return placeRepository.findByIdAndIsActiveTrue(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place", "id", placeId));
    }

    @Override
    @Transactional
    @CacheEvict(value = "query:geohash", allEntries = true)
    public Place createPlace(Place place) {
        return placeRepository.save(place);
    }

    @Override
    @Transactional
    @CacheEvict(value = "query:geohash", allEntries = true)
    public Place updatePlace(UUID placeId, Place updates) {
        redisTemplate.delete(PLACE_DETAIL_CACHE_PREFIX + placeId.toString());

        Place existing = placeRepository.findByIdAndIsActiveTrue(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place", "id", placeId));

        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getNameTr() != null) {
            existing.setNameTr(updates.getNameTr());
        }
        if (updates.getNameEn() != null) {
            existing.setNameEn(updates.getNameEn());
        }
        if (updates.getCategory() != null) {
            existing.setCategory(updates.getCategory());
        }
        if (updates.getLatitude() != null) {
            existing.setLatitude(updates.getLatitude());
        }
        if (updates.getLongitude() != null) {
            existing.setLongitude(updates.getLongitude());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getAddress() != null) {
            existing.setAddress(updates.getAddress());
        }
        if (updates.getPhone() != null) {
            existing.setPhone(updates.getPhone());
        }
        if (updates.getWebsite() != null) {
            existing.setWebsite(updates.getWebsite());
        }
        if (updates.getOpeningHours() != null) {
            existing.setOpeningHours(updates.getOpeningHours());
        }
        if (updates.getPhotoUrl() != null) {
            existing.setPhotoUrl(updates.getPhotoUrl());
        }
        if (updates.getPopularityScore() != null) {
            existing.setPopularityScore(updates.getPopularityScore());
        }
        if (updates.getKeywords() != null) {
            existing.setKeywords(updates.getKeywords());
        }

        return placeRepository.save(existing);
    }

    @Override
    @Transactional
    @CacheEvict(value = "query:geohash", allEntries = true)
    public void softDeletePlace(UUID placeId) {
        redisTemplate.delete(PLACE_DETAIL_CACHE_PREFIX + placeId.toString());

        Place place = placeRepository.findByIdAndIsActiveTrue(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place", "id", placeId));
        place.setIsActive(false);
        placeRepository.save(place);
    }

    @Override
    @Transactional
    @CacheEvict(value = "query:geohash", allEntries = true)
    public void updateRating(UUID placeId, double avgRating, int reviewCount) {
        redisTemplate.delete(PLACE_DETAIL_CACHE_PREFIX + placeId.toString());

        Place place = placeRepository.findById(placeId).orElse(null);
        if (place == null) {
            return;
        }
        place.setAvgRating(avgRating);
        place.setReviewCount(reviewCount);
        placeRepository.save(place);
    }
}
