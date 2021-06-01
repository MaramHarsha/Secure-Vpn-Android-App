package com.technoupdate.securevpn.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import com.technoupdate.securevpn.GlobalApp
import com.technoupdate.securevpn.R
import com.technoupdate.securevpn.model.VPNGateConnection
import com.technoupdate.securevpn.provider.BaseProvider
import com.technoupdate.securevpn.utils.DataUtil
import com.technoupdate.securevpn.utils.TotalTraffic
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.*
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError
import de.blinkt.openvpn.core.OpenVPNService.LocalBinder
import de.blinkt.openvpn.core.VpnStatus.ConnectionStatus
import kotlinx.android.synthetic.main.activity_details.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class DetailsActivity : AppCompatActivity() {

    companion object {
        const val TYPE_FROM_NOTIFY = 1001
        const val TYPE_NORMAL = 1000
        const val TYPE_START = "vn.ulimit.vpngate.TYPE_START"
        const val START_VPN_PROFILE = 70
    }

    private var dataUtil: DataUtil? = null
    private var mVPNService: OpenVPNService? = null
    private var mVpnGateConnection: VPNGateConnection? = null
    private var vpnProfile: VpnProfile? = null
    private var brStatus: BroadcastReceiver? = null
    private var isConnecting = false
    private var isAuthFailed = false
    lateinit var mAdView : AdView
    private lateinit var mInterstitialAd: InterstitialAd
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) { // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocalBinder
            mVPNService = binder.service
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mVPNService = null
        }
    }

    private fun checkConnectionData() {
        if (mVpnGateConnection == null) { //Start main
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataUtil = GlobalApp.getInstance().dataUtil
        if (intent.getIntExtra(TYPE_START, TYPE_NORMAL) == TYPE_FROM_NOTIFY) {
            mVpnGateConnection = dataUtil!!.lastVPNConnection
            loadVpnProfile()
        } else {
            mVpnGateConnection =
                intent.getParcelableExtra(BaseProvider.PASS_DETAIL_VPN_CONNECTION)
        }
        checkConnectionData()
        setContentView(R.layout.activity_details)
        registerBroadCast()
        bindData()
        btn_connect.setOnClickListener {
            connectVpn()
        }
        btn_back.setOnClickListener {
            onBackPressed()
            showAd()
        }
        loadAd()
    }

    private fun showAd() {
        if (mInterstitialAd.isLoaded) {
            mInterstitialAd.show()
        } else {
            Log.d("TAG", "The interstitial wasn't loaded yet.")
        }
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

    private fun connectVpn() {
        if (!isConnecting) {
            val status = checkStatus()
            if (status) {
                stopVpn()
                isConnecting = false
                txt_status.setText(R.string.disconnecting)
            } else {
                prepareVpn()
                isConnecting = true
                dataUtil!!.lastVPNConnection = mVpnGateConnection
                txt_status.text = getString(R.string.connecting)
                sendConnectVPN()
            }
        } else {
            stopVpn()
            isConnecting = false
            txt_status.text = getString(R.string.canceled)
        }
    }

    private fun sendConnectVPN() {
        val intent = Intent(BaseProvider.ACTION.ACTION_CONNECT_VPN)
        sendBroadcast(intent)
    }

    private fun prepareVpn() {
        if (loadVpnProfile()) {
            startVpn()
        } else {
            Toast.makeText(this, getString(R.string.error_load_profile), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadVpnProfile(): Boolean {
        val data: ByteArray
        val useUDP = dataUtil!!.getBooleanSetting(DataUtil.LAST_CONNECT_USE_UDP, false)
        data = if (useUDP) {
            mVpnGateConnection!!.openVpnConfigDataUdp.toByteArray()
        } else {
            mVpnGateConnection!!.openVpnConfigData.toByteArray()
        }
        dataUtil!!.setBooleanSetting(DataUtil.LAST_CONNECT_USE_UDP, useUDP)
        val cp = ConfigParser()
        val isr =
            InputStreamReader(ByteArrayInputStream(data))
        try {
            cp.parseConfig(isr)
            vpnProfile = cp.convertProfile()
            vpnProfile!!.mName = mVpnGateConnection!!.getName(useUDP)
            vpnProfile!!.mOverrideDNS = true
            vpnProfile!!.mDNS1 = dataUtil!!.getStringSetting(DataUtil.CUSTOM_DNS_IP_1, "8.8.8.8")
            val dns2 = dataUtil!!.getStringSetting(DataUtil.CUSTOM_DNS_IP_2, null)
            if (dns2 != null) {
                vpnProfile!!.mDNS2 = dns2
            }
            ProfileManager.setTemporaryProfile(vpnProfile)
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } catch (e: ConfigParseError) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun checkStatus(): Boolean {
        try {
            return VpnStatus.isVPNActive()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isCurrent(): Boolean {
        val vpnGateConnection = dataUtil!!.lastVPNConnection
        return vpnGateConnection != null && vpnGateConnection.name == mVpnGateConnection!!.name
    }

    @SuppressLint("SetTextI18n")
    private fun bindData() {
        if (mVpnGateConnection != null) {
            try {
                Glide.with(this)
                    .load(GlobalApp.getInstance().dataUtil.baseUrl.toString() + "/images/flags/" + mVpnGateConnection!!.countryShort + ".png")
                    .into(img_flag)
                txt_country.text = mVpnGateConnection!!.countryLong
                txt_ip.text = mVpnGateConnection!!.ip
                txt_uptime.text = mVpnGateConnection!!.getCalculateUpTime(applicationContext)
                txt_speed.text = "${mVpnGateConnection!!.calculateSpeed} Mbps"
                txt_ping.text = "${mVpnGateConnection!!.pingAsString} ms"
                txt_session.text = mVpnGateConnection!!.numVpnSessionAsString
                txt_owner.text = mVpnGateConnection!!.operator
                if (isCurrent() && checkStatus()) {
                    btn_connect.text = resources.getString(R.string.disconnect)
                    var message = VpnStatus.getLastCleanLogMessage(this)
                    if(message.toLowerCase().contains("connected")){
                        message = "Connected: Success"
                    }
                    txt_status.text = message
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun registerBroadCast() {
        brStatus = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                receiveStatus(intent)
            }
        }
        registerReceiver(brStatus, IntentFilter(OpenVPNService.VPN_STATUS))
    }

    private fun receiveStatus(intent: Intent) {
        if (!isFinishing) {
            var message = VpnStatus.getLastCleanLogMessage(this)
            if(message.toLowerCase().contains("connected")){
                message = "Connected: Success"
            }
            txt_status.text = message
            changeServerStatus(ConnectionStatus.valueOf(intent.getStringExtra("status")))
            if ("NOPROCESS" == intent.getStringExtra("detailstatus")) {
                try {
                    TimeUnit.SECONDS.sleep(1)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun changeServerStatus(status: ConnectionStatus) {
        try {
            dataUtil!!.setBooleanSetting(DataUtil.USER_ALLOWED_VPN, true)
            when (status) {
                ConnectionStatus.LEVEL_CONNECTED -> {
                    btn_connect.text = getString(R.string.disconnect)
                    isConnecting = false
                    isAuthFailed = false
                    showAd()
                }
                ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> dataUtil!!.setBooleanSetting(
                    DataUtil.USER_ALLOWED_VPN,
                    false
                )
                ConnectionStatus.LEVEL_NOTCONNECTED -> if (!isConnecting && !isAuthFailed) {
                    btn_connect.setText(R.string.connect_to_this_server)
                    txt_status.setText(R.string.disconnected)
                    showAd()
                }
                ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    isAuthFailed = true
                    btn_connect.text = getString(R.string.retry_connect)
                    txt_status.text = resources.getString(R.string.vpn_auth_failure)
                    isConnecting = false
                }
                else -> {}
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() { //prepareStopVPN();
        ProfileManager.setConntectedVpnProfileDisconnected(this)
        if (mVPNService != null && mVPNService!!.management != null) {
            mVPNService!!.management.stopVPN(false)
        }
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            VpnStatus.updateStateString(
                "USER_VPN_PERMISSION", "", R.string.state_user_vpn_permission,
                ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT
            )
            // Start the query
            try {
                startActivityForResult(
                    intent,
                    START_VPN_PROFILE
                )
            } catch (ane: ActivityNotFoundException) { // Shame on you Sony! At least one user reported that
                VpnStatus.logError(R.string.no_vpn_support_image)
            }
        } else {
            onActivityResult(
                START_VPN_PROFILE,
                Activity.RESULT_OK,
                null
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == START_VPN_PROFILE) {
                    VPNLaunchHelper.startOpenVpn(vpnProfile, baseContext)
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(brStatus)
    }

    override fun onPause() {
        try {
            super.onPause()
            TotalTraffic.saveTotal()
            unbindService(mConnection)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Handler().postDelayed({
                val intent = Intent(this, OpenVPNService::class.java)
                intent.action = OpenVPNService.START_SERVICE
                bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
            }, 300)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}
