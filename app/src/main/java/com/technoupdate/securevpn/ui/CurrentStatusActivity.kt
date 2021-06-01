package com.technoupdate.securevpn.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import com.technoupdate.securevpn.GlobalApp
import com.technoupdate.securevpn.R
import com.technoupdate.securevpn.utils.DataUtil
import com.technoupdate.securevpn.utils.PropertiesService
import com.technoupdate.securevpn.utils.TotalTraffic
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.android.synthetic.main.activity_current_status.*

class CurrentStatusActivity : AppCompatActivity() {

    private var dataUtil: DataUtil? = null
    private var trafficReceiver: BroadcastReceiver? = null
    lateinit var mAdView : AdView
    private lateinit var mInterstitialAd: InterstitialAd

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_current_status)
        dataUtil = GlobalApp.getInstance().dataUtil
        bindData()
        registerBroadCast()
        btn_back.setOnClickListener {
            onBackPressed()
            showAd()
        }
        loadAd()
    }

    private fun loadAd() {
        //Banner Ad
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

        //Interstitial Ad
        mInterstitialAd = InterstitialAd(this)
        mInterstitialAd.adUnitId = getString(R.string.interstitial_ad_id)
        mInterstitialAd.loadAd(AdRequest.Builder().build())
        mInterstitialAd.adListener = object : AdListener() {
            override fun onAdClosed() {
                mInterstitialAd.loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun showAd(){
        if (mInterstitialAd.isLoaded) {
            mInterstitialAd.show()
        } else {
            Log.d("TAG", "The interstitial wasn't loaded yet.")
        }
    }

    private fun registerBroadCast() {
        try {
            trafficReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    receiveTraffic(intent)
                }
            }
            registerReceiver(trafficReceiver, IntentFilter(TotalTraffic.TRAFFIC_ACTION))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun checkStatus(): Boolean {
        try {
            return VpnStatus.isVPNActive()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun receiveTraffic(intent: Intent) {
        if (checkStatus()) {
            txt_download_session.text = intent.getStringExtra(TotalTraffic.DOWNLOAD_SESSION)
            txt_upload_session.text = intent.getStringExtra(TotalTraffic.UPLOAD_SESSION)
            txt_total_download.text = intent.getStringExtra(TotalTraffic.DOWNLOAD_ALL)
            txt_total_upload.text = intent.getStringExtra(TotalTraffic.UPLOAD_ALL)
        }
    }

    private fun bindData() {
        try {
            txt_total_upload.text = OpenVPNService.humanReadableByteCount(PropertiesService.getUploaded(), false)
            txt_total_download.text = OpenVPNService.humanReadableByteCount(PropertiesService.getDownloaded(), false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trafficReceiver)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}
