package com.technoupdate.securevpn.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.technoupdate.securevpn.GlobalApp
import com.technoupdate.securevpn.R
import com.technoupdate.securevpn.adapter.CountriesAdapter
import com.technoupdate.securevpn.interfaces.NavItemClickListener
import com.technoupdate.securevpn.model.VPNGateConnection
import com.technoupdate.securevpn.model.VPNGateConnectionList
import com.technoupdate.securevpn.provider.BaseProvider
import com.technoupdate.securevpn.utils.DataUtil
import com.technoupdate.securevpn.utils.TotalTraffic
import com.technoupdate.securevpn.worker.ServerUpdate
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.*
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError
import de.blinkt.openvpn.core.OpenVPNService.LocalBinder
import de.blinkt.openvpn.core.VpnStatus.ConnectionStatus
import kotlinx.android.synthetic.main.activity_content.*
import kotlinx.android.synthetic.main.motion_drawerlayout.*
import kotlinx.android.synthetic.main.motion_drawerlayout_menu.*
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), NavItemClickListener, CoroutineScope {

    companion object {
        const val TAG = "MainActivity"
    }

    private var serverListRVAdapter: CountriesAdapter? = null
    private var isInFront = false
    private var dataUtil: DataUtil? = null
    var vpnGateConnectionList: VPNGateConnectionList? = null
    private var isRetried = false
    private var isApiCallGoing = false
    private var isConnecting = false
    private var isAuthFailed = false

    private var job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private var mVPNService: OpenVPNService? = null
    private var mVpnGateConnection: VPNGateConnection? = null
    private var brStatus: BroadcastReceiver? = null
    private var trafficReceiver: BroadcastReceiver? = null
    private var vpnProfile: VpnProfile? = null
    private lateinit var mInterstitialAd: InterstitialAd
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocalBinder
            mVPNService = binder.service
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mVPNService = null
        }
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (Objects.requireNonNull(intent.action)) {
                BaseProvider.ACTION.ACTION_CHANGE_NETWORK_STATE -> if (isInFront) {
                    initState()
                }
                BaseProvider.ACTION.ACTION_CLEAR_CACHE -> vpnGateConnectionList = null
                BaseProvider.ACTION.ACTION_CONNECT_VPN -> if (dataUtil?.lastVPNConnection != null) {
                    try {
                        Toast.makeText(this@MainActivity, "Broadcast Received!", Toast.LENGTH_SHORT)
                            .show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.motion_drawerlayout)
        if (!GlobalApp.getInstance().dataUtil.isIntroShown) {
            val intent = Intent(this, IntroActivity::class.java)
            startActivity(intent)
            finish()
        }
        if (!GlobalApp.getInstance().dataUtil.isAcceptedPrivacyPolicy) {
            showDialogPrivacy()
        }
        Glide.with(this).load(R.drawable.ic_world_mao).into(mainImage)
        dataUtil = (application as GlobalApp).dataUtil
        //Init Broadcast Receiver
        val filter = IntentFilter()
        filter.addAction(BaseProvider.ACTION.ACTION_CHANGE_NETWORK_STATE)
        filter.addAction(BaseProvider.ACTION.ACTION_CLEAR_CACHE)
        filter.addAction(BaseProvider.ACTION.ACTION_CONNECT_VPN)
        registerReceiver(broadcastReceiver, filter)
        mVpnGateConnection = dataUtil!!.lastVPNConnection
        initRecyclerView()
        registerBroadCast()
        bindData()
        btnConnect.setOnClickListener {
            connectVpn()
        }
        usageTv.setOnClickListener {
            startActivity(Intent(this, CurrentStatusActivity::class.java))
        }
        connectionTv.setOnClickListener {
            launch {
                if (checkStatus()) {
                    val intent = Intent(this@MainActivity, DetailsActivity::class.java)
                    intent.putExtra(BaseProvider.PASS_DETAIL_VPN_CONNECTION, mVpnGateConnection)
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No Active VPN Connection Detected!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        exitTv.setOnClickListener {
            finish()
        }
        rateUsTv.setOnClickListener {
            rateUs()
        }
        shareAppTv.setOnClickListener {
            shareApp()
        }
        loadAd()
    }

    private fun showDialogPrivacy() {
        MaterialDialog(this).show {
            title(R.string.label_privacy_policy)
            icon(R.mipmap.ic_launcher)
            positiveButton(text = "Agree") {
                GlobalApp.getInstance().dataUtil.isAcceptedPrivacyPolicy = true
                dismiss()
            }
            cancelable(false)
            cancelOnTouchOutside(false)
            message(R.string.privacy_policy) {
                html()
            }
        }
    }

    private fun showAd() {
        if (mInterstitialAd.isLoaded) {
            mInterstitialAd.show()
        } else {
            Log.d("TAG", "The interstitial wasn't loaded yet.")
        }
    }

    private fun loadAd() {
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

    private fun shareApp() {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND;
        sendIntent.putExtra(
            Intent.EXTRA_TEXT,
            "Check out " +
                    resources.getString(R.string.app_name) +
                    " App at: https://play.google.com/store/apps/details?id=" +
                    packageName
        )
        sendIntent.type = "text/plain";
        startActivity(sendIntent);
    }

    private fun rateUs() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (anfe: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    private fun bindData() {
        totalUploadTv.text = intent.getStringExtra(TotalTraffic.UPLOAD_ALL)
        totalDownloadTv.text = intent.getStringExtra(TotalTraffic.DOWNLOAD_ALL)
        mVpnGateConnection?.let {
            Glide.with(this)
                .load(GlobalApp.getInstance().dataUtil.baseUrl.toString() + "/images/flags/" + it.countryShort + ".png")
                .into(img_flag)
        }
        launch {
            when {
                checkStatus() -> {
                    btnConnect.isActivated = true
                    txtStatus.text = String.format(
                        resources.getString(R.string.tap_to_disconnect),
                        getConnectionName()
                    )
                    btnConnect.text = getString(R.string.txt_disconnect)
                }
                mVpnGateConnection != null -> {
                    txtStatus.text = String.format(
                        resources.getString(R.string.tap_to_connect_last),
                        getConnectionName()
                    )
                    btnConnect.text = getString(R.string.txt_connect)
                }
                else -> {
                    btnConnect.isActivated = false
                    btnConnect.isEnabled = false
                    txtStatus.setText(R.string.no_last_vpn_server)
                    btnConnect.text = getString(R.string.txt_connect)
                }
            }
        }

    }

    private fun registerBroadCast() {
        try {
            brStatus = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    receiveStatus(intent)
                }
            }
            registerReceiver(brStatus, IntentFilter(OpenVPNService.VPN_STATUS))
            trafficReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    receiveTraffic(intent)
                }
            }
            registerReceiver(
                trafficReceiver,
                IntentFilter(TotalTraffic.TRAFFIC_ACTION)
            )
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun receiveTraffic(intent: Intent) {
        launch {
            val status = checkStatus()
            if (status) {
                totalUploadTv.text = intent.getStringExtra(TotalTraffic.UPLOAD_ALL)
                totalDownloadTv.text = intent.getStringExtra(TotalTraffic.DOWNLOAD_ALL)
                sessionUploadTv.text = intent.getStringExtra(TotalTraffic.UPLOAD_SESSION)
                sessionDownloadTv.text = intent.getStringExtra(TotalTraffic.DOWNLOAD_SESSION)
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun receiveStatus(intent: Intent) {
        try {
            var message = VpnStatus.getLastCleanLogMessage(this)
            if (message.toLowerCase().contains("connected")) {
                message = "Connected: Success"
            }
            txtStatus.text = message
            changeServerStatus(VpnStatus.ConnectionStatus.valueOf(intent.getStringExtra("status")))
            if ("NOPROCESS" == intent.getStringExtra("detailstatus")) {
                try {
                    TimeUnit.SECONDS.sleep(1)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun changeServerStatus(status: ConnectionStatus) {
        try {
            dataUtil!!.setBooleanSetting(DataUtil.USER_ALLOWED_VPN, true)
            when (status) {
                ConnectionStatus.LEVEL_CONNECTED -> {
                    btnConnect.isActivated = true
                    btnConnect.isEnabled = true
                    isConnecting = false
                    isAuthFailed = false
                    btnConnect.text = getString(R.string.txt_disconnect)
                    Glide.with(this)
                        .load(GlobalApp.getInstance().dataUtil.baseUrl.toString() + "/images/flags/" + mVpnGateConnection!!.countryShort + ".png")
                        .into(img_flag)
                    showAd()
                }
                ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> dataUtil!!.setBooleanSetting(
                    DataUtil.USER_ALLOWED_VPN,
                    false
                )
                ConnectionStatus.LEVEL_NOTCONNECTED -> if (!isConnecting && !isAuthFailed) {
                    btnConnect.isActivated = false
                    btnConnect.isEnabled = true
                    txtStatus.text = String.format(
                        getString(R.string.tap_to_connect_last),
                        getConnectionName()
                    )
                    btnConnect.text = getString(R.string.txt_connect)
                }
                ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    isAuthFailed = true
                    btnConnect.isActivated = false
                    btnConnect.isEnabled = true
                    isConnecting = false
                    txtStatus.text = resources.getString(R.string.vpn_auth_failure)
                    btnConnect.text = getString(R.string.txt_connect)
                    showAd()
                }
                else -> {
                    btnConnect.isEnabled = false
                    btnConnect.isActivated = false
                    btnConnect.text = getString(R.string.txt_loading)
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkStatus() = withContext(Dispatchers.IO) {
        try {
            return@withContext VpnStatus.isVPNActive()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun connectVpn() {
        btnConnect.isActivated = false
        btnConnect.isEnabled = false
        changeState()
        launch {
            delay(2000)
            if (mVpnGateConnection == null) {
                return@launch
            }
            if (!isConnecting) {
                val status = checkStatus()
                if (status) {
                    stopVpn()
                    isConnecting = false
                    btnConnect.isActivated = false
                    txtStatus.setText(R.string.disconnecting)
                } else {
                    prepareVpn()
                    btnConnect.isActivated = true
                    isConnecting = true
                    dataUtil!!.lastVPNConnection = mVpnGateConnection
                    txtStatus.text = getString(R.string.connecting)
                }
            } else {
                stopVpn()
                btnConnect.isActivated = false
                isConnecting = false
                txtStatus.text = getString(R.string.canceled)
            }
        }

    }

    private suspend fun prepareVpn() {
        withContext(Dispatchers.IO) {
            val profile = loadVpnProfile()
            if (profile) {
                startVpn()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.error_load_profile),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun startVpn() {
        withContext(Dispatchers.IO) {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) {
                VpnStatus.updateStateString(
                    "USER_VPN_PERMISSION", "",
                    R.string.state_user_vpn_permission,
                    ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT
                )
                try {
                    startActivityForResult(intent, DetailsActivity.START_VPN_PROFILE)
                } catch (ane: ActivityNotFoundException) { // Shame on you Sony! At least one user reported that
                    VpnStatus.logError(R.string.no_vpn_support_image)
                }
            } else {
                onActivityResult(DetailsActivity.START_VPN_PROFILE, Activity.RESULT_OK, null)
            }
        }
    }

    private suspend fun stopVpn() {
        withContext(Dispatchers.Default) {
            ProfileManager.setConntectedVpnProfileDisconnected(this@MainActivity)
            if (mVPNService != null && mVPNService!!.management != null)
                mVPNService!!.management.stopVPN(false)
        }
    }

    private suspend fun loadVpnProfile() =
        withContext(Dispatchers.IO) {
            val useUdp = dataUtil!!.getBooleanSetting(DataUtil.LAST_CONNECT_USE_UDP, false)
            val data: ByteArray
            data = if (useUdp) {
                mVpnGateConnection?.openVpnConfigDataUdp!!.toByteArray()
            } else {
                mVpnGateConnection!!.openVpnConfigData!!.toByteArray()
            }
            val cp = ConfigParser()
            val isr =
                InputStreamReader(ByteArrayInputStream(data))
            try {
                cp.parseConfig(isr)
                vpnProfile = cp.convertProfile()
                vpnProfile!!.mName = getConnectionName()
                vpnProfile!!.mOverrideDNS = true
                vpnProfile!!.mDNS1 =
                    dataUtil!!.getStringSetting(DataUtil.CUSTOM_DNS_IP_1, "8.8.8.8")
                val dns2 = dataUtil!!.getStringSetting(DataUtil.CUSTOM_DNS_IP_2, null)
                if (dns2 != null) {
                    vpnProfile!!.mDNS2 = dns2
                }
                ProfileManager.setTemporaryProfile(vpnProfile)
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext false
            } catch (e: ConfigParseError) {
                e.printStackTrace()
                return@withContext false
            }
            return@withContext true
        }

    private fun getConnectionName(): String? {
        val useUdp = dataUtil!!.getBooleanSetting(DataUtil.LAST_CONNECT_USE_UDP, false)
        return mVpnGateConnection!!.getName(useUdp)
    }

    private fun initRecyclerView() {
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(this)
        serverListRVAdapter = CountriesAdapter(this)
        recyclerView.adapter = serverListRVAdapter
    }

    private fun changeState() {
        val motionLayout = mLayout as MotionLayout
        if (motionLayout.progress > 0.5f) {
            motionLayout.transitionToStart()
        } else {
            motionLayout.transitionToEnd()
        }
    }

    fun changeDrawerState(view: View) {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            drawer.openDrawer(GravityCompat.START)
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        isInFront = false
        try {
            TotalTraffic.saveTotal()
            unbindService(mConnection)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (vpnGateConnectionList == null || vpnGateConnectionList!!.size() == 0) {
                initState()
            }
            isInFront = true
            val intent = Intent(this, OpenVPNService::class.java)
            intent.action = OpenVPNService.START_SERVICE
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun initState() {
        loadData()
    }

    private fun loadData() {
        if (DataUtil.isOnline(applicationContext)) {
            vpnGateConnectionList = dataUtil!!.connectionsCache
            if (vpnGateConnectionList == null || vpnGateConnectionList!!.size() == 0) {
                getDataServer()
            } else {
                updateData(vpnGateConnectionList!!)
            }
        } else {
            Toast.makeText(this, "Network Error!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateData(vpnGateConnectionList: VPNGateConnectionList) {
        this.vpnGateConnectionList = vpnGateConnectionList
        serverListRVAdapter?.initialize(vpnGateConnectionList)
    }

    private fun getDataServer() {
        if (!isApiCallGoing) {
            isApiCallGoing = true
            progressBar.visibility = View.VISIBLE
            loadingTxt.visibility = View.VISIBLE
            loadingTxt.text = getString(R.string.loading_servers_please_wait)
            launch {
                val vpnGateConnectionList = getVpnConnectionList()
                if (vpnGateConnectionList?.size()!! > 0) {
                    isApiCallGoing = false
                    progressBar.visibility = View.GONE
                    loadingTxt.visibility = View.GONE
                    serverListRVAdapter?.initialize(vpnGateConnectionList)
                    if (dataUtil?.lastVPNConnection == null) {
                        dataUtil!!.lastVPNConnection = vpnGateConnectionList.get(0)
                        mVpnGateConnection = dataUtil!!.lastVPNConnection
                        btnConnect.isActivated = false
                        btnConnect.isEnabled = true
                        bindData()
                    }
                    dataUtil!!.connectionsCache = vpnGateConnectionList
                    val mPeriodicWorkRequest = PeriodicWorkRequest.Builder(
                        ServerUpdate::class.java,
                        2, TimeUnit.HOURS
                    )
                        .addTag("periodicWorkRequest")
                        .build()
                    WorkManager.getInstance().enqueue(mPeriodicWorkRequest)
                } else {
                    isApiCallGoing = false
                    progressBar.visibility = View.GONE
                    loadingTxt.visibility = View.VISIBLE
                    loadingTxt.text = getString(R.string.loading_failed_please_try_again)
                    Toast.makeText(this@MainActivity, "Server Error!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private suspend fun getVpnConnectionList(): VPNGateConnectionList? {
        return withContext(Dispatchers.IO) {
            val dataUtil: DataUtil = GlobalApp.getInstance()
                .dataUtil
            var vpnList = VPNGateConnectionList()
            var connection: HttpURLConnection? = null
            try {
                val url = "http://www.vpngate.net/api/iphone/"
                val urlConnect = URL(url)
                connection = urlConnect.openConnection() as HttpURLConnection
                connection.readTimeout = 10000
                connection.connectTimeout = 10000
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.connect()
                vpnList = readLineSuspending(connection.inputStream)
                if (vpnList.size() == 0 && !isRetried) {
                    isRetried = true
                    dataUtil.setUseAlternativeServer(true)
                    cancel()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            } finally {
                connection?.disconnect()
            }
            return@withContext vpnList
        }
    }

    private suspend fun readLineSuspending(inputStream: InputStream?) =
        withContext(Dispatchers.IO) {
            val vpnGateConnectionList = VPNGateConnectionList()
            var br: BufferedReader? = null

            var line: String
            try {
                br = BufferedReader(InputStreamReader(inputStream!!))
                while (br.readLine().also { line = it } != null) {
                    if (line.indexOf("*") != 0 && line.indexOf("#") != 0) {
                        val vpnGateConnection: VPNGateConnection? = VPNGateConnection.fromCsv(line)
                        vpnGateConnection?.let {
                            vpnGateConnectionList.add(it)
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            } finally {
                if (br != null) {
                    try {
                        br.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            vpnGateConnectionList
        }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        unregisterReceiver(broadcastReceiver)
        unregisterReceiver(brStatus)
        unregisterReceiver(trafficReceiver)
    }

    override fun clickedItem(index: Int) {
        launch {
            if(!isConnecting){
                dataUtil?.lastVPNConnection = vpnGateConnectionList?.get(index)
                mVpnGateConnection = dataUtil!!.lastVPNConnection
                val status = checkStatus()
                if (status) {
                    stopVpn()
                }
                delay(500)
                connectVpn()
            }else{
                showToast("Please Wait...")
            }
        }
    }

    override fun onLongclickedItem(index: Int) {
        //Toast.makeText(this, "Long Item $index", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            if (resultCode == Activity.RESULT_OK) {
                when (requestCode) {
                    DetailsActivity.START_VPN_PROFILE -> VPNLaunchHelper.startOpenVpn(
                        vpnProfile,
                        this
                    )
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

}
