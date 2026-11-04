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
 * 주행/정차 판단용 차속과 기어. Pleos 가이드에 따라 표준 데이터는 AAOS CarPropertyManager로 받습니다.
 * 2km/h 이상이거나 기어가 D이면 주행 중으로 봅니다(에뮬레이터 Virtual Console은 기어만 바꿀 수 있음).
 */
class SpeedSource(private val context: Context) {
    private val _speedKmh = MutableStateFlow<Float?>(null)
    val speedKmh: StateFlow<Float?> = _speedKmh
    private val _gearDrive = MutableStateFlow<Boolean?>(null)
    /** 기어가 D면 true, P·N·R이면 false */
    val gearDrive: StateFlow<Boolean?> = _gearDrive
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { _speedKmh.value = it * 3.6f } // m/s → km/h
                VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR ->
                    (value.value as? Int)?.let { _gearDrive.value = it == GEAR_DRIVE || it >= GEAR_1 }
            }
        }
        override fun onErrorEvent(propId: Int, zone: Int) {}
    }

    fun start() {
        try {
            val c = Car.createCar(context.applicationContext) ?: return
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            runCatching { m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
                .onFailure { Log.w("CareroSpeed", "speed unavailable", it) }
            runCatching { m.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                .onFailure { Log.w("CareroSpeed", "gear unavailable", it) }
        } catch (e: Throwable) {
            // 권한 미허용이거나 자동차 OS가 아닌 경우
            Log.w("CareroSpeed", "speed unavailable", e)
        }
    }

    fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
    }

    companion object {
        // VHAL VehicleGear 값
        private const val GEAR_DRIVE = 0x0008
        private const val GEAR_1 = 0x0010
    }
}
