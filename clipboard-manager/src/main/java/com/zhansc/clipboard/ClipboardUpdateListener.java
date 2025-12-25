package com.zhansc.clipboard;

/**
 * @author zhanshuchan
 * @version 1.0
 * @description 剪贴板更新监听器接口
 * @date 12/24/25 17:10
 */
public interface ClipboardUpdateListener {
    /**
     * 当剪贴板内容更新时调用
     */
    void onClipboardUpdated();
}