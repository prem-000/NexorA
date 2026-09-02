package com.fury.peerconnect.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fury.peerconnect.R
import com.fury.peerconnect.data.ChatMessage

class ChatAdapter(
    private val myNickName: String,
    private val onAttachmentClick: ((fileName: String, pathOrUri: String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = ArrayList<ChatMessage>()

    companion object {
        private const val TYPE_TEXT_ME = 1
        private const val TYPE_TEXT_OTHER = 2
        private const val TYPE_FILE_ME = 3
        private const val TYPE_FILE_OTHER = 4
        private const val TYPE_IMAGE_ME = 5
        private const val TYPE_IMAGE_OTHER = 6
    }

    fun clear() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun setMessages(history: List<ChatMessage>) {
        messages.clear()
        messages.addAll(history)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        val isMe = (msg.senderName == myNickName)
        val body = msg.messageBody

        val isFile = body.startsWith("[FILE]:") || body.startsWith("📄 Shared a file:")
        val cleanBody = when {
            body.startsWith("[FILE]:") -> body.removePrefix("[FILE]:")
            body.startsWith("📄 Shared a file: ") -> body.removePrefix("📄 Shared a file: ")
            else -> ""
        }
        val fileName = cleanBody.split("|")[0]
        val isImage = isFile && (fileName.endsWith(".jpg", ignoreCase = true) ||
                fileName.endsWith(".jpeg", ignoreCase = true) ||
                fileName.endsWith(".png", ignoreCase = true) ||
                fileName.endsWith(".webp", ignoreCase = true))

        return when {
            isImage -> if (isMe) TYPE_IMAGE_ME else TYPE_IMAGE_OTHER
            isFile -> if (isMe) TYPE_FILE_ME else TYPE_FILE_OTHER
            else -> if (isMe) TYPE_TEXT_ME else TYPE_TEXT_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT_ME -> TextViewHolder(inflater.inflate(R.layout.item_message_me, parent, false))
            TYPE_TEXT_OTHER -> TextViewHolder(inflater.inflate(R.layout.item_message_other, parent, false))
            TYPE_FILE_ME -> FileViewHolder(inflater.inflate(R.layout.item_message_file_me, parent, false), onAttachmentClick)
            TYPE_FILE_OTHER -> FileViewHolder(inflater.inflate(R.layout.item_message_file_other, parent, false), onAttachmentClick)
            TYPE_IMAGE_ME -> ImageViewHolder(inflater.inflate(R.layout.item_message_image_me, parent, false), onAttachmentClick)
            TYPE_IMAGE_OTHER -> ImageViewHolder(inflater.inflate(R.layout.item_message_image_other, parent, false), onAttachmentClick)
            else -> TextViewHolder(inflater.inflate(R.layout.item_message_me, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is TextViewHolder -> holder.bind(msg)
            is FileViewHolder -> holder.bind(msg)
            is ImageViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messages.size

    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textSender: TextView = itemView.findViewById(R.id.textSender)

        fun bind(msg: ChatMessage) {
            textMessage.text = msg.messageBody
            textSender.text = msg.senderName
        }
    }

    class FileViewHolder(
        itemView: View,
        private val onAttachmentClick: ((String, String) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val textFileName: TextView = itemView.findViewById(R.id.textFileName)
        private val textSender: TextView = itemView.findViewById(R.id.textSender)

        fun bind(msg: ChatMessage) {
            val body = msg.messageBody
            val cleanBody = when {
                body.startsWith("[FILE]:") -> body.removePrefix("[FILE]:")
                body.startsWith("📄 Shared a file: ") -> body.removePrefix("📄 Shared a file: ")
                else -> body
            }
            val parts = cleanBody.split("|")
            val fileName = parts[0]
            val pathOrUri = if (parts.size > 1) parts[1] else null

            textFileName.text = fileName
            textSender.text = msg.senderName

            itemView.setOnClickListener {
                onAttachmentClick?.invoke(fileName, pathOrUri ?: "")
            }
        }
    }

    class ImageViewHolder(
        itemView: View,
        private val onAttachmentClick: ((String, String) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val textFileName: TextView = itemView.findViewById(R.id.textFileName)
        private val textSender: TextView = itemView.findViewById(R.id.textSender)
        private val imagePreview: ImageView = itemView.findViewById(R.id.imagePreview)

        fun bind(msg: ChatMessage) {
            val body = msg.messageBody
            val cleanBody = when {
                body.startsWith("[FILE]:") -> body.removePrefix("[FILE]:")
                body.startsWith("📄 Shared a file: ") -> body.removePrefix("📄 Shared a file: ")
                else -> body
            }
            val parts = cleanBody.split("|")
            val fileName = parts[0]
            val pathOrUri = if (parts.size > 1) parts[1] else null

            textFileName.text = fileName
            textSender.text = msg.senderName

            if (!pathOrUri.isNullOrEmpty()) {
                val file = java.io.File(pathOrUri)
                if (file.exists()) {
                    imagePreview.setImageURI(android.net.Uri.fromFile(file))
                } else if (pathOrUri.startsWith("content://")) {
                    imagePreview.setImageURI(android.net.Uri.parse(pathOrUri))
                } else {
                    imagePreview.setImageResource(R.drawable.ic_image)
                }
            } else {
                imagePreview.setImageResource(R.drawable.ic_image)
            }

            itemView.setOnClickListener {
                onAttachmentClick?.invoke(fileName, pathOrUri ?: "")
            }
        }
    }
}