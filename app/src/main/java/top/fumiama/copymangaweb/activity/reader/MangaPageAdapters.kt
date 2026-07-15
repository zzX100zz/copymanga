package top.fumiama.copymangaweb.activity.reader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.view.ScaleImageView

class PagedMangaAdapter(
    private val itemCountProvider: () -> Int,
    private val bindImage: (ScaleImageView, Int) -> Unit,
) : RecyclerView.Adapter<PagedMangaAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_imgview, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.image.resetImageTransform()
        bindImage(holder.image, position)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        holder.image.setOnTapRegionListener(null)
        holder.image.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = itemCountProvider()

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ScaleImageView = itemView.findViewById(R.id.onei)
    }
}

class ContinuousMangaAdapter(
    private val itemCountProvider: () -> Int,
    private val bindImage: (ScaleImageView, Int) -> Unit,
) : RecyclerView.Adapter<ContinuousMangaAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_img_continuous, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.image.resetImageTransform()
        bindImage(holder.image, position)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        holder.image.setOnTapRegionListener(null)
        holder.image.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = itemCountProvider()

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ScaleImageView = itemView.findViewById(R.id.pageImage)
    }
}
