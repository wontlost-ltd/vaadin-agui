package com.wontlost.agui.spring;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AG-UI 端点配置。
 *
 * @param path    端点前缀，agent 以 {@code <path>/<beanName>} 暴露；默认 {@code /agui}
 * @param timeout 单次运行的 SSE 超时；默认 10 分钟，长工具链场景可调大
 */
@ConfigurationProperties("agui")
public record AgUiProperties(
        @DefaultValue("/agui") String path,
        @DefaultValue("10m") Duration timeout) {
}
