package com.zhansc.clipboard;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.IOException;

/**
 * @author zhanshuchan
 * @version 1.0
 * @description 调用macOS剪贴板功能demo
 * @date 12/4/25 20:07
 */
public class ClipboardDemo {
    /**
     * 写入文本到剪贴板
     * @param text 要写入的文本
     */
    public static void writeToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    /**
     * 从剪贴板读取文本
     * @return 剪贴板中的文本，如果读取失败则返回null
     */
    public static String readFromClipboard() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                return (String) contents.getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException | IOException e) {
                System.err.println("无法读取剪贴板内容: " + e.getMessage());
            }
        }
        return null;
    }

    public static void main(String[] args) {
        // 示例：将文本复制到剪贴板并读取
        writeToClipboard("Hello, macOS Clipboard!");
        String text = readFromClipboard();
        if (text != null) {
            System.out.println("从剪贴板读取的内容: " + text);
        }
    }
}
