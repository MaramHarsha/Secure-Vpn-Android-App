package com.technoupdate.securevpn.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.technoupdate.securevpn.GlobalApp
import com.technoupdate.securevpn.model.VPNGateConnection
import com.technoupdate.securevpn.model.VPNGateConnectionList
import com.technoupdate.securevpn.utils.DataUtil
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

class ServerUpdate(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private var isRetried = false

    override fun doWork(): Result {
        GlobalScope.launch {
            val vpnGateConnectionList = getVpnConnectionList()
            if (vpnGateConnectionList?.size()!! > 0) {
                val dataUtil = GlobalApp.getInstance().dataUtil
                dataUtil!!.connectionsCache = vpnGateConnectionList
                dataUtil.lastVPNConnection = vpnGateConnectionList.get(0)
            }
        }
        return Result.success()
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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

}