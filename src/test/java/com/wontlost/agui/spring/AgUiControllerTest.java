package com.wontlost.agui.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import com.wontlost.agui.agent.AgUiAgent;
import com.wontlost.agui.event.AgUiEvent;

@SpringBootTest(classes = AgUiControllerTest.App.class, properties = "agui.path=/ai")
class AgUiControllerTest {

    @Configuration
    @EnableAutoConfiguration
    static class App {
        /** bean 名即端点名：/ai/echo */
        @Bean
        AgUiAgent echo() {
            return (input, emitter) -> {
                emitter.emit(new AgUiEvent.RunStarted(input.threadId(), input.runId()));
                emitter.emit(new AgUiEvent.TextMessageStart("m1"));
                emitter.emit(new AgUiEvent.TextMessageContent("m1", "echo: " + input.messages().getLast().content()));
                emitter.emit(new AgUiEvent.TextMessageEnd("m1"));
                emitter.emit(new AgUiEvent.RunFinished(input.threadId(), input.runId()));
                emitter.complete();
            };
        }

        @Bean
        AgUiAgent broken() {
            return (input, emitter) -> {
                throw new IllegalStateException("model unavailable");
            };
        }
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final String BODY = """
            {"threadId":"t1","runId":"r1","messages":[{"id":"u1","role":"user","content":"hi"}]}
            """;

    @Test
    @DisplayName("POST <path>/<bean> 以 SSE 返回 agent 发出的事件")
    void streamsEvents() throws Exception {
        MvcResult result = mvc.perform(post("/ai/echo").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).content(BODY))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("data:{\"type\":\"RUN_STARTED\",\"threadId\":\"t1\",\"runId\":\"r1\"}");
        assertThat(body).contains("\"delta\":\"echo: hi\"");
        assertThat(body).contains("\"type\":\"RUN_FINISHED\"");
    }

    @Test
    @DisplayName("agent 抛异常时客户端收到 RUN_ERROR 而不是断连")
    void agentFailureBecomesRunError() throws Exception {
        MvcResult result = mvc.perform(post("/ai/broken").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        assertThat(result.getResponse().getContentAsString())
                .contains("{\"type\":\"RUN_ERROR\",\"message\":\"model unavailable\"}");
    }

    @Test
    @DisplayName("没有对应 bean 的 agentId 返回 404")
    void unknownAgentIs404() throws Exception {
        mvc.perform(post("/ai/nope").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}
