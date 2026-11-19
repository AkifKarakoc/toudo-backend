package com.tourguide.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local demo {@link AiModelClient} that allows running chat endpoints without
 * an external Ollama or Gemini instance. {@link AiChatService} intercepts this
 * provider to return database-grounded Izmir recommendations via
 * {@link ChatKnowledgeService}.
 */
@Service
@ConditionalOnProperty(name = "ai-service.provider", havingValue = "demo", matchIfMissing = true)
public class DemoAiModelClient implements AiModelClient {

    @Override
    public String generate(String prompt) {
        return prompt;
    }
}
