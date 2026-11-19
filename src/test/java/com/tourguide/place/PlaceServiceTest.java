package com.tourguide.place;

import com.tourguide.chat.places.GooglePlacesApiClient;
import com.tourguide.common.util.MinioUtil;
import com.tourguide.place.dto.PlaceResponse;
import com.tourguide.place.places.PlaceImportService;
import com.tourguide.review.ReviewRepository;
import com.tourguide.user.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private IUserService userService;

    @Mock
    private PlaceMapper placeMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private MinioUtil minioUtil;

    @Mock
    private GooglePlacesApiClient googlePlacesApiClient;

    @Mock
    private PlaceImportService placeImportService;

    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(googlePlacesApiClient.isConfigured()).thenReturn(false);

        placeService = new PlaceService(
                placeRepository,
                userService,
                placeMapper,
                redisTemplate,
                reviewRepository,
                minioUtil,
                googlePlacesApiClient,
                placeImportService
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void findNearby_expandsMuseumCategoryAliasesForTurkishDbCategories() {
        Place museum = Place.builder()
                .name("Izmir Archaeology Museum")
                .category("müze")
                .latitude(38.414)
                .longitude(27.127)
                .build();
        museum.setId(UUID.randomUUID());
        when(placeRepository.findNearbyByCategories(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyList()))
                .thenReturn(List.of(museum));
        when(placeMapper.toResponse(museum)).thenReturn(PlaceResponse.builder().id(museum.getId()).name(museum.getName()).build());

        List<PlaceResponse> results = placeService.findNearby(38.4237, 27.1428, 15000, 50, "museum", null);

        assertThat(results).hasSize(1);
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeRepository).findNearbyByCategories(anyDouble(), anyDouble(), anyDouble(), anyInt(), captor.capture());
        assertThat(captor.getValue()).contains("museum", "müze");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findNearby_expandsReligiousAndLandmarkAliases() {
        when(placeRepository.findNearbyByCategories(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyList()))
                .thenReturn(List.of());

        placeService.findNearby(38.4237, 27.1428, 15000, 50, "religious", null);
        placeService.findNearby(38.4237, 27.1428, 15000, 50, "landmark", null);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeRepository, org.mockito.Mockito.times(2))
                .findNearbyByCategories(anyDouble(), anyDouble(), anyDouble(), anyInt(), captor.capture());

        assertThat(captor.getAllValues().get(0))
                .contains("religious", "mosque", "cami", "kilise", "sinagog", "türbe");
        assertThat(captor.getAllValues().get(1))
                .contains("landmark", "tarihi_yapi", "kule");
    }
}
