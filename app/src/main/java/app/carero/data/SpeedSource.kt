package app.carero.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행/정차 판단용 차속. Pleos 가이드에 따라 표준 데이터는 AAOS CarPropertyManager로 받습니다.
 * 2km/h 이상이면 주행 중으로 봅니다.
 */
class SpeedSource(private val context: Context) {
    private val _speedKmh = MutableStateFlow<Float?>(null)
    val speedKmh: StateFlow<Float?> = _speedKmh
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { _speedKmh.value = it * 3.6f } // m/s → km/h
        }
        override fun onErrorEvent(propId: Int, zone: Int) {}
    }

    fun start() {
        try {
            val c = Car.createCar(context.applicationContext) ?: return
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        } catch (e: Throwable) {
            // 권한 미허용이거나 자동차 OS가 아닌 경우
            Log.w("CareroSpeed", "speed unavailable", e)
        }
    }

    fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
    }
}
