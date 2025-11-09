package com.project.chatapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class NotificationListAdapter(
    private val context: Context,
    private val list: List<NotificationListActivity.PendingRequest>,
    private val onItemClick: (NotificationListActivity.PendingRequest) -> Unit
) : RecyclerView.Adapter<NotificationListAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_notification_row, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.name.text = item.sender.name
        val profileRef = if (!item.sender.profileImageUrl.isNullOrEmpty() && item.sender.profileImageUrl!!.startsWith("/")) java.io.File(item.sender.profileImageUrl!!) else item.sender.profileImageUrl
        Glide.with(context).load(profileRef).placeholder(R.drawable.default_avatar).into(holder.image)
        holder.root.setOnClickListener { onItemClick(item) }
        holder.badge.visibility = if (item.request != null) View.VISIBLE else View.GONE
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v
        val image: CircleImageView = v.findViewById(R.id.senderImage)
        val name: TextView = v.findViewById(R.id.senderName)
        val badge: TextView = v.findViewById(R.id.requestBadge)
    }
}
