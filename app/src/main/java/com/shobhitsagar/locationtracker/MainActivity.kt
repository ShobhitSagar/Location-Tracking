package com.shobhitsagar.locationtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.shobhitsagar.locationtracker.databinding.ActivityMainBinding
import com.shobhitsagar.locationtracker.utils.LoadingDialog
import com.shobhitsagar.locationtracker.utils.LoadingDialog.Companion.LOADING_DIALOG

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var serviceIntent: Intent
    private lateinit var db: FirebaseDatabase
    private lateinit var dbRef: DatabaseReference

    private lateinit var bind: ActivityMainBinding
    private lateinit var loadingDialog: LoadingDialog

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        val appName = getString(R.string.app_name)
        loadingDialog = LoadingDialog()
        serviceIntent = Intent(this, MyBgService::class.java)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create()
        locationRequest.setInterval(4000)
        locationRequest.setFastestInterval(2000)
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        db = FirebaseDatabase.getInstance()
        dbRef = db.getReference("root/$appName/users/")

        databaseListener()

        bind.startBtn.setOnClickListener { startSendingLocation() }
        bind.stopBtn.setOnClickListener { stopSendingLocation() }

        showStartButton()
    }

    private fun databaseListener() {
        var i = 0
        dbRef.child("9213143881").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").value.toString()
                val lng = snapshot.child("lng").value.toString()

                bind.textView.text = "From Database ${i++}\nLatitude : $lat\nLongitude : $lng"
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "onCancelled: Firebase Database Error", p0.toException())
            }

        })
    }

    // TODO: Send Current location
    // TODO: Start background service
    private fun startSendingLocation() {
        checkLocationSettings()
    }

    private fun checkLocationSettings() {
        loadingDialog.show(supportFragmentManager, LOADING_DIALOG)
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest).build()
        val client = LocationServices.getSettingsClient(this)

        val locationSettingsResponse = client.checkLocationSettings(request)

        locationSettingsResponse.addOnSuccessListener {
            getLastLocation()
//            startLocationUpdates()
        }

        locationSettingsResponse.addOnFailureListener {
            if (it is ResolvableApiException) {
                val apiException = it
                apiException.startResolutionForResult(this, 1001)
            }
            loadingDialog.dismiss()
        }
    }

    // TODO: Stop background Service
    private fun stopSendingLocation() {
        val dialogBuild = MaterialAlertDialogBuilder(this)
        dialogBuild.setTitle("Stop service").setMessage("Are you sure you want to stop the service?")
            .setPositiveButton("Yes") { _, _ ->
                showStartButton()
                fusedLocationClient.removeLocationUpdates(locationCallback)
                stopService(serviceIntent)
            }
            .setNegativeButton("No") { _, _ -> }

        dialogBuild.create().show()
    }

    private fun getLastLocation() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            loadingDialog.dismiss()
            Snackbar.make(bind.rootView, "Location permission denied.", Snackbar.LENGTH_INDEFINITE)
                .setAction("Retry") {
                    checkLocationSettings()
                }
                .setAnchorView(bind.startBtn)
                .show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

//        fusedLocationClient.lastLocation.addOnSuccessListener {
//            val lat = it.latitude
//            val lng = it.longitude
//
//            bind.textView.text = "Latitude : $lat\nLongitude : $lng"
//
//            dbRef.child("9213143881").apply {
//                child("lat").setValue(lat)
//                child("lng").setValue(lng)
//            }
//        }
//
//        fusedLocationClient.lastLocation.addOnFailureListener {
//            Snackbar.make(bind.rootView, "Something went wrong.", Snackbar.LENGTH_SHORT)
//                .setAction("Retry") {
//                    checkLocationSettings()
//                }
//                .setAnchorView(bind.startBtn)
//                .show()
//        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                            grantResults[1] == PackageManager.PERMISSION_GRANTED)
                ) {
                    checkLocationSettings()
                } else {
                    Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (locationResult != null) {
                startService(serviceIntent)
                showStopButton()
                loadingDialog.dismiss()
                locationResult.locations.forEach {
                    val lat = it.latitude
                    val lng = it.longitude

                    Log.d(TAG, "onLocationResult: Latitude - ${it.latitude}")
                    Log.d(TAG, "onLocationResult: Longitude - ${it.longitude}")

//                    bind.textView.text = "Latitude : $lat\nLongitude : $lng"

                    dbRef.child("9213143881").apply {
                        child("lat").setValue(lat)
                        child("lng").setValue(lng)
                    }
                }
            }
        }
    }

    private fun showStartButton() {
        bind.stopBtn.visibility = View.GONE
        bind.startBtn.visibility = View.VISIBLE
//        bind.stopBtn.isEnabled = false
//        bind.stopBtn.setBackgroundColor(ContextCompat.getColor(this, androidx.appcompat.R.color.material_grey_600))
//        bind.startBtn.isEnabled = true
//        bind.startBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
    }

    private fun showStopButton() {
        bind.stopBtn.visibility = View.VISIBLE
        bind.startBtn.visibility = View.GONE
//        bind.startBtn.isEnabled = false
//        bind.startBtn.setBackgroundColor(ContextCompat.getColor(this, androidx.appcompat.R.color.material_grey_600))
//        bind.stopBtn.isEnabled = true
//        bind.stopBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
    }

    companion object {
        const val DISTANCE = "distance"
        const val LOCATION_PERMISSION_REQUEST_CODE = 102
    }
}