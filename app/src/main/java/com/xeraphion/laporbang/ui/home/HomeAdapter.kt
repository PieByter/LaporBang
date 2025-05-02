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
            tvUser.text = "Oleh ${post.username}"
//            tvCoordinates.text = "Koordinat : ${post.location?.lat}, ${post.location?.lng}"
            tvHolesCount.text = "Jumlah Lubang : ${post.holesCount} lubang"
            tvSeverity.text = "Keparahan Lubang : ${post.severity}"
            val dateText = if (!post.updatedAt.isNullOrEmpty() && post.updatedAt != post.createdAt) {
                "Diperbarui: ${formatDate(post.updatedAt)}"
            } else {
                "Dibuat: ${formatDate(post.createdAt)}"
            }
            tvTimes.text = dateText
//            tvDiameter.text = "Diameter Lubang : ${post.diameter} mm"
//            tvDepth.text = "Kedalaman Lubang : ${post.depth} mm"

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
