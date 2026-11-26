package dev.sashimi.picsviewer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.sashimi.picsviewer.model.PICS_COMPANY_ID

/**
 * 高度化PICS の BLE アドバタイズ（Company ID 0x01CE）だけを拾うスキャナ。
 * GATT 接続は行わない（受信専用）。
 */
class PicsScanner(context: Context) {

    private val appContext = context.applicationContext
    private val manager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)

    private var callback: ScanCallback? = null

    val adapter: BluetoothAdapter? get() = manager?.adapter

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun start(
        onData: (address: String, rssi: Int, data: ByteArray) -> Unit,
        onFailure: (String) -> Unit,
    ): Boolean {
        stop()
        val scanner = try {
            adapter?.bluetoothLeScanner
        } catch (_: SecurityException) {
            onFailure("Bluetooth の権限がありません")
            return false
        }
        if (scanner == null) {
            onFailure("Bluetooth がオフ、または利用できません")
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0L)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val data = record.getManufacturerSpecificData(PICS_COMPANY_ID) ?: return
                if (data.size < 10) return
                onData(result.device.address, result.rssi, data)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                onFailure("スキャンを開始できませんでした (code=$errorCode)")
            }
        }

        callback = cb
        return try {
            scanner.startScan(null, settings, cb)
            true
        } catch (e: SecurityException) {
            callback = null
            onFailure("Bluetooth の権限がありません")
            false
        } catch (e: IllegalStateException) {
            callback = null
            onFailure("Bluetooth がオフです")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
            // 権限が剥がされた場合は何もしない
        } catch (_: IllegalStateException) {
            // Bluetooth が切れている場合も無視
        }
    }
}
