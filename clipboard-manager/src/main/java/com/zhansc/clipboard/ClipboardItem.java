package com.zhansc.clipboard;

import com.zhansc.clipboard.enums.ContentTypeEnum;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author zhanshuchan
 * @version 1.0
 * @description 剪贴板项目数据模型
 * @date 12/4/25 20:07
 */
public class ClipboardItem {
    // 时间戳
    private final LocalDateTime timestamp;
    // 内容类型
    private final ContentTypeEnum contentType;
    // 实际内容
    private final Object content;
    // 内容MD5值
    private String contentMd5;
    // 图片预览信息
    private String imagePreview;

    public ClipboardItem(String contentMd5, Object content, ContentTypeEnum contentType) {
        this.contentMd5 = contentMd5;
        this.content = content;
        this.contentType = contentType;
        this.timestamp = LocalDateTime.now();
        
        // 如果是图片类型，生成预览信息
        if (contentType == ContentTypeEnum.IMAGE && content != null) {
            this.imagePreview = this.generateImagePreview(content);
        }
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public ContentTypeEnum getContentType() {
        return contentType;
    }
    
    public Object getContent() {
        return content;
    }

    public String getContentMd5() {
        return contentMd5;
    }
    
    public String getTextPreview() {
        switch (contentType) {
            case TEXT:
                String text = (String) content;
                return text.length() > 100 ? text.substring(0, 100) + "..." : text;
            case URL:
                return (String) content;
            case IMAGE:
                return this.imagePreview != null ? this.imagePreview : "[图片]";
            default:
                return "";
        }
    }
    
    /**
     * 生成图片预览信息
     * @param content 图片内容
     * @return 图片预览描述
     */
    private String generateImagePreview(Object content) {
        if (content instanceof java.awt.Image) {
            java.awt.Image image = (java.awt.Image) content;
            return "[图片 " + image.getWidth(null) + "x" + image.getHeight(null) + "]";
        }
        return "[图片]";
    }
    
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ClipboardItem that = (ClipboardItem) object;

        // 对于图片类型，我们只比较类型而不比较具体内容，因为图片对象难以准确比较
        if (this.contentType == ContentTypeEnum.IMAGE && that.contentType == ContentTypeEnum.IMAGE) {
            return Objects.equals(contentMd5, that.content);
        }
        
        return Objects.equals(content, that.content) &&
               contentType == that.contentType;
    }
    
    @Override
    public int hashCode() {
        // 对于图片，我们使用内容类型作为哈希码，因为图片对象难以准确哈希
        if (contentType == ContentTypeEnum.IMAGE) {
            return Objects.hash(contentMd5, contentType);
        }
        return Objects.hash(content, contentType);
    }
}