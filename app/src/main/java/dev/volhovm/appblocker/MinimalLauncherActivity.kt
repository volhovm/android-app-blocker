package dev.volhovm.appblocker

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

/** The home screen in dumb mode: a clock and the apps on the allowlist. */
class MinimalLauncherActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "h:mm"
            textSize = 64f
            setTextColor(Color.WHITE)
            setPadding(0, pad, 0, pad)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
            addView(clock)
            addView(ScrollView(this@MinimalLauncherActivity).apply { addView(list) })
        }
        setContentView(root)
        // A home screen has nothing to go back to.
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val pm = packageManager
        val pad = (12 * resources.displayMetrics.density).toInt()
        Apps.allowlist(this)
            .mapNotNull { pkg ->
                val launch = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() to launch
            }
            .sortedBy { it.first.lowercase() }
            .forEach { (label, launch) ->
                list.addView(TextView(this).apply {
                    text = label
                    textSize = 28f
                    setTextColor(Color.WHITE)
                    setPadding(0, pad, 0, pad)
                    setOnClickListener { startActivity(launch) }
                })
            }
    }
}
