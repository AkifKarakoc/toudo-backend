package com.tourguide.place.places;

import com.tourguide.chat.places.GooglePlacesApiClient;
import com.tourguide.common.util.MinioUtil;
import com.tourguide.image.PlaceImage;
import com.tourguide.place.Place;
import com.tourguide.place.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceImportService {

    private final PlaceRepository placeRepository;
    private final PlacesApiPlaceMapper mapper;
    private final GooglePlacesApiClient googlePlacesApiClient;
    private final MinioUtil minioUtil;

    private static final String PLACE_IMAGES_BUCKET = "place-images";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Place> importPlaces(List<Map<String, Object>> placesData) {
        if (placesData == null || placesData.isEmpty()) {
            return List.of();
        }

        List<Place> saved = new ArrayList<>();
        for (Map<String, Object> data : placesData) {
            try {
                Place place = saveOrUpdate(data);
                saved.add(place);
                log.info("Imported place: externalId={}, name={}", place.getExternalId(), place.getName());
            } catch (Exception e) {
                log.warn("Failed to import place from Google Places: externalId={}, error={}",
                        data.get("id"), e.getMessage(), e);
            }
        }
        return saved;
    }

    private Place saveOrUpdate(Map<String, Object> data) {
        String externalId = String.valueOf(data.get("id"));

        Map<String, Object> enrichedData = enrichWithDetails(data);

        Optional<Place> existing = placeRepository.findByExternalId(externalId);

        Place place;
        if (existing.isPresent()) {
            place = existing.get();
            updatePlace(place, enrichedData);
            log.debug("Updating existing place: externalId={}", externalId);
        } else {
            place = mapper.toPlace(enrichedData);
            log.debug("Creating new place: externalId={}", externalId);
        }

        List<PlaceImage> images = downloadAndUploadPhotos(place, enrichedData);
        if (place.getImages() == null) {
            place.setImages(new ArrayList<>());
        }
        place.getImages().clear();
        place.getImages().addAll(images);

        return placeRepository.save(place);
    }

    private Map<String, Object> enrichWithDetails(Map<String, Object> data) {
        String placeId = String.valueOf(data.get("id"));
        try {
            Map<String, Object> details = googlePlacesApiClient.getPlaceDetails(placeId);
            if (details.isEmpty()) {
                return data;
            }

            Map<String, Object> enriched = new HashMap<>(data);
            if (details.containsKey("editorialSummary")) {
                enriched.put("editorialSummary", details.get("editorialSummary"));
            }
            if (details.containsKey("photos")) {
                enriched.put("photos", details.get("photos"));
            }
            return enriched;
        } catch (Exception e) {
            log.warn("Failed to fetch details for place {}: {}", placeId, e.getMessage());
            return data;
        }
    }

    @SuppressWarnings("unchecked")
    private List<PlaceImage> downloadAndUploadPhotos(Place place, Map<String, Object> data) {
        Object photos = data.get("photos");
        if (!(photos instanceof List<?> list)) {
            return List.of();
        }

        List<PlaceImage> images = new ArrayList<>();
        for (Object photo : list) {
            if (!(photo instanceof Map<?, ?>)) continue;
            if (images.size() >= 3) break;

            Object nameObj = ((Map<?, ?>) photo).get("name");
            String photoName = nameObj != null ? String.valueOf(nameObj) : "";
            if (photoName.isBlank()) continue;

            try {
                byte[] photoBytes = googlePlacesApiClient.downloadPhoto(photoName, 800);
                if (photoBytes == null || photoBytes.length == 0) continue;

                String objectName = minioUtil.upload(PLACE_IMAGES_BUCKET, photoBytes, "image/jpeg");
                images.add(PlaceImage.builder()
                        .place(place)
                        .imageUrl(objectName)
                        .build());
                log.debug("Uploaded photo for place {}: {}", place.getExternalId(), objectName);
            } catch (Exception e) {
                log.warn("Failed to upload photo {} for place {}: {}", photoName, place.getExternalId(), e.getMessage());
            }
        }
        return images;
    }

    private void updatePlace(Place place, Map<String, Object> data) {
        Place updated = mapper.toPlace(data);
        place.setName(updated.getName());
        place.setNameTr(updated.getNameTr());
        place.setNameEn(updated.getNameEn());
        place.setCategory(updated.getCategory());
        place.setLatitude(updated.getLatitude());
        place.setLongitude(updated.getLongitude());
        place.setAddress(updated.getAddress());
        place.setAvgRating(updated.getAvgRating());
        place.setReviewCount(updated.getReviewCount());
        place.setDescription(updated.getDescription());
        place.setWebsite(updated.getWebsite());
        place.setOpeningHours(updated.getOpeningHours());
        place.setKeywords(updated.getKeywords());
        place.setPhotoUrl(updated.getPhotoUrl());
        place.setIsActive(true);
    }
}
