package app.carero

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import app.carero.ui.CareroApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestSpeed.launch(SPEED_PERMISSION)
        }
        setContent { CareroApp(viewModel) }
    }

    companion object {
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
