package com.zhansc.clipboard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.dispatcher.SwingDispatchService;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

/**
 * @author zhanshuchan
 * @version 1.0
 * @description 剪贴板管理器主界面
 * @date 12/4/25 20:07
 */
public class ClipboardManager extends JFrame implements ClipboardUpdateListener {
    private final ClipboardHistory history;
    private final ClipboardMonitor monitor;
    private JList<ClipboardItem> itemList;
    private DefaultListModel<ClipboardItem> listModel;
    private JTextField searchField;
    private JButton clearButton;
    private JLabel statusLabel;
    private JLabel copyStatusLabel; // 用于显示复制状态的标签
    
    private static final String DEFAULT_PLACEHOLDER = "请输入关键词";
    
    private static final int SHIFT_MASK = 1 << 6; // Shift键掩码
    private static final int META_MASK = 1 << 22; // Meta键掩码 (Command键在Mac上)
    
    // 用于跟踪当前按下的键
    private boolean shiftPressed = false;
    private boolean metaPressed = false;
    
    public ClipboardManager() {
        // 初始化组件
        history = new ClipboardHistory(100);
        monitor = new ClipboardMonitor(history);
        
        // 设置窗口属性
        this.setTitle("剪贴板管理器");
        // 改为隐藏而不是退出
        // TODO: zhanshuchan 12/5/25 功能待完善
//        this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(600, 500);
        this.setLocationRelativeTo(null);
        
        // 创建界面
        this.createUI();
        
        // 启动剪贴板监控
        monitor.setUpdateListener(this);
        monitor.start();
        
        // 定期刷新列表以显示新增的剪贴板内容
//        Timer refreshTimer = new Timer(500, e -> refreshList());
//        refreshTimer.start();
        
        // 注册全局键盘监听器
//        this.registerGlobalKeyListener();
        
        // 注册系统托盘图标
//        this.registerSystemTray();
        
        // 注册全局键盘监听器
        this.registerGlobalHotkey();
    }
    
    /**
     * 注册全局热键监听器
     */
    private void registerGlobalHotkey() {
        try {
            // 设置 Swing 事件调度
            GlobalScreen.setEventDispatcher(new SwingDispatchService());
            
            // 注册全局键盘监听器
            GlobalScreen.registerNativeHook();
            
            // 添加键盘监听器
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    // 检测Shift键
                    if (e.getKeyCode() == NativeKeyEvent.VC_SHIFT) {
                        shiftPressed = true;
                    }
                    // 检测Meta键 (Command键)
                    else if (e.getKeyCode() == NativeKeyEvent.VC_META) {
                        metaPressed = true;
                    }
                    
                    // 检测A/C/V键，当Shift和Command键都按下时 (Shift+Command+A/C/V)
                    if (shiftPressed && metaPressed && Arrays.asList(NativeKeyEvent.VC_A, NativeKeyEvent.VC_C, NativeKeyEvent.VC_V).contains(e.getKeyCode())) {
                        // 在事件调度线程中执行界面操作
                        SwingUtilities.invokeLater(() -> {
                            toggleVisibility();

                            // 等窗口可见后再执行搜索框聚焦
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            // 确保搜索框获得焦点
                            if (searchField != null) {
                                searchField.requestFocusInWindow();
                            }
                        });
                    }
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    // 释放Shift键
                    if (e.getKeyCode() == NativeKeyEvent.VC_SHIFT) {
                        shiftPressed = false;
                    }
                    // 释放Meta键 (Command键)
                    else if (e.getKeyCode() == NativeKeyEvent.VC_META) {
                        metaPressed = false;
                    }
                    
                    // 确保在释放任一键时重置状态，避免状态混乱
                    if (e.getKeyCode() == NativeKeyEvent.VC_V || 
                        e.getKeyCode() == NativeKeyEvent.VC_C || 
                        e.getKeyCode() == NativeKeyEvent.VC_A) {
                        // 释放快捷键相关键时重置，确保组合键状态正确
                        shiftPressed = false;
                        metaPressed = false;
                    }
                }

                @Override
                public void nativeKeyTyped(NativeKeyEvent e) {
                    // 不需要实现
                }
            });
            
            System.out.println("全局热键监听器注册成功，支持的快捷键: Shift+Command+V, Shift+Command+C, Shift+Command+A");
        } catch (NativeHookException ex) {
            System.err.println("注册全局热键监听器失败: " + ex.getMessage());
            System.err.println("提示: 在macOS上，您可能需要在系统偏好设置 > 安全性与隐私 > 辅助功能中授权此应用");
        }
    }
    
    /**
     * 注册全局键盘监听器
     */
    private void registerGlobalKeyListener() {
        System.out.println("注册全局键盘监听器...");
        
        // 添加窗口焦点监听器，确保窗口获得焦点时能响应键盘事件
        this.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                System.out.println("窗口获得焦点");
                searchField.requestFocusInWindow();
            }
        });
        
        // 注册一个AWT事件监听器来捕获全局按键事件
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event instanceof KeyEvent) {
                    KeyEvent keyEvent = (KeyEvent) event;

                    System.out.println("按键事件: ID=" + keyEvent.getID() +
                            ", keyCode=" + keyEvent.getKeyCode() +
                            ", modifiers=" + keyEvent.getModifiersEx() +
                            ", isMetaDown=" + keyEvent.isMetaDown() +
                            ", isShiftDown=" + keyEvent.isShiftDown() +
                            ", isControlDown=" + keyEvent.isControlDown() +
                            ", isAltDown=" + keyEvent.isAltDown());
                    if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {

                        // 检查是否按下了 Command+Shift+V (在Mac上)
                        // 注意：在Mac上VK_V可能对应不同的键码，我们尝试多种组合
                        System.out.println("检测到 Command+Shift+V 组合键（AWT事件监听器）");
                        if (keyEvent.isMetaDown() && keyEvent.isShiftDown() && keyEvent.getKeyCode() == KeyEvent.VK_V) {
                            toggleVisibility();
                            keyEvent.consume();
                            return;
                        }
                        
                        // 也尝试监听 Ctrl+Shift+V 作为备选方案
                        System.out.println("检测到 Ctrl+Shift+V 组合键（AWT事件监听器）");
                        if (keyEvent.isControlDown() && keyEvent.isShiftDown() && keyEvent.getKeyCode() == KeyEvent.VK_V) {
                            toggleVisibility();
                            keyEvent.consume();
                            return;
                        }
                    }
                }
            }
        }, AWTEvent.KEY_EVENT_MASK);
        
        System.out.println("全局键盘监听器注册完成");
    }
    
    /**
     * 注册系统托盘图标
     */
    private void registerSystemTray() {
        try {
            if (!SystemTray.isSupported()) {
                System.out.println("系统托盘不支持");
                return;
            }
            System.out.println("系统托盘支持可用");
            SystemTray tray = SystemTray.getSystemTray();

            // 创建一个简单的图像作为托盘图标
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.BLUE);
            g2d.fillRect(0, 0, 16, 16);
            g2d.setColor(Color.WHITE);
            g2d.drawString("Clipboard", 10, 12);
            g2d.dispose();

            TrayIcon trayIcon = new TrayIcon(image, "剪贴板管理器");
            trayIcon.setImageAutoSize(true);

            // 添加鼠标监听器，点击托盘图标时显示窗口
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1) { // 单击
                        System.out.println("托盘图标被点击");
                        toggleVisibility();
                    }
                }
            });

            // 添加右键菜单
            PopupMenu popup = new PopupMenu();
            MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(exitItem);

            MenuItem showItem = new MenuItem("显示/隐藏");
            showItem.addActionListener(e -> toggleVisibility());
            popup.add(showItem);

            trayIcon.setPopupMenu(popup);

            tray.add(trayIcon);
            System.out.println("系统托盘图标注册成功");
        } catch (Exception e) {
            System.err.println("注册系统托盘图标失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 切换窗口可见性
     */
    private void toggleVisibility() {
        if (this.isVisible()) {
            this.setVisible(false);
        } else {
            this.setVisible(true);
            // 如果是最小化状态，恢复窗口
            this.setState(JFrame.NORMAL);
            // 确保窗口在最前面
            this.toFront();
            // 确保窗口获得焦点
            this.requestFocus();
            // 让搜索框获得焦点
            SwingUtilities.invokeLater(() -> {
                if (searchField != null) {
                    searchField.requestFocusInWindow();
                    // 选择所有文本，方便用户直接输入
                    searchField.selectAll();
                }
            });
        }
    }
    
    /**
     * 创建用户界面
     */
    private void createUI() {
        setLayout(new BorderLayout());
        
        // 创建顶部面板（搜索区域）
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 修改搜索框，添加占位符文本功能
        searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {  // 只要文本为空就显示占位符
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(Color.GRAY);
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    FontMetrics metrics = g2.getFontMetrics();
                    int x = getInsets().left;
                    int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                    g2.drawString(DEFAULT_PLACEHOLDER, x, y);
                    g2.dispose();
                }
            }
        };
        searchField.setText(""); // 初始时不设置默认文本，仅作为占位符显示
        searchField.setToolTipText("输入关键字搜索剪贴板历史");
        
        // 添加文档监听器，实现实时搜索
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                performSearch();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                performSearch();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                performSearch();
            }
        });
        
        clearButton = new JButton("清空历史");
        clearButton.addActionListener(e -> this.clearHistory());
        
        topPanel.add(new JLabel("搜索:"), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(clearButton, BorderLayout.EAST);
        
        // 创建中部面板（列表区域）
        listModel = new DefaultListModel<>();
        itemList = new JList<>(listModel);
        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemList.setCellRenderer(new ClipboardItemRenderer());
        itemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // 双击时仅当选中有效项时才复制
                    if (itemList.getSelectedValue() != null) {
                        copyToClipboard();
                    }
                }
            }
        });
        
        // 添加鼠标移动监听器实现悬浮选中
        itemList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = itemList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    itemList.setSelectedIndex(index);
                }
            }
        });
        
        // 添加键盘监听器支持上下键导航
        itemList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int selectedIndex = itemList.getSelectedIndex();
                int lastIndex = listModel.getSize() - 1;
                
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:
                        if (selectedIndex > 0) {
                            itemList.setSelectedIndex(selectedIndex - 1);
                            itemList.ensureIndexIsVisible(selectedIndex - 1);
                        }
                        e.consume();
                        break;
                    case KeyEvent.VK_DOWN:
                        if (selectedIndex < lastIndex) {
                            itemList.setSelectedIndex(selectedIndex + 1);
                            itemList.ensureIndexIsVisible(selectedIndex + 1);
                        }
                        e.consume();
                        break;
                    case KeyEvent.VK_ENTER:
                        // Enter键仅当选中有效项时才复制
                        if (itemList.getSelectedValue() != null) {
                            copyToClipboard();
                        }
                        e.consume();
                        break;
                }
            }
        });
        
        // 为搜索框添加键盘监听器，支持在搜索结果中使用上下键导航
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // 当搜索框有内容时，允许使用上下键在列表中导航
                if (!searchField.getText().isEmpty()) {
                    int selectedIndex = itemList.getSelectedIndex();
                    int lastIndex = listModel.getSize() - 1;
                    
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP:
                            if (selectedIndex > 0) {
                                itemList.setSelectedIndex(selectedIndex - 1);
                                itemList.ensureIndexIsVisible(selectedIndex - 1);
                            }
                            e.consume();
                            break;
                        case KeyEvent.VK_DOWN:
                            if (selectedIndex < lastIndex) {
                                itemList.setSelectedIndex(selectedIndex + 1);
                                itemList.ensureIndexIsVisible(selectedIndex + 1);
                            }
                            e.consume();
                            break;
                        case KeyEvent.VK_ENTER:
                            // Enter键仅当选中有效项时才复制
                            if (itemList.getSelectedValue() != null) {
                                copyToClipboard();
                            }
                            e.consume();
                            break;
                    }
                }
            }
        });
        
        // 确保 itemList 可以获得焦点
        itemList.setFocusable(true);
        
        JScrollPane scrollPane = new JScrollPane(itemList);
        
        // 创建底部面板（状态栏）
        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
        copyStatusLabel = new JLabel();
        copyStatusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
        copyStatusLabel.setHorizontalAlignment(JLabel.RIGHT);
        
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(copyStatusLabel, BorderLayout.EAST);
        
        // 组装界面
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 刷新列表显示
     */
    private void refreshList() {
        // 只有在搜索框为空时才刷新整个列表，避免干扰用户的搜索结果
        String searchText = searchField.getText();
        if (searchText.trim().isEmpty()) {
            listModel.clear();
            for (ClipboardItem item : history.getHistory()) {
                listModel.addElement(item);
            }
            this.updateStatus();
        }
    }
    
    /**
     * 执行搜索操作
     */
    private void performSearch() {
        SwingUtilities.invokeLater(() -> {
            String keyword = searchField.getText();
            System.out.println("执行搜索: '" + keyword + "'");
            listModel.clear();
            
            // 如果关键词为空，则显示所有历史记录
            if (keyword.isEmpty()) {
                for (ClipboardItem item : history.getHistory()) {
                    listModel.addElement(item);
                }
            } else {
                // 否则显示匹配的历史记录
                for (ClipboardItem item : history.search(keyword)) {
                    listModel.addElement(item);
                }
            }

            this.updateStatus();
        });
    }
    
    /**
     * 清空历史记录
     */
    private void clearHistory() {
        int result = JOptionPane.showConfirmDialog(
            this,
            "确定要清空所有剪贴板历史记录吗？",
            "确认清空",
            JOptionPane.YES_NO_OPTION
        );
        
        if (result == JOptionPane.YES_OPTION) {
            history.clear();
            this.refreshList();
            // 清空搜索框内容
            searchField.setText("");
            // 清空复制状态信息
            copyStatusLabel.setText("");
        }
    }
    
    /**
     * 将选中项复制到剪贴板
     */
    private void copyToClipboard() {
        ClipboardItem selected = itemList.getSelectedValue();
        if (selected == null) {
            return;
        }
        switch (selected.getContentType()) {
            case TEXT:
            case URL:
                String text = (String) selected.getContent();
                java.awt.datatransfer.Clipboard clipboard =
                        Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(text), null);
                // 限制复制状态文本长度，防止遮挡左侧记录数信息
                String previewText = selected.getTextPreview();
                String truncatedText = this.truncateText(previewText, 30); // 限制为30个字符
                copyStatusLabel.setText("已复制到剪贴板: " + truncatedText);
                // 将该项添加到历史记录的最前面
                history.addItem(selected);
                this.refreshList();
                // 复制完成后隐藏窗口
//                this.setVisible(false);
                break;
            case IMAGE:
                try {
                    Image image = (Image) selected.getContent();
                    TransferableImage transferableImage = new TransferableImage(image);
                    java.awt.datatransfer.Clipboard clipboardImage =
                            Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboardImage.setContents(transferableImage, null);
                    copyStatusLabel.setText("图片已复制到剪贴板");
                    // 将该项添加到历史记录的最前面
                    history.addItem(selected);
                    this.refreshList();
                    // 复制完成后隐藏窗口
                    // TODO: zhanshuchan 12/5/25 功能还不全
//                    this.setVisible(false);
                } catch (Exception e) {
                    copyStatusLabel.setText("复制图片失败: " + e.getMessage());
                    e.printStackTrace();
                }
                break;
        }
    }
    
    /**
     * 图片传输类，用于将图片放到剪贴板中
     */
    private static class TransferableImage implements Transferable {
        private final Image image;
        
        public TransferableImage(Image image) {
            this.image = image;
        }
        
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }
        
        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(DataFlavor.imageFlavor);
        }
        
        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (flavor.equals(DataFlavor.imageFlavor)) {
                return image;
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }
    }
    
    /**
     * 更新状态栏信息
     */
    private void updateStatus() {
        statusLabel.setText("共 " + listModel.getSize() + " 条记录");
    }
    
    /**
     * 自定义列表项渲染器
     */
    private static class ClipboardItemRenderer extends JPanel implements ListCellRenderer<ClipboardItem> {
        private final JLabel iconLabel;
        private final JLabel contentLabel;
        private final JLabel timeLabel;
        private Color selectionBackgroundColor;
        private Color defaultBackgroundColor;
        private boolean isSelected; // 跟踪当前项是否被选中
        
        public ClipboardItemRenderer() {
            setLayout(new BorderLayout(5, 5));
            setBorder(new EmptyBorder(5, 10, 5, 10));
            
            iconLabel = new JLabel();
            iconLabel.setPreferredSize(new Dimension(35, 20));
            iconLabel.setHorizontalAlignment(JLabel.CENTER);
            iconLabel.setFont(iconLabel.getFont().deriveFont(9f));
            
            contentLabel = new JLabel();
            contentLabel.setVerticalAlignment(JLabel.TOP);
            
            timeLabel = new JLabel();
            timeLabel.setHorizontalAlignment(JLabel.RIGHT);
            timeLabel.setFont(timeLabel.getFont().deriveFont(10f));
            timeLabel.setForeground(Color.GRAY);
            
            JPanel textPanel = new JPanel(new BorderLayout());
            textPanel.add(contentLabel, BorderLayout.CENTER);
            textPanel.add(timeLabel, BorderLayout.SOUTH);
            
            add(iconLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            
            // 添加鼠标监听器实现悬浮效果
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!ClipboardItemRenderer.this.isSelected) {
                        setBackground(selectionBackgroundColor != null ? 
                                   selectionBackgroundColor.brighter() : 
                                   getBackground().brighter());
                    }
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    if (!ClipboardItemRenderer.this.isSelected) {
                        setBackground(defaultBackgroundColor != null ? 
                                   defaultBackgroundColor : 
                                   UIManager.getColor("List.background"));
                    }
                }
            });
        }
        
        @Override
        public Component getListCellRendererComponent(
                JList<? extends ClipboardItem> list,
                ClipboardItem value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            
            // 保存是否选中状态
            this.isSelected = isSelected;
            
            // 保存背景色引用用于鼠标悬浮效果
            if (defaultBackgroundColor == null) {
                defaultBackgroundColor = list.getBackground();
                selectionBackgroundColor = list.getSelectionBackground();
            }
            
            // 清除之前可能添加的组件
            Component[] components = getComponents();
            for (Component component : components) {
                if (component != iconLabel && component != contentLabel.getParent() && component != timeLabel.getParent()) {
                    remove(component);
                }
            }
            
            // 重置contentLabel的状态，防止组件复用导致的问题
            contentLabel.setIcon(null);
            contentLabel.setText("");
            
            // 设置选中状态样式
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                // 保持内容标签的前景色为黑色，避免在选中状态下文本不可见
                contentLabel.setForeground(Color.BLACK);
            } else {
                setBackground(list.getBackground());
                contentLabel.setForeground(list.getForeground());
            }
            
            // 根据内容类型设置图标和文本
            switch (value.getContentType()) {
                case TEXT:
                    iconLabel.setText("Text");
                    iconLabel.setForeground(Color.BLUE);
                    iconLabel.setToolTipText("文本类型");
                    contentLabel.setText(value.getTextPreview());
                    break;
                case URL:
                    iconLabel.setText("Link");
                    iconLabel.setForeground(Color.GREEN);
                    iconLabel.setToolTipText("链接类型");
                    contentLabel.setText("<html><a href='#'>" + value.getTextPreview() + "</a></html>");
                    break;
                case IMAGE:
                    iconLabel.setText("Image");
                    iconLabel.setForeground(Color.ORANGE);
                    iconLabel.setToolTipText("图片类型");
                    displayImagePreview(value);
                    break;
            }
            
            // 设置时间显示
            timeLabel.setText(value.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            this.revalidate();
            this.repaint();
            
            return this;
        }
        
        /**
         * 显示图片预览
         * @param item ClipboardItem对象
         */
        private void displayImagePreview(ClipboardItem item) {
            Object content = item.getContent();
            if (content instanceof java.awt.Image) {
                java.awt.Image image = (java.awt.Image) content;
                
                // 创建缩略图
                int maxWidth = 100;
                int maxHeight = 80;
                
                int imgWidth = image.getWidth(null);
                int imgHeight = image.getHeight(null);
                
                // 处理图片尺寸获取失败的情况
                if (imgWidth <= 0 || imgHeight <= 0) {
                    contentLabel.setText("[图片]");
                    return;
                }
                
                // 计算缩放比例
                double scale = Math.min((double) maxWidth / imgWidth, (double) maxHeight / imgHeight);
                scale = Math.min(scale, 1.0); // 不放大图片
                
                int scaledWidth = (int) (imgWidth * scale);
                int scaledHeight = (int) (imgHeight * scale);
                
                // 缩放图片
                java.awt.Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, java.awt.Image.SCALE_SMOOTH);
                
                // 创建图标并设置到标签上
                ImageIcon imageIcon = new ImageIcon(scaledImage);
                contentLabel.setIcon(imageIcon);
                contentLabel.setText(""); // 清除文本
            } else {
                contentLabel.setIcon(null);
                contentLabel.setText(item.getTextPreview());
            }
        }
    }
    
    @Override
    public void onClipboardUpdated() {
        // 在事件调度线程中执行UI更新
        SwingUtilities.invokeLater(() -> {
            // 只有在搜索框为空时才刷新整个列表，避免干扰用户的搜索结果
            String searchText = searchField.getText();
            if (searchText.trim().isEmpty()) {
                this.refreshList();
            } else {
                // 如果有搜索关键词，只更新状态栏
                this.updateStatus();
            }
        });
    }
    
    /**
     * 截断过长的文本，防止遮挡左侧状态信息
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    public static void main(String[] args) {
        System.out.println("启动剪贴板管理器...");
        SwingUtilities.invokeLater(() -> {
            try {
                // 修复Look and Feel设置
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // 使用默认外观
                System.err.println("设置 Look and Feel 失败: " + e.getMessage());
            }
            
            // 创建剪贴板管理器实例但不立即显示
            System.out.println("创建 ClipboardManager 实例...");
            ClipboardManager manager = new ClipboardManager();
            manager.setVisible(true);
            System.out.println("ClipboardManager 实例创建完成");
            // 窗口默认隐藏，通过系统托盘或其它方式触发显示
        });
        
        // 添加关闭钩子以确保在程序退出时注销全局键盘钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                GlobalScreen.unregisterNativeHook();
                System.out.println("全局热键监听器已注销");
            } catch (Exception e) {
                System.err.println("注销全局热键监听器时出错: " + e.getMessage());
            }
        }));
    }
}