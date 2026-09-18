package com.zcz.javatavern.prompt;

import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.Persona;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SillyTavern 宏替换引擎
 * 
 * 支持的宏：
 * - {{char}} - 角色名称
 * - {{user}} - 用户名称
 * - {{random::option1::option2::...}} - 随机选择
 * - {{roll:1d20}} - 掷骰子
 * - {{maxc}} - 最大上下文长度
 * - {{minc}} - 最小上下文长度
 */
public class MacroEngine {
    
    private static final Pattern RANDOM_PATTERN = Pattern.compile("\\{\\{random::([^}]+)\\}\\}");
    private static final Pattern ROLL_PATTERN = Pattern.compile("\\{\\{roll:(\\d+)d(\\d+)\\}\\}");
    
    private final Random random = new Random();
    
    /**
     * 替换文本中的所有宏
     * 
     * @param text 原始文本
     * @param character 角色信息
     * @param persona 用户人设
     * @param maxContext 最大上下文长度
     * @return 替换后的文本
     */
    public String replaceMacros(
            String text,
            CharacterProfile character,
            Persona persona,
            int maxContext
    ) {
        return replaceMacros(text, character.getName(), persona.getName(), maxContext);
    }

    /**
     * 同上，但直接take角色名/用户名字符串——调用方只有名字、没有完整
     * {@link CharacterProfile}/{@link Persona} 实例时用这个重载（例如网络层只
     * 从设置里解析出了 persona 名字，不想为了替宏而构造整个 Persona 对象）。
     */
    public String replaceMacros(
            String text,
            String charName,
            String userName,
            int maxContext
    ) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // 基础宏
        result = result.replace("{{char}}", charName);
        result = result.replace("{{user}}", userName);

        // 上下文宏
        result = result.replace("{{maxc}}", String.valueOf(maxContext));
        result = result.replace("{{minc}}", String.valueOf(maxContext / 2));

        // {{random::}} 宏
        result = replaceRandomMacros(result);

        // {{roll:}} 宏
        result = replaceRollMacros(result);

        return result;
    }
    
    /**
     * 替换 {{random::option1::option2::...}} 宏
     */
    private String replaceRandomMacros(String text) {
        Matcher matcher = RANDOM_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String optionsStr = matcher.group(1);
            String[] options = optionsStr.split("::");
            
            if (options.length > 0) {
                String selected = options[random.nextInt(options.length)].trim();
                matcher.appendReplacement(sb, Matcher.quoteReplacement(selected));
            }
        }
        
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * 替换 {{roll:NdM}} 宏（例如 {{roll:1d20}}）
     */
    private String replaceRollMacros(String text) {
        Matcher matcher = ROLL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            int sides = Integer.parseInt(matcher.group(2));
            
            int total = 0;
            for (int i = 0; i < count; i++) {
                total += random.nextInt(sides) + 1;
            }
            
            matcher.appendReplacement(sb, String.valueOf(total));
        }
        
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * 检查文本中是否包含宏
     */
    public boolean containsMacros(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("{{") && text.contains("}}");
    }
}
