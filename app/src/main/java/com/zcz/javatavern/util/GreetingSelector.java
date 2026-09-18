package com.zcz.javatavern.util;

import java.util.List;
import java.util.Random;

/**
 * 开场白选择器
 * 
 * 支持多个备用开场白（Alternate Greetings）
 */
public class GreetingSelector {
    
    private final Random random = new Random();
    
    /**
     * 获取开场白
     * 
     * @param primaryGreeting 主开场白
     * @param alternateGreetings 备用开场白列表
     * @param index 指定索引（-1 表示使用主开场白，-2 表示随机）
     * @return 选中的开场白
     */
    public String selectGreeting(
            String primaryGreeting,
            List<String> alternateGreetings,
            int index
    ) {
        // 使用主开场白
        if (index == -1 || alternateGreetings == null || alternateGreetings.isEmpty()) {
            return primaryGreeting;
        }
        
        // 随机选择
        if (index == -2) {
            // 包括主开场白在内的所有选项
            int totalCount = 1 + alternateGreetings.size();
            int randomIndex = random.nextInt(totalCount);
            
            if (randomIndex == 0) {
                return primaryGreeting;
            } else {
                return alternateGreetings.get(randomIndex - 1);
            }
        }
        
        // 指定索引（0 = 第一个备用）
        if (index >= 0 && index < alternateGreetings.size()) {
            return alternateGreetings.get(index);
        }
        
        // 索引越界，返回主开场白
        return primaryGreeting;
    }
    
    /**
     * 获取开场白总数（包括主开场白）
     */
    public int getTotalGreetingCount(List<String> alternateGreetings) {
        return 1 + (alternateGreetings != null ? alternateGreetings.size() : 0);
    }
    
    /**
     * 获取所有开场白（主开场白 + 备用）
     */
    public List<String> getAllGreetings(String primaryGreeting, List<String> alternateGreetings) {
        List<String> allGreetings = new java.util.ArrayList<>();
        allGreetings.add(primaryGreeting);
        if (alternateGreetings != null && !alternateGreetings.isEmpty()) {
            allGreetings.addAll(alternateGreetings);
        }
        return allGreetings;
    }
}
