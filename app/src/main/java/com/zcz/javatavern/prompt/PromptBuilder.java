package com.zcz.javatavern.prompt;

import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.Persona;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 构建器（雏形）
 * 
 * 负责将角色卡、用户人设、聊天历史等组装成完整的 Prompt
 * 后续会扩展支持：
 * - Author's Note
 * - 世界书注入
 * - Token 预算管理
 */
public class PromptBuilder {
    
    private final MacroEngine macroEngine;
    private final ExampleDialogueParser exampleParser;
    
    public PromptBuilder() {
        this.macroEngine = new MacroEngine();
        this.exampleParser = new ExampleDialogueParser();
    }
    
    /**
     * 构建系统 Prompt
     * 
     * @param character 角色信息
     * @param persona 用户人设
     * @param maxContext 最大上下文长度
     * @param includeExamples 是否包含示例对话
     * @return 系统 Prompt
     */
    public String buildSystemPrompt(
            CharacterProfile character,
            Persona persona,
            int maxContext,
            boolean includeExamples
    ) {
        List<String> parts = new ArrayList<>();
        
        // 角色描述
        if (!character.getDescription().isEmpty()) {
            parts.add(character.getDescription());
        }
        
        // 角色性格
        if (!character.getPersonality().isEmpty()) {
            parts.add("性格: " + character.getPersonality());
        }
        
        // 场景设定
        if (!character.getScenario().isEmpty()) {
            parts.add("场景: " + character.getScenario());
        }
        
        // 示例对话
        if (includeExamples && !character.getMesExample().isEmpty()) {
            List<ExampleDialogueParser.ExampleExchange> examples = 
                    exampleParser.parseExamples(
                            character.getMesExample(),
                            persona.getName(),
                            character.getName()
                    );
            
            if (!examples.isEmpty()) {
                String examplesText = exampleParser.formatExamplesForPrompt(
                        examples,
                        persona.getName(),
                        character.getName()
                );
                parts.add(examplesText);
            }
        }
        
        // 自定义系统 Prompt
        if (!character.getSystemPrompt().isEmpty()) {
            parts.add(character.getSystemPrompt());
        }
        
        String systemPrompt = String.join("\n\n", parts);
        
        // 替换宏
        return macroEngine.replaceMacros(systemPrompt, character, persona, maxContext);
    }
    
    /**
     * 构建系统 Prompt（默认包含示例对话）
     */
    public String buildSystemPrompt(
            CharacterProfile character,
            Persona persona,
            int maxContext
    ) {
        return buildSystemPrompt(character, persona, maxContext, true);
    }
    
    /**
     * 处理用户消息中的宏
     */
    public String processUserMessage(
            String message,
            CharacterProfile character,
            Persona persona,
            int maxContext
    ) {
        return macroEngine.replaceMacros(message, character, persona, maxContext);
    }
    
    /**
     * 获取宏引擎（供外部使用）
     */
    public MacroEngine getMacroEngine() {
        return macroEngine;
    }
    
    /**
     * 获取示例对话解析器（供外部使用）
     */
    public ExampleDialogueParser getExampleParser() {
        return exampleParser;
    }
}
