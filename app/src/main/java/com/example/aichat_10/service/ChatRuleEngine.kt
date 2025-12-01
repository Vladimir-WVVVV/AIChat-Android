package com.example.aichat_10.service

/**
 * 对话规则引擎
 * 基于关键词匹配和预设规则进行对话
 */
object ChatRuleEngine {
    
    // 对话规则映射表
    private val rules = mapOf(
        // 问候语
        listOf("你好", "您好", "hello", "hi", "早上好", "下午好", "晚上好") to "你好！很高兴和你聊天，有什么我可以帮助你的吗？",
        
        // 自我介绍
        listOf("你是谁", "你是什么", "介绍", "介绍自己") to "我是一个AI助手，专门为你提供帮助和回答问题。",
        
        // 功能询问
        listOf("你能做什么", "功能", "帮助", "你能帮我什么") to "我可以回答你的问题、陪你聊天、提供建议等。你可以问我任何问题！",
        
        // 天气相关
        listOf("天气", "今天天气", "明天天气", "下雨") to "抱歉，我目前无法获取实时天气信息。建议你查看天气应用或询问语音助手。",
        
        // 时间相关
        listOf("现在几点", "时间", "几点了", "现在什么时候") to "你可以查看手机上的时间显示。",
        
        // 感谢
        listOf("谢谢", "感谢", "thank", "thanks") to "不客气！很高兴能帮到你。还有其他问题吗？",
        
        // 再见
        listOf("再见", "拜拜", "bye", "goodbye", "退出") to "再见！期待下次和你聊天。",
        
        // 询问状态
        listOf("你好吗", "怎么样", "如何") to "我很好，谢谢关心！你呢？",
        
        // 默认回复
        listOf("默认") to "这是一个很有趣的问题。让我想想... 你可以换个方式问我吗？"
    )
    
    /**
     * 根据用户输入生成回复
     */
    fun generateResponse(userInput: String): String {
        val input = userInput.trim().lowercase()
        
        // 遍历所有规则，查找匹配的关键词
        for ((keywords, response) in rules) {
            if (keywords.any { input.contains(it.lowercase()) }) {
                return response
            }
        }
        
        // 如果没有匹配到，使用智能回复
        return generateSmartResponse(input)
    }
    
    /**
     * 生成智能回复（当没有匹配到规则时）
     */
    private fun generateSmartResponse(input: String): String {
        return when {
            input.contains("?") || input.contains("？") -> {
                "这是一个好问题。虽然我没有预设的答案，但你可以尝试问我一些常见的问题，比如：\n" +
                "• 你是谁？\n" +
                "• 你能做什么？\n" +
                "• 你好"
            }
            input.length < 3 -> {
                "你的输入太短了，可以详细描述一下你的问题吗？"
            }
            input.length > 100 -> {
                "你的问题很详细。让我理解一下... 你可以尝试用更简洁的方式表达吗？"
            }
            else -> {
                "我理解你说的\"$input\"。虽然我没有预设这个问题的答案，但我会尽力帮助你。你可以尝试问我其他问题。"
            }
        }
    }
    
    /**
     * 获取欢迎消息
     */
    fun getWelcomeMessage(): String {
        return "你好！我是AI助手，很高兴为你服务。你可以问我任何问题，或者输入\"帮助\"查看我能做什么。"
    }
}

