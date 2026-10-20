package com.wontlost.agui;

/** 代码块上可显示的动作；值与前端 codeActions 属性一致。 */
public enum AgUiCodeAction {
    COPY("copy"), INSERT("insert"), REPLACE("replace");

    private final String value;

    AgUiCodeAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
