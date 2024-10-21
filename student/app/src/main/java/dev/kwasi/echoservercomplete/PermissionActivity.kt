package dev.kwasi.echoservercomplete

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PermissionActivity : AppCompatActivity() {
    private val requestCode = 1234

    private lateinit var tvPermissionTitle: TextView
    private lateinit var tvPermissionDescription: TextView
    private lateinit var btnRequestPermissions: Button
    private lateinit var btnGoToSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_permission)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupListeners()
        updateUI()
    }

    private fun initializeViews() {
        tvPermissionTitle = findViewById(R.id.tvPermissionTitle)
        tvPermissionDescription = findViewById(R.id.tvPermissionDescription)
        btnRequestPermissions = findViewById(R.id.btnRequestPermissions)
        btnGoToSettings = findViewById(R.id.btnGoToSettings)
    }

    private fun setupListeners() {
        btnRequestPermissions.setOnClickListener {
            requestPermissions()
        }

        btnGoToSettings.setOnClickListener {
            goToSettings()
        }
    }

    private fun updateUI() {
        if (hasAllPermissions()) {
            tvPermissionTitle.text = "Permissions Granted"
            tvPermissionDescription.text = "All necessary permissions have been granted. You can now use the app."
            btnRequestPermissions.visibility = View.GONE
            btnGoToSettings.visibility = View.GONE
        } else {
            tvPermissionTitle.text = "Permissions Required"
            tvPermissionDescription.text = "This app requires certain permissions to function properly. Please grant the necessary permissions to continue."
            btnRequestPermissions.visibility = View.VISIBLE
            btnGoToSettings.visibility = View.VISIBLE
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (SDK_INT >= 33) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestCode)
    }

    private fun hasAllPermissions(): Boolean {
        val basePermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )

        val allGranted = basePermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

        return if (SDK_INT >= 33) {
            allGranted && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            allGranted
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (hasAllPermissions()) {
                navigateToNextPage()
            } else {
                updateUI()
            }
        }
    }

    private fun navigateToNextPage() {
        val intent = Intent(this, CommunicationActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
        finish()
    }

    private fun goToSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            navigateToNextPage()
        } else {
            updateUI()
        }
    }
}
