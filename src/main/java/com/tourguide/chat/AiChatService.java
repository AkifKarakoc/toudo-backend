package com.tourguide.chat;

import com.tourguide.chat.places.ChatPlacesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int MAX_CONTEXT_MESSAGES = 12;
    private static final String FALLBACK_RESPONSE = "AI modeline bağlanılamadı. Lütfen daha sonra tekrar deneyin.";

    private final AiModelClient aiModelClient;
    private final ChatKnowledgeService chatKnowledgeService;
    private final ChatPlacesService chatPlacesService;

    public String generateReply(List<ChatMessage> conversation, String language) {
        return generateReply(conversation, language, Optional.empty(), Optional.empty());
    }

    public String generateReply(
            List<ChatMessage> conversation,
            String language,
            Optional<Double> latitude,
            Optional<Double> longitude) {
        try {
            if (aiModelClient instanceof DemoAiModelClient) {
                return chatKnowledgeService.buildDemoReply(conversation, language);
            }

            String userQuery = latestUserMessage(conversation);
            String knowledgeContext = chatKnowledgeService.buildKnowledgeContext(conversation);
            String placesContext = chatPlacesService.buildPlacesContext(userQuery, latitude, longitude);
            String prompt = buildPrompt(conversation, language, knowledgeContext, placesContext);
            log.info("AI prompt length: {}, hasKnowledgeContext: {}, hasPlacesContext: {}",
                    prompt.length(), !knowledgeContext.isBlank(), !placesContext.isBlank());
            log.debug("AI prompt: {}", prompt);
            return aiModelClient.generate(prompt);
        } catch (Exception e) {
            log.error("AI model call failed: {}", e.getMessage());
            return FALLBACK_RESPONSE;
        }
    }

    private String buildPrompt(
            List<ChatMessage> conversation,
            String language,
            String knowledgeContext,
            String placesContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                Sen Smart Tourism Guide uygulamasının yerel turist rehberi asistanısın.
                Kullanıcıya kısa, net ve pratik cevap ver.
                Bilmediğin veya emin olmadığın bilgiyi uydurma; gerekirse bunu açıkça söyle.
                Turizm, rota, mekan, gezi planı ve uygulama kullanımı konularında yardımcı ol.
                Eğer veritabanı veya Google Places bağlamı varsa, önerilerini öncelikle bu kayıtlardan yap.
                Mekan, rota veya görev adı verirken yalnızca listede yer alan adları kullan.
                Cevap dili: Kullanıcının son mesajını yazdığı dil. Eğer son mesajın dili kesin olarak anlaşılamazsa """).append(normalizeLanguage(language)).append(" kullan.\n\n");

        if (knowledgeContext != null && !knowledgeContext.isBlank()) {
            prompt.append("Uygulama veritabanı bağlamı:\n")
                    .append(knowledgeContext)
                    .append("\n");
        }

        if (placesContext != null && !placesContext.isBlank()) {
            prompt.append("Google Places API'den canlı olarak çekilen mekanlar:\n")
                    .append(placesContext)
                    .append("\n");
        }

        prompt.append("Konuşma geçmişi:\n");
        conversation.stream()
                .skip(Math.max(0, conversation.size() - MAX_CONTEXT_MESSAGES))
                .forEach(message -> prompt
                        .append(message.getRole())
                        .append(": ")
                        .append(message.getContent())
                        .append("\n"));
        prompt.append("\nassistant:");
        return prompt.toString();
    }

    private String latestUserMessage(List<ChatMessage> conversation) {
        if (conversation == null) {
            return "";
        }
        for (int i = conversation.size() - 1; i >= 0; i--) {
            ChatMessage message = conversation.get(i);
            if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                return Objects.toString(message.getContent(), "");
            }
        }
        return "";
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank() || language.equalsIgnoreCase("tr")) {
            return "Türkçe";
        }
        if (language.equalsIgnoreCase("en")) {
            return "English";
        }
        return language;
    }
}
