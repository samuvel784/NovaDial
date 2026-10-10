package com.example.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import kotlinx.coroutines.*

class FloatingCallWidgetService : Service() {

    companion object {
        var isRunning = false
            private set

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            try {
                val intent = Intent(context, FloatingCallWidgetService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingCallWidgetService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var collapsedView: View? = null
    private var expandedView: View? = null
    private var collapsedParams: WindowManager.LayoutParams? = null
    private var expandedParams: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createFloatingViews()
        observeCallState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        timerJob?.cancel()
        serviceScope.cancel()
        removeViews()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createFloatingViews() {
        val wm = windowManager ?: return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Collapsed Bubble View
        collapsedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        collapsedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF2563EB.toInt())
            setPadding(28, 28, 28, 28)
            elevation = 16f
            clipToOutline = true

            val callLabel = TextView(context).apply {
                text = "📞 In Call"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(callLabel)

            val timerTv = TextView(context).apply {
                tag = "timer_view"
                text = "00:00"
                setTextColor(0xFFE2E8F0.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
            }
            addView(timerTv)

            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = collapsedParams?.x ?: 0
                            initialY = collapsedParams?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (dx * dx + dy * dy > 25) {
                                isDragging = true
                            }
                            if (isDragging) {
                                collapsedParams?.x = initialX + dx
                                collapsedParams?.y = initialY + dy
                                try {
                                    wm.updateViewLayout(collapsedView, collapsedParams)
                                } catch (e: Exception) {}
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isDragging) {
                                expandWidget()
                            } else {
                                snapToEdge()
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        // 2. Expanded Control Panel View
        expandedParams = WindowManager.LayoutParams(
            750,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        expandedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E293B.toInt())
            setPadding(36, 36, 36, 36)
            elevation = 24f

            val header = TextView(context).apply {
                text = "NovaDial Active Call"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            addView(header)

            val btnLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                setPadding(0, 8, 0, 8)

                val muteBtn = Button(context).apply {
                    text = "Mute"
                    setBackgroundColor(0xFF334155.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setOnClickListener {
                        CallManager.getInstance(context).toggleMute()
                    }
                }
                addView(muteBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) })

                val speakerBtn = Button(context).apply {
                    text = "Speaker"
                    setBackgroundColor(0xFF334155.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setOnClickListener {
                        CallManager.getInstance(context).toggleSpeaker()
                    }
                }
                addView(speakerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) })
            }
            addView(btnLayout)

            val actionLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                setPadding(0, 8, 0, 8)

                val returnBtn = Button(context).apply {
                    text = "Open App"
                    setBackgroundColor(0xFF2563EB.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setOnClickListener {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_IN_CALL_MODE", true)
                        }
                        context.startActivity(intent)
                        collapseWidget()
                    }
                }
                addView(returnBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) })

                val endBtn = Button(context).apply {
                    text = "End Call"
                    setBackgroundColor(0xFFEF4444.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setOnClickListener {
                        CallManager.getInstance(context).endCall()
                        stop(context)
                    }
                }
                addView(endBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) })
            }
            addView(actionLayout)

            val hideBtn = TextView(context).apply {
                text = "Hide Widget"
                setTextColor(0xFF94A3B8.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
                setOnClickListener {
                    collapseWidget()
                }
            }
            addView(hideBtn)
        }

        try {
            wm.addView(collapsedView, collapsedParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun expandWidget() {
        val wm = windowManager ?: return
        if (isExpanded) return
        try {
            wm.removeView(collapsedView)
            wm.addView(expandedView, expandedParams)
            isExpanded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collapseWidget() {
        val wm = windowManager ?: return
        if (!isExpanded) return
        try {
            wm.removeView(expandedView)
            wm.addView(collapsedView, collapsedParams)
            isExpanded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snapToEdge() {
        val wm = windowManager ?: return
        val p = collapsedParams ?: return
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val viewWidth = collapsedView?.width ?: 200

        p.x = if (p.x + viewWidth / 2 < screenWidth / 2) {
            20
        } else {
            screenWidth - viewWidth - 20
        }
        try {
            wm.updateViewLayout(collapsedView, p)
        } catch (e: Exception) {}
    }

    private fun removeViews() {
        val wm = windowManager ?: return
        try {
            if (isExpanded) {
                wm.removeView(expandedView)
            } else {
                wm.removeView(collapsedView)
            }
        } catch (e: Exception) {}
        collapsedView = null
        expandedView = null
    }

    private fun observeCallState() {
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000)
                val session = CallManager.getInstance(applicationContext).activeSession.value
                if (session.state == CallSessionState.IDLE || session.state == CallSessionState.DISCONNECTED) {
                    stop(applicationContext)
                    break
                }
                val secs = session.durationSeconds
                val mins = secs / 60
                val remSecs = secs % 60
                val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, remSecs)

                val tv = collapsedView?.findViewWithTag<TextView>("timer_view")
                tv?.text = timeStr
            }
        }
    }
}
