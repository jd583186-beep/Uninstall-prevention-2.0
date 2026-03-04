package com.uninstallprevention.ui

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.uninstallprevention.R
import com.uninstallprevention.data.AppInfo
import java.util.concurrent.TimeUnit

class AppListAdapter(
    private val onProtectToggled: (AppInfo, Boolean) -> Unit,
    private val onCancelCountdown: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppInfo>()

    fun submitList(newList: List<AppInfo>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun updateProtection(packageName: String, isProtected: Boolean) {
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            items[index].isProtected = isProtected
            notifyItemChanged(index)
        }
    }

    fun updateCountdown(packageName: String, remainingMs: Long) {
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            items[index].countdownRemainingMs = remainingMs
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvPackage: TextView = itemView.findViewById(R.id.tvPackageName)
        private val switchProtect: Switch = itemView.findViewById(R.id.switchProtect)
        private val tvCountdown: TextView = itemView.findViewById(R.id.tvCountdown)
        private val btnCancelCountdown: Button = itemView.findViewById(R.id.btnCancelCountdown)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(appInfo: AppInfo) {
            ivIcon.setImageDrawable(appInfo.icon)
            tvName.text = appInfo.appName
            tvPackage.text = appInfo.packageName

            // Set switch without triggering listener
            switchProtect.setOnCheckedChangeListener(null)
            switchProtect.isChecked = appInfo.isProtected || appInfo.hasActiveCountdown

            when {
                appInfo.hasActiveCountdown -> {
                    // Countdown in progress
                    tvStatus.text = "⏳ Countdown to unprotect"
                    tvStatus.visibility = View.VISIBLE
                    tvCountdown.visibility = View.VISIBLE
                    tvCountdown.text = formatTime(appInfo.countdownRemainingMs)
                    btnCancelCountdown.visibility = View.VISIBLE
                    switchProtect.isEnabled = false
                }
                appInfo.isProtected -> {
                    tvStatus.text = "🔒 Protected"
                    tvStatus.visibility = View.VISIBLE
                    tvCountdown.visibility = View.GONE
                    btnCancelCountdown.visibility = View.GONE
                    switchProtect.isEnabled = true
                }
                else -> {
                    tvStatus.text = ""
                    tvStatus.visibility = View.GONE
                    tvCountdown.visibility = View.GONE
                    btnCancelCountdown.visibility = View.GONE
                    switchProtect.isEnabled = true
                }
            }

            switchProtect.setOnCheckedChangeListener { _, isChecked ->
                onProtectToggled(appInfo, isChecked)
            }

            btnCancelCountdown.setOnClickListener {
                onCancelCountdown(appInfo)
            }
        }

        private fun formatTime(millis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
            return String.format("%02d:%02d:%02d remaining", hours, minutes, seconds)
        }
    }
}
