package org.xiyu.fxxklocation

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ScrollView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 80)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "FxxkLocation"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            gravity = Gravity.CENTER
        })

        // Version
        layout.addView(TextView(this).apply {
            text = "v1.0.0"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 48)
        })

        // Credits
        val credits = listOf(
            "作者" to "Mai_xiyu",
            "贡献者" to "Osilvfe",
            "贡献者" to "Claude Opus 4.6",
        )
        for ((role, name) in credits) {
            layout.addView(TextView(this).apply {
                text = "$role：$name"
                textSize = 17f
                setTextColor(Color.parseColor("#333333"))
                setPadding(0, 16, 0, 8)
            })
        }

        // Acknowledgments
        layout.addView(TextView(this).apply {
            setPadding(0, 48, 0, 0)
            text = "鸣谢"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
        })
        layout.addView(TextView(this).apply {
            text = "感谢 Lerist 制作 FakeLocation\n" +
                    "感谢 52pojie.cn 提供的部分脱壳教程\n" +
                    "感谢 Layout Inspect 及作者提供脱壳"
            textSize = 15f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, 16, 0, 0)
            setLineSpacing(8f, 1f)
        })

        // Usage Guide
        layout.addView(TextView(this).apply {
            setPadding(0, 48, 0, 0)
            text = "使用方法"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
        })

        val guideItems = listOf(
            "① LSPosed/LSPatch 配置" to
                "• 安装模块后，在 LSPosed 管理器中启用本模块\n" +
                "• 勾选作用域：「系统框架」+「FakeLocation」\n" +
                "• 不需要勾选目标应用（如小步点等），模块会自动处理\n" +
                "• 修改作用域后需要重启设备生效",

            "② Root 管理器配置" to
                "• KernelSU/Magisk/APatch：授予「FakeLocation」超级用户权限\n" +
                "• FxxkLocation 本身不需要 Root 权限\n" +
                "• 模块会借助 FakeLocation 的 Root 自动配置 SELinux 策略",

            "③ FakeLocation 使用" to
                "• 打开 FakeLocation，选择模式 0（推荐）\n" +
                "• 设置目标位置或路线，点击「启动模拟」\n" +
                "• 路线模拟：添加路线后开启「循环」，点击启动\n" +
                "• 步频模拟：FL 会自动同步步频到目标应用",

            "④ 注意事项" to
                "• 每次更新模块后需要重启设备\n" +
                "• 如果 FakeLocation 提示服务连接失败，重启设备即可\n" +
                "• 本模块已内置 Pro 功能，无需登录\n" +
                "• 协议弹窗会自动跳过"
        )

        for ((title, content) in guideItems) {
            layout.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#2E7D32"))
                setPadding(0, 32, 0, 4)
            })
            layout.addView(TextView(this).apply {
                text = content
                textSize = 14f
                setTextColor(Color.parseColor("#555555"))
                setPadding(24, 4, 0, 0)
                setLineSpacing(6f, 1f)
            })
        }

        root.addView(layout)
        setContentView(root)
    }
}
