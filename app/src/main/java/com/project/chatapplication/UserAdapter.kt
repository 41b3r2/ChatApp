package com.project.chatapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class UserAdapter(private val context: Context, private val userList: ArrayList<User>) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val filteredUserList = ArrayList<User>(userList)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.user_list_item, parent, false)
        return UserViewHolder(view)
    }    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val currentUser = filteredUserList[position]
        holder.nameTextView.text = currentUser.name
        holder.statusTextView.text = "Ready to chat"

        if (!currentUser.profileImageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(currentUser.profileImageUrl)
                .placeholder(R.drawable.default_avatar)
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.default_avatar)
        }

        // Click listener for the entire item
        holder.itemView.setOnClickListener {
            (context as? MainActivity)?.showUserProfileDialog(currentUser)
        }

        // Click listener for the chat icon
        holder.chatIconView.setOnClickListener {
            (context as? MainActivity)?.showUserProfileDialog(currentUser)
        }
    }

    override fun getItemCount(): Int {
        return filteredUserList.size
    }

    fun filter(query: String) {
        filteredUserList.clear()
        if (query.isEmpty()) {
            filteredUserList.addAll(userList)
        } else {            val searchQuery = query.lowercase()
            for (user in userList) {
                if (user.name?.lowercase()?.contains(searchQuery) == true) {
                    filteredUserList.add(user)
                }
            }
        }
        notifyDataSetChanged()
    }

    fun updateUsers() {
        filteredUserList.clear()
        filteredUserList.addAll(userList)
        notifyDataSetChanged()
    }    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        val profileImageView: CircleImageView = itemView.findViewById(R.id.profile_image_item)
        val chatIconView: ImageView = itemView.findViewById(R.id.chatIconView)
    }
}