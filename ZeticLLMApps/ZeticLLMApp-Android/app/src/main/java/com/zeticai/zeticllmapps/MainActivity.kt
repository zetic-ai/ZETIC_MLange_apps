package com.zeticai.zeticllmapps

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zeticai.mlange.core.model.llm.ZeticMLangeLLMModel

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private val model by lazy {
        ZeticMLangeLLMModel(this, "deepseek-r1-distill-qwen-1.5b-f16")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        adapter = ChatAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        sendButton.setOnClickListener {
            sendButton.setEnabled(false, R.drawable.ic_send)
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                adapter.addMessage(Message(StringBuilder(message), isFromUser = true))
                messageInput.text.clear()
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

                adapter.addMessage(Message(StringBuilder(), isFromUser = false))
                simulateStreamingResponse(message.trim())
            } else {
                sendButton.setEnabled(true, R.drawable.ic_send)
            }
        }

        sendButton.setEnabled(false, R.drawable.ic_send)
        messageInput.hint = "model loading.."
        messageInput.isEnabled = false

        Thread {
            model
            runOnUiThread {
                sendButton.setEnabled(true, R.drawable.ic_send)
                messageInput.hint = "Type a message"
                messageInput.isEnabled = true
            }
        }.start()
    }

    private fun simulateStreamingResponse(text: String) {
        Thread {
            model.run(text)
            while (true) {
                val token = model.waitForNextToken()
                if (token == "")
                    break

                runOnUiThread {
                    adapter.appendToLastMessage(token)
                    recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
            runOnUiThread {
                sendButton.setEnabled(true, R.drawable.ic_send)
            }
        }.start()
    }

    private fun ImageButton.setEnabled(enabled: Boolean, iconResId: Int) {
        isEnabled = enabled
        val originalIcon = ResourcesCompat.getDrawable(resources, iconResId, theme) ?: return
        val icon = if (enabled) originalIcon else convertDrawableToGrayScale(originalIcon)
        setImageDrawable(icon)
    }

    private fun convertDrawableToGrayScale(drawable: Drawable): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val res = drawable.mutate()
            res.colorFilter = BlendModeColorFilter(Color.GRAY, BlendMode.SRC_IN)
            return res
        }
        return drawable
    }
}