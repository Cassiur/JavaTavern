package com.zcz.javatavern.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成预设：一组命名采样参数，可一键应用/保存/删除。
 *
 * <p>预设是采样参数的「快照」。应用预设即把其参数复制到当前模型设置，
 * 不引入「激活预设 id」的间接层（保持请求链路简单）。
 */
public final class GenerationPreset {
    private final String id;
    private final String name;
    private final GenerationParams params;

    public GenerationPreset(String id, String name, GenerationParams params) {
        this.id = id;
        this.name = name;
        this.params = params;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public GenerationParams getParams() {
        return params;
    }

    /** 内置预设（首次安装 seed，用户可删）。 */
    public static List<GenerationPreset> builtInPresets() {
        List<GenerationPreset> presets = new ArrayList<>();
        presets.add(new GenerationPreset(
                "builtin:default", "服务端默认", GenerationParams.EMPTY));
        presets.add(new GenerationPreset(
                "builtin:creative", "创意发散",
                new GenerationParams(1.2, 0.95, null, null, null)));
        presets.add(new GenerationPreset(
                "builtin:precise", "严谨精确",
                new GenerationParams(0.3, 0.9, null, null, null)));
        presets.add(new GenerationPreset(
                "builtin:roleplay", "角色扮演",
                new GenerationParams(0.9, 0.98, null, 0.3, 0.3)));
        return presets;
    }
}
