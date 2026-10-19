package com.oficinaagua.chatbot_api.llm;

import org.springframework.stereotype.Component;

import feign.RequestInterceptor;
import feign.RequestTemplate;

@Component
public class LlmAuthInterceptor implements RequestInterceptor {

    private final LlmProperties props;

    public LlmAuthInterceptor(LlmProperties props) {
        this.props = props;
    }

    @Override
    public void apply(RequestTemplate plantilla) {
        boolean esElCliente = plantilla.feignTarget() != null && "llm".equals(plantilla.feignTarget().name());
        if (esElCliente && !props.apiKey().isEmpty()) {
            plantilla.header("Authorization", "Bearer " + props.apiKey());
        }
    }
}
