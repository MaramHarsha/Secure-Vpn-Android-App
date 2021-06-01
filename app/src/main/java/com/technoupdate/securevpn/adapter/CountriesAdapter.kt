package com.technoupdate.securevpn.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.technoupdate.securevpn.GlobalApp
import com.technoupdate.securevpn.R
import com.technoupdate.securevpn.interfaces.NavItemClickListener
import com.technoupdate.securevpn.model.VPNGateConnectionList

class CountriesAdapter(context: Context) : RecyclerView.Adapter<CountriesAdapter.MyViewHolder>() {

    private val mContext: Context
    private val listener: NavItemClickListener
    private var serverLists: VPNGateConnectionList? = null
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyViewHolder {
        val view: View = LayoutInflater.from(mContext)
            .inflate(R.layout.server_list_view, parent, false)
        return MyViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(
        holder: MyViewHolder,
        position: Int
    ) {
        holder.serverCountry.text = serverLists?.get(position)?.countryLong
        holder.speed.text = "${serverLists?.get(position)?.calculateSpeed} Mbps"
        holder.ping.text = "${serverLists?.get(position)?.pingAsString} ms"
        holder.ip.text = serverLists?.get(position)?.ip
        Glide.with(mContext)
            .load(GlobalApp.getInstance().dataUtil.baseUrl.toString() + "/images/flags/" + serverLists?.get(position)?.countryShort + ".png")
            .into(holder.serverIcon)
        holder.serverItemLayout.setOnClickListener { listener.clickedItem(position) }
        holder.serverItemLayout.setOnLongClickListener {
            listener.onLongclickedItem(position)
            return@setOnLongClickListener true
        }
    }

    override fun getItemCount(): Int {
        return serverLists?.size()!!
    }

    inner class MyViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        var serverItemLayout: ConstraintLayout = itemView.findViewById(R.id.serverItemLayout)
        var serverIcon: ImageView = itemView.findViewById(R.id.iconImg)
        var serverCountry: TextView = itemView.findViewById(R.id.countryTv)
        var speed: TextView = itemView.findViewById(R.id.tvSpeed)
        var ping: TextView = itemView.findViewById(R.id.tvPing)
        var ip: TextView = itemView.findViewById(R.id.ipTv)

    }

    init {
        serverLists = VPNGateConnectionList()
        mContext = context
        listener = context as NavItemClickListener
    }

    fun initialize(vpnGateConnectionList: VPNGateConnectionList?) {
        try {
            serverLists?.clear()
            if (vpnGateConnectionList != null) {
                serverLists?.addAll(vpnGateConnectionList)
            }
            notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}