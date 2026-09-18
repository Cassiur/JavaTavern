package com.zcz.javatavern.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * 示例对话解析器
 * 
 * 解析 SillyTavern 格式的示例对话（mes_example）
 * 格式：<START>\n{{user}}: xxx\n{{char}}: yyy\n<START>\n...
 */
public class ExampleDialogueParser {
    
    /**
     * 示例对话交互
     */
    public static class ExampleExchange {
        private final String userMessage;
        private final String characterMessage;
        
        public ExampleExchange(String userMessage, String characterMessage) {
            this.userMessage = userMessage;
            this.characterMessage = characterMessage;
        }
        
        public String getUserMessage() {
            return userMessage;
        }
        
        public String getCharacterMessage() {
            return characterMessage;
        }
    }
    
    /**
     * 解析示例对话
     * 
     * @param mesExample 原始示例对话文本
     * @param userName 用户名称（用于替换 {{user}}）
     * @param charName 角色名称（用于替换 {{char}}）
     * @return 示例对话列表
     */
    public List<ExampleExchange> parseExamples(String mesExample, String userName, String charName) {
        List<ExampleExchange> examples = new ArrayList<>();
        
        if (mesExample == null || mesExample.trim().isEmpty()) {
            return examples;
        }
        
        // 按 <START> 分割
        String[] blocks = mesExample.split("<START>");
        
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 解析单个示例块（一个 <START> 块内可能有多轮交替对话）
            examples.addAll(parseBlock(trimmed, userName, charName));
        }

        return examples;
    }

    /**
     * 解析单个示例块，支持块内多轮交替的 user/char 行——每遇到一个 char 行就
     * 与最近一个待配对的 user 行组成一轮，而不是只保留块内最后一组。
     */
    private List<ExampleExchange> parseBlock(String block, String userName, String charName) {
        List<ExampleExchange> exchanges = new ArrayList<>();
        String[] lines = block.split("\n");
        String pendingUserMsg = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 匹配 {{user}}: 或 userName:
            if (trimmed.startsWith("{{user}}:") || trimmed.startsWith(userName + ":")) {
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0 && colonIndex < trimmed.length() - 1) {
                    pendingUserMsg = trimmed.substring(colonIndex + 1).trim();
                }
            }
            // 匹配 {{char}}: 或 charName:
            else if (trimmed.startsWith("{{char}}:") || trimmed.startsWith(charName + ":")) {
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0 && colonIndex < trimmed.length() - 1 && pendingUserMsg != null) {
                    String charMsg = trimmed.substring(colonIndex + 1).trim();
                    exchanges.add(new ExampleExchange(pendingUserMsg, charMsg));
                    pendingUserMsg = null;
                }
            }
        }

        return exchanges;
    }
    
    /**
     * 将示例对话格式化为 Prompt 文本
     * 
     * @param examples 示例对话列表
     * @param userName 用户名称
     * @param charName 角色名称
     * @return 格式化的文本
     */
    public String formatExamplesForPrompt(List<ExampleExchange> examples, String userName, String charName) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("以下是对话示例：\n\n");
        
        for (int i = 0; i < examples.size(); i++) {
            ExampleExchange ex = examples.get(i);
            sb.append(userName).append(": ").append(ex.getUserMessage()).append("\n");
            sb.append(charName).append(": ").append(ex.getCharacterMessage()).append("\n");
            
            if (i < examples.size() - 1) {
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
}
