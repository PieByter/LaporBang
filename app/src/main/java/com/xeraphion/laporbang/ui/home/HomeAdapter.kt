package com.xeraphion.laporbang.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.databinding.ItemReportBinding
import com.xeraphion.laporbang.helper.formatDate
import com.xeraphion.laporbang.response.ReportsResponseItem

class HomeAdapter(
    private val posts: List<ReportsResponseItem>,
    private val onItemClick: (ReportsResponseItem) -> Unit,
) : RecyclerView.Adapter<HomeAdapter.PostViewHolder>() {

    inner class PostViewHolder(val binding: ItemReportBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        with(holder.binding) {
            tvTitles.text = "${post.titles}"
            tvUser.text = "${post.username}"
            tvHolesCount.text = "Jumlah Lubang : ${post.holesCount} Lubang"
            tvSeverity.text = "Tingkat Keparahan : ${post.severity}"
            val isUpdated = !post.updatedAt.isNullOrEmpty() && post.updatedAt != post.createdAt
            tvTimes.text = if (isUpdated) {
                formatDate(post.updatedAt)
            } else {
                formatDate(post.createdAt)
            }
            ivTimes.setImageResource(if (isUpdated) R.drawable.ic_update_report else R.drawable.ic_upload_report)

            Glide.with(root.context)
                .load(post.imageUrl)
                .placeholder(R.drawable.ic_image)
                .into(ivPotholes)

            root.setOnClickListener {
                onItemClick(post)
            }
        }
    }

    override fun getItemCount() = posts.size
}
