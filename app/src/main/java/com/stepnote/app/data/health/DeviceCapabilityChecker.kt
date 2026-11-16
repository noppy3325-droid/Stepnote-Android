package com.stepnote.app.data.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.LocalRecordingClient
import kotlinx.coroutines.CancellationException

data class CapabilitySnapshot(
    val androidSdk: Int,
    val upsideDownCakeExtension: Int,
    val hasStepCounterSensor: Boolean,
    val healthConnectSdkStatus: Int,
    val supportsDeviceAutoSteps: Boolean,
    val supportsDeviceDataOriginResolver: Boolean,
    val supportsBackgroundReadFeature: Boolean,
    val hasActivityRecognition: Boolean,
    val isPlayServicesAvailable: Boolean
)

class DeviceCapabilityChecker(private val context: Context) {

    fun inspectSync(): CapabilitySnapshot {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        val udcExt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        } else {
            0
        }

        val hcStatus = HealthConnectClient.getSdkStatus(context)
        val supportsAutoSteps = hcStatus == HealthConnectClient.SDK_AVAILABLE && udcExt >= 20
        val supportsDeviceOriginResolver = hcStatus == HealthConnectClient.SDK_AVAILABLE && udcExt >= 22

        val hasActivityRecognition = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val gmsResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
            context, LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE
        )
        val playServicesOk = gmsResult == ConnectionResult.SUCCESS

        val bgFeatureAvailable = if (hcStatus == HealthConnectClient.SDK_AVAILABLE) {
            runCatching {
                val client = HealthConnectClient.getOrCreate(context)
                client.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
                ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            }.getOrDefault(false)
        } else {
            false
        }

        return CapabilitySnapshot(
            androidSdk = Build.VERSION.SDK_INT,
            upsideDownCakeExtension = udcExt,
            hasStepCounterSensor = hasSensor,
            healthConnectSdkStatus = hcStatus,
            supportsDeviceAutoSteps = supportsAutoSteps,
            supportsDeviceDataOriginResolver = supportsDeviceOriginResolver,
            supportsBackgroundReadFeature = bgFeatureAvailable,
            hasActivityRecognition = hasActivityRecognition,
            isPlayServicesAvailable = playServicesOk
        )
    }

    suspend fun getHealthConnectGrantedPermissions(): Set<String> {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return emptySet()
        }
        return runCatching {
            HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()
        }.getOrElse {
            if (it is CancellationException) throw it
            emptySet()
        }
    }

    companion object {
        val READ_STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
        val READ_BACKGROUND_PERMISSION: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }
}
