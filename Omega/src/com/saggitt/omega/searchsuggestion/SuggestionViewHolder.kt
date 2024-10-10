package com.saggitt.omega.searchsuggestion

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R

class SuggestionViewHolder(view: View):RecyclerView.ViewHolder(view) {
    val imageView: ImageView = view.findViewById(R.id.suggestionIcon)
    val titleView: TextView = view.findViewById(R.id.title)
    val urlView: TextView = view.findViewById(R.id.url)
    val insertSuggestion: ImageView = view.findViewById(R.id.complete_search)
}
