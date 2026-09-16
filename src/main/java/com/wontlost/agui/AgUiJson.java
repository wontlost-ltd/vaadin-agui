package com.wontlost.agui;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 整个组件包共用的 Jackson 3 映射器。
 * <p>
 * 统一在这里配置两条规则：序列化时省略 null 字段（AG-UI 事件的可选字段很多，
 * 省略后与 TypeScript 参考实现的输出一致）；反序列化时忽略未知字段（协议仍在演进，
 * 前端或第三方 agent 可能带上新字段，不应因此中断）。
 */
public final class AgUiJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private AgUiJson() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
