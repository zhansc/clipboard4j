package com.zhansc.clipboard;

import com.zhansc.clipboard.enums.ContentTypeEnum;

import java.awt.*;
import java.awt.datatransfer.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author zhanshuchan
 * @version 1.0
 * @description 剪贴板监控器，定期检查剪贴板内容变化
 * @date 12/4/25 20:07
 */
public class ClipboardMonitor {
    private final ClipboardHistory history;
    private final Clipboard clipboard;
    // 上次检测到的内容
    private Object lastContent;
    // 上次内容的类型
    private ContentTypeEnum lastContentType;
    // 上次内容的时间戳
    private long lastContentTimestamp;
    
    public ClipboardMonitor(ClipboardHistory history) {
        this.history = history;
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        this.lastContent = null;
        this.lastContentType = null;
        this.lastContentTimestamp = 0;
    }
    
    private ScheduledExecutorService scheduler;
    
    /**
     * 开始监控剪贴板
     */
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkClipboard, 0, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 停止监控剪贴板
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    
    /**
     * 检查剪贴板内容是否有变化
     */
    private void checkClipboard() {
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents == null) {
                return;
            }
            
            // 处理不同类型的内容
            ClipboardItem item = this.processContent(contents);
            if (item != null) {
                // 检查是否与上次内容相同
                if (isDuplicate(item)) {
                    return;
                }
                
                System.out.println("添加新项目到历史记录: " + item.getTextPreview());
                history.addItem(item);
                // 更新上次内容的引用和时间戳
                this.updateLastContent(item);
            }
        } catch (Exception e) {
            System.err.println("监控剪贴板时发生错误: " + e.getMessage());
            // 打印详细错误信息
            e.printStackTrace();
            // 忽略监控过程中的异常
        }
    }
    
    /**
     * 更新上次内容记录
     */
    private void updateLastContent(ClipboardItem item) {
        lastContent = item.getContent();
        lastContentType = item.getContentType();
        lastContentTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 判断是否为重复内容
     */
    private boolean isDuplicate(ClipboardItem currentItem) {
        // 检查内容是否为空
        if (lastContent == null || lastContentType == null) {
            return false;
        }
        
        // 检查内容类型是否相同
        if (currentItem.getContentType() != lastContentType) {
            return false;
        }
        
        // 检查时间间隔，如果在短时间内（1秒内）则进一步判断是否为相同内容
        long timeDiff = System.currentTimeMillis() - lastContentTimestamp;
        if (timeDiff < 1000) {
            // 对于文本和URL，精确比较内容
            if (currentItem.getContentType() == ContentTypeEnum.TEXT ||
                currentItem.getContentType() == ContentTypeEnum.URL) {
                return currentItem.getContent().equals(lastContent);
            }
            
            // 对于图片，在短时间内认为是相同的
            return currentItem.getContentType() == ContentTypeEnum.IMAGE;
        }
        return false;
    }
    
    /**
     * 处理剪贴板内容并创建相应的ClipboardItem
     */
    private ClipboardItem processContent(Transferable contents) throws Exception {
        // 检查是否为文本
        if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
            
            // 检查是否为空或纯空白字符
            if (text.trim().isEmpty()) {
                return null;
            }
            
            // 检查是否为URL
            if (isUrl(text)) {
                return new ClipboardItem(null, text, ContentTypeEnum.URL);
            }
            
            // 普通文本
            return new ClipboardItem(null, text, ContentTypeEnum.TEXT);
        }
        
        // 检查是否为图片
        if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
            String contentMd5 = this.calculateImageMD5(image);
            return new ClipboardItem(contentMd5, image, ContentTypeEnum.IMAGE);
        }
        
        return null;
    }
    
    /**
     * 计算图片的MD5值
     * @param image 图片对象
     * @return 图片的MD5值
     */
    private String calculateImageMD5(Image image) {
        try {
            // 将Image转换为BufferedImage
            BufferedImage bufferedImage = new BufferedImage(
                image.getWidth(null), 
                image.getHeight(null), 
                BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g = bufferedImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            
            // 将图片写入字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // 计算MD5
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(imageBytes);
            byte[] digest = md.digest();
            
            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("计算图片MD5时出错: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 判断字符串是否为有效的URL
     */
    private boolean isUrl(String text) {
        // 至少要有"http"四个字符
        if (text == null || text.length() < 4) {
            return false;
        }
        
        // 先检查是否以常见的协议开头
        if (!(text.startsWith("http://") || text.startsWith("https://"))) {
            return false;
        }
        
        try {
            new URL(text);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}