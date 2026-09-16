package com.wontlost.agui;

/**
 * 主题变体。名称不带 Lumo 或 Aura 前缀，两套主题下同名生效，
 * 与 Vaadin 25.1 起官方组件的"公共变体"约定一致。
 */
public enum AgUiChatVariant {

    /** 更紧凑的行距与内边距，适合侧栏或悬浮窗。 */
    COMPACT("compact"),

    /** 不绘制气泡，只用左右对齐与颜色区分角色。 */
    FLAT("flat");

    private final String variant;

    AgUiChatVariant(String variant) {
        this.variant = variant;
    }

    public String getVariantName() {
        return variant;
    }
}
