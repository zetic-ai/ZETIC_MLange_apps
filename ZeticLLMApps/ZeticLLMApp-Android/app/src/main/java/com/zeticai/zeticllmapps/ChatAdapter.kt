package com.zeticai.zeticllmapps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    private val messages = mutableListOf<Message>()

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_USER -> R.layout.item_message_user
            else -> R.layout.item_message_ai
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun appendToLastMessage(token: String) {
        if (messages.isNotEmpty()) {
            messages.last().content.append(token)
            notifyItemChanged(messages.size - 1)
        }
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)

        fun bind(message: Message) {
            messageText.text = message.content.toString()
            timestampText.text = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))
        }
    }
}