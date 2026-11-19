package com.tourguide.chat;

import com.tourguide.chat.places.ChatPlacesService;
import com.tourguide.place.Place;
import com.tourguide.place.PlaceRepository;
import com.tourguide.quest.Quest;
import com.tourguide.quest.QuestRepository;
import com.tourguide.route.Route;
import com.tourguide.route.RouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private QuestRepository questRepository;

    @Mock
    private ChatPlacesService chatPlacesService;

    @Test
    void generateReply_usesDemoKnowledgeService_whenDemoProviderActive() {
        ChatKnowledgeService knowledgeService = new ChatKnowledgeService(placeRepository, routeRepository, questRepository);
        AiChatService aiChatService = new AiChatService(new DemoAiModelClient(), knowledgeService, chatPlacesService);

        Place clockTower = Place.builder()
                .name("Izmir Clock Tower")
                .nameTr("İzmir Saat Kulesi")
                .nameEn("Izmir Clock Tower")
                .category("landmark")
                .address("Konak Square, Izmir")
                .avgRating(4.8)
                .popularityScore(95)
                .description("Historic Ottoman clock tower in Konak Square.")
                .keywords(List.of("clock tower", "saat kulesi", "konak"))
                .build();
        when(placeRepository.findByIsActiveTrue()).thenReturn(List.of(clockTower));
        when(routeRepository.findByIsActiveTrue()).thenReturn(List.of(
                Route.builder().name("Historic Konak Trail").estimatedMinutes(90).expReward(150)
                        .centerLatitude(38.4189).centerLongitude(27.1287).build()));
        when(questRepository.findByIsActiveTrue()).thenReturn(List.of(
                Quest.builder().title("Clock Tower Challenge").region("Konak").expReward(100).build()));

        List<ChatMessage> conversation = List.of(
                ChatMessage.builder().role("user").content("Konak Saat Kulesi hakkında bilgi verir misin?").build());

        String reply = aiChatService.generateReply(conversation, "tr", Optional.empty(), Optional.empty());

        assertThat(reply).contains("İzmir Saat Kulesi");
        assertThat(reply).contains("Demo Modu");
        verify(chatPlacesService, never()).buildPlacesContext(anyString(), any(), any());
    }

    @Test
    void generateReply_fallsBackToTopSeededHighlights_whenDemoQueryHasNoKeywordMatch() {
        ChatKnowledgeService knowledgeService = new ChatKnowledgeService(placeRepository, routeRepository, questRepository);
        AiChatService aiChatService = new AiChatService(new DemoAiModelClient(), knowledgeService, chatPlacesService);

        Place kordon = Place.builder()
                .name("Kordon Promenade")
                .nameEn("Kordon Promenade")
                .address("Alsancak, Izmir")
                .avgRating(4.7)
                .popularityScore(90)
                .description("Popular seaside promenade.")
                .build();
        when(placeRepository.findByIsActiveTrue()).thenReturn(List.of(kordon));
        when(routeRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(questRepository.findByIsActiveTrue()).thenReturn(List.of());

        List<ChatMessage> conversation = List.of(
                ChatMessage.builder().role("user").content("Hello").build());

        String reply = aiChatService.generateReply(conversation, "en", Optional.empty(), Optional.empty());

        assertThat(reply).contains("popular Izmir highlights");
        assertThat(reply).contains("Kordon Promenade");
        assertThat(reply).contains("Demo Mode");
    }

    @Test
    void generateReply_doesNotReturnUnrelatedPopularPlaces_whenOnlyQuestMatches() {
        ChatKnowledgeService knowledgeService = new ChatKnowledgeService(placeRepository, routeRepository, questRepository);
        AiChatService aiChatService = new AiChatService(new DemoAiModelClient(), knowledgeService, chatPlacesService);

        Place unrelatedPopularPlace = Place.builder()
                .name("Unrelated Popular Mosque")
                .nameEn("Unrelated Popular Mosque")
                .address("Konak, Izmir")
                .avgRating(4.9)
                .popularityScore(95)
                .reviewCount(30)
                .description("Historic neighborhood mosque.")
                .build();
        Quest matchingQuest = Quest.builder()
                .title("Clock Tower Challenge")
                .region("Konak")
                .expReward(100)
                .build();
        when(placeRepository.findByIsActiveTrue()).thenReturn(List.of(unrelatedPopularPlace));
        when(routeRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(questRepository.findByIsActiveTrue()).thenReturn(List.of(matchingQuest));

        List<ChatMessage> conversation = List.of(
                ChatMessage.builder().role("user").content("clock tower").build());

        String reply = aiChatService.generateReply(conversation, "en", Optional.empty(), Optional.empty());

        assertThat(reply)
                .contains("matching Izmir recommendations")
                .contains("Clock Tower Challenge")
                .doesNotContain("Unrelated Popular Mosque");
    }

    @Test
    void generateReply_combinesDbAndPlacesContext_whenLiveModelActive() {
        AiModelClient liveModel = mock(AiModelClient.class);
        ChatKnowledgeService knowledgeService = new ChatKnowledgeService(placeRepository, routeRepository, questRepository);
        AiChatService aiChatService = new AiChatService(liveModel, knowledgeService, chatPlacesService);

        Place kemeralti = Place.builder()
                .name("Kemeralti Bazaar")
                .nameTr("Kemeraltı Çarşısı")
                .category("landmark")
                .keywords(List.of("kemeralti", "bazaar"))
                .build();
        when(placeRepository.findByIsActiveTrue()).thenReturn(List.of(kemeralti));
        when(routeRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(questRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(chatPlacesService.buildPlacesContext("kemeralti", Optional.of(38.42), Optional.of(27.13)))
                .thenReturn("- Kemeraltı Tarihi Çarşısı");
        when(liveModel.generate(anyString())).thenReturn("Kemeraltı önerisi");

        List<ChatMessage> conversation = List.of(
                ChatMessage.builder().role("user").content("kemeralti").build());

        String reply = aiChatService.generateReply(conversation, "tr", Optional.of(38.42), Optional.of(27.13));

        assertThat(reply).isEqualTo("Kemeraltı önerisi");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(liveModel).generate(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("Uygulama veritabanı bağlamı:")
                .contains("Kemeraltı Çarşısı")
                .contains("Google Places API'den canlı olarak çekilen mekanlar:");
    }

    @Test
    void generateReply_fallsBackToDemoReply_whenLiveModelThrows() {
        AiModelClient liveModel = mock(AiModelClient.class);
        ChatKnowledgeService knowledgeService = new ChatKnowledgeService(placeRepository, routeRepository, questRepository);
        AiChatService aiChatService = new AiChatService(liveModel, knowledgeService, chatPlacesService);

        Place clockTower = Place.builder()
                .name("İzmir Saat Kulesi")
                .nameTr("İzmir Saat Kulesi")
                .category("kule")
                .avgRating(4.9)
                .popularityScore(95)
                .build();
        when(placeRepository.findByIsActiveTrue()).thenReturn(List.of(clockTower));
        when(routeRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(questRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(chatPlacesService.buildPlacesContext(anyString(), any(), any())).thenReturn("");
        when(liveModel.generate(anyString())).thenThrow(new RuntimeException("401 Unauthorized"));

        List<ChatMessage> conversation = List.of(
                ChatMessage.builder().role("user").content("Saat Kulesi önerir misin?").build());

        String reply = aiChatService.generateReply(conversation, "tr", Optional.empty(), Optional.empty());

        assertThat(reply)
                .contains("İzmir Saat Kulesi")
                .contains("Demo Modu")
                .doesNotContain("AI modeline bağlanılamadı");
    }
}
