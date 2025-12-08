package com.zhansc.clipboard;

import com.zhansc.clipboard.enums.ContentTypeEnum;

import java.util.*;

/**
 * @author zhanshuchan
 * @version 1.0
 * @description 剪贴板历史记录管理器
 * @date 12/4/25 20:07
 */
public class ClipboardHistory {
    // 最大历史记录数
    private final int maxSize;
    // 历史记录列表
    private final LinkedList<ClipboardItem> history;

    public ClipboardHistory(int maxSize) {
        this.maxSize = maxSize;
        this.history = new LinkedList<>();
    }

    /**
     * 添加新的剪贴板项到历史记录
     *
     * @param item 剪贴板项
     */
    public void addItem(ClipboardItem item) {
        // 先判断内容是否已经存在，存在则先删除再添加到历史记录开头
        if (item.getContentType() == ContentTypeEnum.IMAGE) {
            // 对于图片类型的项目，采用MD5值去重策略
            history.removeIf(existingItem -> existingItem.getContentType() == ContentTypeEnum.IMAGE
                    && existingItem.getContentMd5().equals(item.getContentMd5()));
        } else {
            // 对于文本和URL，使用原有的去重方法
            history.removeIf(existingItem -> existingItem.equals(item));
        }

        // 添加到开头（最新）
        history.addFirst(item);

        // 如果超出最大数量，移除最老的
        if (history.size() > maxSize) {
            history.removeLast();
        }
    }

    /**
     * 获取所有历史记录（按时间倒序）
     *
     * @return 历史记录列表
     */
    public List<ClipboardItem> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * 根据关键字搜索历史记录
     *
     * @param keyword 搜索关键字
     * @return 匹配的历史记录列表
     */
    public List<ClipboardItem> search(String keyword) {
        System.out.println("搜索关键词: '" + keyword + "'");

        if (keyword == null || keyword.trim().isEmpty()) {
            System.out.println("关键词为空，返回所有历史记录");
            return getHistory();
        }

        List<ClipboardItem> result = new ArrayList<>();
        for (ClipboardItem item : history) {
            if (item.getContentType() == ContentTypeEnum.TEXT ||
                    item.getContentType() == ContentTypeEnum.URL) {
                String content = (String) item.getContent();
                if (content.toLowerCase().contains(keyword.toLowerCase())) {
                    result.add(item);
                }
            }
        }
        System.out.println("找到 " + result.size() + " 个匹配项");
        return result;
    }

    /**
     * 清空历史记录
     */
    public void clear() {
        history.clear();
    }

    /**
     * 获取历史记录数量
     *
     * @return 历史记录数量
     */
    public int size() {
        return history.size();
    }
}