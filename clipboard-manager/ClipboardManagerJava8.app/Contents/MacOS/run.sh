#!/bin/bash

# 获取应用程序的根目录
APP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 获取Java运行时路径
JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || echo "/Library/Java/Home")

# 如果Java路径不存在，尝试常见路径
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    if [ -x "/Library/Java/JavaVirtualMachines/jdk1.8.0_XXX.jdk/Contents/Home/bin/java" ]; then
        JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_XXX.jdk/Contents/Home"
    elif [ -x "/usr/bin/java" ]; then
        JAVA_HOME="/usr"
    else
        # 尝试直接使用java命令
        if command -v java >/dev/null 2>&1; then
            java -cp "$APP_ROOT/Resources/ClipboardManager.jar:$APP_ROOT/Resources/jnativehook-2.2.2.jar:." com.zhansc.clipboard.ClipboardManager
            exit $?
        else
            # 弹出错误对话框
            osascript -e 'tell app "System Events" to display dialog "Java运行时未找到！\n\n请安装Java 8或更高版本。" buttons {"确定"} default button 1 with icon caution with title "剪贴板管理器"'
            exit 1
        fi
    fi
fi

# 运行应用程序
"$JAVA_HOME/bin/java" -cp "$APP_ROOT/Resources/ClipboardManager.jar:$APP_ROOT/Resources/jnativehook-2.2.2.jar:." com.zhansc.clipboard.ClipboardManager