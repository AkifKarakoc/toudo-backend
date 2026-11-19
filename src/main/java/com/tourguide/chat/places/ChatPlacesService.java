package com.tourguide.chat.places;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPlacesService {

    private final GooglePlacesApiClient placesApiClient;

    private static final int NEARBY_RADIUS_METERS = 50000;

    public String buildPlacesContext(String userQuery, Optional<Double> latitude, Optional<Double> longitude) {
        if (!placesApiClient.isConfigured()) {
            return "";
        }
        try {
            List<Map<String, Object>> nearby = latitude.isPresent() && longitude.isPresent()
                    ? placesApiClient.searchNearby(latitude.get(), longitude.get(), NEARBY_RADIUS_METERS, List.of())
                    : List.of();

            List<Map<String, Object>> textResults = placesApiClient.textSearch(
                    userQuery, latitude, longitude, Optional.of(NEARBY_RADIUS_METERS));

            Set<String> seenIds = new LinkedHashSet<>();
            List<Map<String, Object>> merged = new ArrayList<>();

            for (Map<String, Object> place : nearby) {
                String id = String.valueOf(place.getOrDefault("id", UUID.randomUUID()));
                if (seenIds.add(id)) {
                    merged.add(place);
                }
            }
            for (Map<String, Object> place : textResults) {
                String id = String.valueOf(place.getOrDefault("id", UUID.randomUUID()));
                if (seenIds.add(id)) {
                    merged.add(place);
                }
            }

            if (merged.isEmpty()) {
                return "";
            }

            StringBuilder context = new StringBuilder();
            context.append("Aşağıda Google Places API'den canlı olarak çekilen mekanlar listelenmektedir. " +
                    "Önerilerini öncelikle bu mekanlardan yap; bu listede olmayan mekanları kesin varmış gibi söyleme.\n\n");
            context.append("Mekanlar:\n");

            for (Map<String, Object> place : merged.stream().limit(8).toList()) {
                context.append("- ")
                        .append(extractDisplayName(place))
                        .append(" | adres: ").append(place.getOrDefault("formattedAddress", "-"))
                        .append(" | puan: ").append(place.getOrDefault("rating", "-"))
                        .append(" | yorum sayısı: ").append(place.getOrDefault("userRatingCount", "-"))
                        .append(" | fiyat seviyesi: ").append(place.getOrDefault("priceLevel", "-"))
                        .append(" | türler: ").append(extractTypes(place))
                        .append(" | açıklama: ").append(extractEditorialSummary(place))
                        .append("\n");
            }

            return context.toString();
        } catch (Exception e) {
            log.error("Failed to build places context: {}", e.getMessage());
            return "";
        }
    }

    private String extractDisplayName(Map<String, Object> place) {
        Object displayName = place.get("displayName");
        if (displayName instanceof Map<?, ?> map) {
            Object text = map.get("text");
            return text != null ? String.valueOf(text) : "Bilinmeyen Mekan";
        }
        return "Bilinmeyen Mekan";
    }

    @SuppressWarnings("unchecked")
    private String extractTypes(Map<String, Object> place) {
        Object types = place.get("types");
        if (types instanceof List<?> list) {
            return list.stream().limit(3).map(String::valueOf).collect(Collectors.joining(", "));
        }
        return "-";
    }

    private String extractEditorialSummary(Map<String, Object> place) {
        Object summary = place.get("editorialSummary");
        if (summary instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text == null) {
                return "-";
            }
            String summaryText = String.valueOf(text);
            return summaryText.length() > 120 ? summaryText.substring(0, 117) + "..." : summaryText;
        }
        return "-";
    }
}
