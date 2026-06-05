package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.entity.CodingTask;

public interface CodingMessageEnricher {

    /**
     * 解析 @path 引用，将工作区文件内容附加到用户消息（Claude @file 行为）。
     */
    String enrichUserMessage(String userMessage, CodingTask task);
}
