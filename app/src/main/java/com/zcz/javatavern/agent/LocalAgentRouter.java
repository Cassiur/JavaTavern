package com.zcz.javatavern.agent;

import com.zcz.javatavern.model.AgentCard;

import java.util.Locale;

public final class LocalAgentRouter {
    public AgentCard route(String userMessage) {
        String normalized = userMessage.trim();
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        if (lowerCase.equals("/status")) {
            return new AgentCard(
                    "客户端能力检查",
                    "✓ 本地分页与 FTS 搜索\n✓ Agent 确认与审计\n✓ 原生结构化卡片\n✓ SSE 流式与取消\n✓ 图片多模态输入\n✓ JSON 角色卡与世界书",
                    "只读检查"
            );
        }
        if (lowerCase.startsWith("/plan")) {
            String goal = normalized.substring(Math.min(5, normalized.length())).trim();
            if (goal.isEmpty()) {
                goal = "完成一个可验证的小目标";
            }
            return new AgentCard(
                    "计划 Agent",
                    "目标：" + goal + "\n\n1. 明确完成标准\n2. 交付最小可运行结果\n3. 记录问题并安排下一步",
                    "本地生成"
            );
        }
        if (lowerCase.equals("/clear")) {
            return new AgentCard(
                    "清空当前会话",
                    "这会删除当前角色的全部聊天消息，但不会删除角色、模型设置或其他会话。该操作无法在应用内撤销。",
                    "需要确认",
                    "clear_conversation"
            );
        }
        return null;
    }
}
