package com.tourguide.chat.places;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlacesApiClient {

    private final RestTemplate restTemplate;

    @Value("${google.places.api-key:}")
    private String apiKey;

    @Value("${google.places.base-url:https://places.googleapis.com}")
    private String baseUrl;

    private static final String FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress," +
            "places.location,places.rating,places.userRatingCount," +
            "places.priceLevel,places.types,places.regularOpeningHours," +
            "places.photos.name,places.photos.widthPx,places.photos.heightPx," +
            "places.editorialSummary,places.websiteUri";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<Map<String, Object>> searchNearby(
            double latitude, double longitude, int radiusMeters, List<String> includedTypes) {
        URI uri = URI.create(baseUrl + "/v1/places:searchNearby");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("locationRestriction", Map.of(
                "circle", Map.of(
                        "center", Map.of("latitude", latitude, "longitude", longitude),
                        "radius", radiusMeters
                )
        ));
        body.put("maxResultCount", 10);
        body.put("rankPreference", "DISTANCE");
        if (includedTypes != null && !includedTypes.isEmpty()) {
            body.put("includedTypes", includedTypes);
        }

        return executeRequest(uri, body);
    }

    public List<Map<String, Object>> textSearch(String query, Optional<Double> latitude, Optional<Double> longitude, Optional<Integer> radiusMeters) {
        URI uri = URI.create(baseUrl + "/v1/places:searchText");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("textQuery", query);
        body.put("maxResultCount", 10);
        body.put("languageCode", "tr");

        if (latitude.isPresent() && longitude.isPresent() && radiusMeters.isPresent()) {
            body.put("locationBias", Map.of(
                    "circle", Map.of(
                            "center", Map.of("latitude", latitude.get(), "longitude", longitude.get()),
                            "radius", radiusMeters.get()
                    )
            ));
        }

        return executeRequest(uri, body);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlaceDetails(String placeId) {
        if (!isConfigured()) {
            log.debug("GOOGLE_PLACES_API_KEY is not configured; skipping place details fetch");
            return Map.of();
        }
        URI uri = URI.create(baseUrl + "/v1/places/" + placeId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask",
                "id,displayName,formattedAddress,location,rating,userRatingCount," +
                "priceLevel,types,regularOpeningHours,photos.name,photos.widthPx,photos.heightPx," +
                "editorialSummary,websiteUri");

        try {
            Map<String, Object> response = restTemplate.exchange(
                    uri, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class).getBody();
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.warn("Failed to fetch place details for {}: {}", placeId, e.getMessage());
            return Map.of();
        }
    }

    public byte[] downloadPhoto(String photoName, int maxWidthPx) {
        if (!isConfigured()) {
            return null;
        }
        String url = baseUrl + "/v1/" + photoName + "/media?maxWidthPx=" + maxWidthPx + "&key=" + apiKey;
        try {
            return restTemplate.getForObject(URI.create(url), byte[].class);
        } catch (Exception e) {
            log.warn("Failed to download photo {}: {}", photoName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeRequest(URI uri, Map<String, Object> body) {
        if (!isConfigured()) {
            log.debug("GOOGLE_PLACES_API_KEY is not configured; skipping Places API call");
            return List.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", FIELD_MASK);

        String apiKeySuffix = apiKey.length() >= 4 ? apiKey.substring(apiKey.length() - 4) : "****";
        log.info("Places API request: uri={}, baseUrl={}, apiKeySuffix={}, fieldMask={}",
                uri, baseUrl, apiKeySuffix, FIELD_MASK);
        log.info("Places API request body: {}", body);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    uri, new HttpEntity<>(body, headers), Map.class);

            if (response == null || !response.containsKey("places")) {
                log.info("Places API response has no places: {}", response);
                return List.of();
            }
            log.info("Places API response places count: {}", ((List<?>) response.get("places")).size());
            return (List<Map<String, Object>>) response.get("places");
        } catch (Exception e) {
            log.error("Google Places API call failed: {}", e.getMessage());
            return List.of();
        }
    }
}
