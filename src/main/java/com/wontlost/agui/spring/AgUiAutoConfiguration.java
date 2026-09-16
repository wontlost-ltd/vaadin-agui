package com.wontlost.agui.spring;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wontlost.agui.agent.AgUiAgent;

/**
 * Spring Boot 自动配置：只要类路径上有 Spring MVC 且应用是 Servlet 型，就注册 AG-UI 端点。
 * <p>
 * 执行器优先复用 Boot 的 applicationTaskExecutor（开启虚拟线程时即为虚拟线程）；
 * 没有时退回到本地的虚拟线程执行器。agent 运行通常在等待模型输出，虚拟线程是最合适的载体。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SseEmitter.class)
@EnableConfigurationProperties(AgUiProperties.class)
public class AgUiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgUiController agUiController(Map<String, AgUiAgent> agents,
            ObjectProvider<AsyncTaskExecutor> executors, AgUiProperties properties) {
        AsyncTaskExecutor executor = executors.getIfAvailable(() -> {
            SimpleAsyncTaskExecutor local = new SimpleAsyncTaskExecutor("agui-");
            local.setVirtualThreads(true);
            return local;
        });
        return new AgUiController(agents, executor, properties);
    }
}
