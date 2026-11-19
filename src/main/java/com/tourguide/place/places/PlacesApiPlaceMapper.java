package com.tourguide.place.places;

import com.tourguide.image.PlaceImage;
import com.tourguide.place.Place;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PlacesApiPlaceMapper {

    private static final String SOURCE_API = "GOOGLE_PLACES";

    public Place toPlace(Map<String, Object> placeData) {
        String externalId = String.valueOf(placeData.get("id"));

        Place place = new Place();
        place.setExternalId(externalId);
        place.setSourceApi(SOURCE_API);
        place.setName(extractDisplayName(placeData));
        place.setNameTr(extractDisplayNameForLocale(placeData, "tr"));
        place.setNameEn(extractDisplayNameForLocale(placeData, "en"));
        place.setCategory(mapCategory(extractTypes(placeData)));
        place.setLatitude(extractLatitude(placeData));
        place.setLongitude(extractLongitude(placeData));
        place.setAddress(extractString(placeData, "formattedAddress"));
        place.setAvgRating(extractDouble(placeData, "rating"));
        place.setReviewCount(extractInteger(placeData, "userRatingCount"));
        place.setDescription(extractEditorialSummary(placeData));
        place.setWebsite(extractString(placeData, "websiteUri"));
        place.setOpeningHours(extractOpeningHours(placeData));
        place.setKeywords(extractTypes(placeData));
        // Photos are downloaded to MinIO via PlaceImportService to avoid persisting API keys in DB URLs.
        place.setPhotoUrl(null);
        place.setIsActive(true);

        return place;
    }

    public List<PlaceImage> toPlaceImages(Place place, Map<String, Object> placeData) {
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private String extractDisplayName(Map<String, Object> placeData) {
        Object displayName = placeData.get("displayName");
        if (displayName instanceof Map<?, ?> map) {
            Object text = map.get("text");
            return text != null ? String.valueOf(text) : "Unnamed Place";
        }
        return "Unnamed Place";
    }

    @SuppressWarnings("unchecked")
    private String extractDisplayNameForLocale(Map<String, Object> placeData, String languageCode) {
        // Google Places API returns one displayName based on languageCode request param.
        // For simplicity, use the same as main name. Can be enhanced later.
        return extractDisplayName(placeData);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractTypes(Map<String, Object> placeData) {
        Object types = placeData.get("types");
        if (types instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String mapCategory(List<String> types) {
        if (types == null || types.isEmpty()) {
            return "OTHER";
        }
        for (String type : types) {
            switch (type) {
                case "restaurant":
                case "meal_takeaway":
                case "meal_delivery":
                    return "RESTAURANT";
                case "cafe":
                    return "CAFE";
                case "bar":
                    return "BAR";
                case "tourist_attraction":
                case "museum":
                case "historical_landmark":
                case "monument":
                    return "TOURIST_ATTRACTION";
                case "lodging":
                case "hotel":
                    return "HOTEL";
                case "park":
                    return "PARK";
                case "shopping_mall":
                case "store":
                    return "SHOPPING";
                default:
                    // continue
            }
        }
        return "OTHER";
    }

    @SuppressWarnings("unchecked")
    private Double extractLatitude(Map<String, Object> placeData) {
        Object location = placeData.get("location");
        if (location instanceof Map<?, ?> map) {
            Object lat = map.get("latitude");
            return lat instanceof Number ? ((Number) lat).doubleValue() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Double extractLongitude(Map<String, Object> placeData) {
        Object location = placeData.get("location");
        if (location instanceof Map<?, ?> map) {
            Object lng = map.get("longitude");
            return lng instanceof Number ? ((Number) lng).doubleValue() : null;
        }
        return null;
    }

    private String extractString(Map<String, Object> placeData, String key) {
        Object value = placeData.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private Double extractDouble(Map<String, Object> placeData, String key) {
        Object value = placeData.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private Integer extractInteger(Map<String, Object> placeData, String key) {
        Object value = placeData.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private String extractEditorialSummary(Map<String, Object> placeData) {
        Object summary = placeData.get("editorialSummary");
        if (summary instanceof Map<?, ?> map) {
            Object text = map.get("text");
            return text != null ? String.valueOf(text) : "";
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String extractOpeningHours(Map<String, Object> placeData) {
        Object hours = placeData.get("regularOpeningHours");
        if (hours instanceof Map<?, ?> map) {
            Object weekday = map.get("weekdayDescriptions");
            if (weekday instanceof List<?> list && !list.isEmpty()) {
                return String.join("\n", list.stream().map(String::valueOf).toList());
            }
            Object text = map.get("text");
            return text != null ? String.valueOf(text) : "";
        }
        return "";
    }
}
