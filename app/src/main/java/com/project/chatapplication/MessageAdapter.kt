package com.project.chatapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class MessageAdapter(private val context: Context, private val messageList: ArrayList<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val ITEM_RECEIVE = 1
    private val ITEM_SENT = 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ITEM_RECEIVE) {
            val view: View = LayoutInflater.from(context).inflate(R.layout.received_message_item, parent, false)
            ReceiveViewHolder(view)
        } else {
            val view: View = LayoutInflater.from(context).inflate(R.layout.sent_message_item, parent, false)
            SentViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentMessage = messageList[position]

        if (holder is SentViewHolder) {
            if (currentMessage.messageType == "TEXT") {
                holder.sentMessage.visibility = View.VISIBLE
                holder.sentImageView.visibility = View.GONE
                holder.sentMessage.text = currentMessage.message
            } else {
                holder.sentMessage.visibility = View.GONE
                holder.sentImageView.visibility = View.VISIBLE
                Glide.with(context).load(currentMessage.imageUrl).into(holder.sentImageView)
            }
        } else if (holder is ReceiveViewHolder) {
            if (currentMessage.messageType == "TEXT") {
                holder.receivedMessage.visibility = View.VISIBLE
                holder.receivedImageView.visibility = View.GONE
                holder.receivedMessage.text = currentMessage.message
            } else {
                holder.receivedMessage.visibility = View.GONE
                holder.receivedImageView.visibility = View.VISIBLE
                Glide.with(context).load(currentMessage.imageUrl).into(holder.receivedImageView)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val currentMessage = messageList[position]
        return if (FirebaseAuth.getInstance().currentUser?.uid == currentMessage.senderId) {
            ITEM_SENT
        } else {
            ITEM_RECEIVE
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    // ViewHolder for sent messages
    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sentMessage: TextView = itemView.findViewById(R.id.sent_message_text)
        val sentImageView: ImageView = itemView.findViewById(R.id.sent_image_view)
    }

    // ViewHolder for received messages
    class ReceiveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receivedMessage: TextView = itemView.findViewById(R.id.received_message_text)
        val receivedImageView: ImageView = itemView.findViewById(R.id.received_image_view)
    }
}