package com.shobhitsagar.locationtracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.shobhitsagar.locationtracker.databinding.ActivityMainBinding

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private var isServiceRunning: Boolean = false
    private lateinit var serviceIntent: Intent
    private lateinit var db: FirebaseDatabase
    private lateinit var dbRef: DatabaseReference

    private lateinit var bind: ActivityMainBinding
    private lateinit var loadingDialog: AlertDialog

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private var cuLat: Double = 0.0
    private var cuLng: Double = 0.0
    private var tempId: String? = null
    private var currentUserId: String? = null
    private var requestId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        val appName = getString(R.string.app_name)
        loadingDialog = loadingDialog()
        serviceIntent = Intent(this, MyBgService::class.java)

        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        currentUserId = sharedPref.getString(getString(R.string.current_user_id), "")
        requestId = sharedPref.getString(getString(R.string.request_id), "")
        isServiceRunning = sharedPref.getBoolean(getString(R.string.is_service_running), false)

        if (!currentUserId.isNullOrBlank()) bind.startServiceEt.setText(currentUserId)
        if (!requestId.isNullOrBlank()) bind.requestServiceEt.setText(requestId)
        if (isServiceRunning) showStopButton() else showStartButton()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create()
        locationRequest.setInterval(4000)
        locationRequest.setFastestInterval(2000)
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        db = FirebaseDatabase.getInstance()
        dbRef = db.getReference("root/$appName/users/")

        databaseListener()

        // Start Service
        bind.startBtn.setOnClickListener {
            tempId = bind.startServiceEt.text.toString()

            if (!tempId.isNullOrBlank()) {
                currentUserId = tempId as String
                loadingDialog.show()
                with (sharedPref.edit()) {
                    putString(getString(R.string.current_user_id), tempId)
                    putBoolean(getString(R.string.is_service_running), true)
                    apply()
                }
                startSendingLocation()
            } else {
                showSnakbar("Please enter a valid Id.")
                bind.startServiceEt.error = "A valid Id is required."
            }
        }

        // Stop Service
        bind.stopBtn.setOnClickListener {
            tempId = bind.startServiceEt.text.toString()

            with (sharedPref.edit()) {
                putBoolean(getString(R.string.is_service_running), false)
                apply()
            }

            if (!tempId.isNullOrBlank()) {
                currentUserId = tempId as String
                loadingDialog.show()

                dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        if (snap.hasChild(requestId!!)) {
                            stopSendingLocation()
                        } else showSnakbar("Wrong ID or user doesn't exists.")
                    }

                    override fun onCancelled(p0: DatabaseError) {
                        loadingDialog.dismiss()
                        showSnakbar("Something went wrong!")
                    }
                })
            } else {
                showSnakbar("Please enter a valid Id.")
                bind.startServiceEt.error = "A valid Id is required."
            }
        }

        // Request Service
        bind.requestServiceBtn.setOnClickListener {
            tempId = bind.requestServiceEt.text.toString()

            if (!tempId.isNullOrBlank()) {
                requestId = tempId as String
                loadingDialog.show()
                dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        if (snap.hasChild(requestId!!)) {
                            startDistanceService()
                            with (sharedPref.edit()) {
                                putString(getString(R.string.request_id), tempId)
                                apply()
                            }
                        } else showSnakbar("Wrong ID or user doesn't exists.")
                    }

                    override fun onCancelled(p0: DatabaseError) {
                        loadingDialog.dismiss()
                        showSnakbar("Something went wrong!")
                    }
                })
            } else {
                showSnakbar("Please enter a valid Id.")
                bind.requestServiceEt.error = "A valid Id is required."
            }
        }

        // View Location Btn
        bind.viewLocationBtn.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(R.layout.bottom_sheet_layout)
            bottomSheetDialog.show()
        }

//        showStartButton()
    }

    private fun showSnakbar(msg: String) {
        Snackbar.make(bind.rootView, msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun startDistanceService() {

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

        fusedLocationClient.lastLocation.addOnSuccessListener {
            cuLat = it.latitude
            cuLng = it.longitude

            loadingDialog.dismiss()
        }

        fusedLocationClient.lastLocation.addOnFailureListener {
            Snackbar.make(bind.rootView, "Something went wrong.", Snackbar.LENGTH_SHORT)
                .setAction("Retry") {
                    checkLocationSettings()
                }
                .setAnchorView(bind.startBtn)
                .show()
            loadingDialog.dismiss()
        }

        distanceHandler()
    }

    private fun distanceHandler() {
        dbRef.child(requestId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = (snapshot.child("lat").value.toString()).toDouble()
                val lng = (snapshot.child("lng").value.toString()).toDouble()

                val results = FloatArray(1)
                Location.distanceBetween(
                    cuLat, cuLng,
                    lat, lng, results
                )
                val distance = results[0]

                var strDistance = if (distance > 1000) (distance/1000).toString() else distance.toInt().toString()
                var distUnit = "meters"

                if (distance > 1000) {
                    strDistance = strDistance.substring(0, strDistance.indexOf('.')+2)
                    distUnit = "Kms"
                }

                bind.textView.text = "$strDistance $distUnit away"
//                startDistanceService()
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "onCancelled: Firebase Database Error", p0.toException())
                loadingDialog.dismiss()
            }
        })
        loadingDialog.dismiss()
    }

    private fun databaseListener() {
        dbRef.child(currentUserId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").value.toString()
                val lng = snapshot.child("lng").value.toString()

                bind.textView.text = "Your Location:\nLatitude : $lat\nLongitude : $lng"
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "onCancelled: Firebase Database Error", p0.toException())
                loadingDialog.dismiss()
            }

        })
        loadingDialog.dismiss()
    }

    // TODO: Send Current location
    // TODO: Start background service
    private fun startSendingLocation() {
        checkLocationSettings()
    }

    private fun checkLocationSettings() {
        loadingDialog.show()
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
                loadingDialog.show()
                showStartButton()
                stopService(serviceIntent)
                fusedLocationClient.removeLocationUpdates(locationCallback)
                loadingDialog.dismiss()
                dbRef.child(currentUserId!!).child("serviceRunning").setValue(false)
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
        startService(serviceIntent)

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
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
                            grantResults[0] == PackageManager.PERMISSION_DENIED &&
                            grantResults[1] == PackageManager.PERMISSION_DENIED)
                ) {
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

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            showStopButton()
            loadingDialog.dismiss()
            locationResult.locations.forEach {
                val lat = it.latitude
                val lng = it.longitude

                Log.d(TAG, "onLocationResult: Latitude - ${it.latitude}")
                Log.d(TAG, "onLocationResult: Longitude - ${it.longitude}")

//                    bind.textView.text = "Latitude : $lat\nLongitude : $lng"

                dbRef.child(currentUserId!!).apply {
                    child("lat").setValue(lat)
                    child("lng").setValue(lng)
                }
            }
            loadingDialog.dismiss()
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
    
    private fun loadingDialog(): AlertDialog {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(R.layout.loading_dialog_layout)
        
        return builder.create()
    }

    companion object {
        const val DISTANCE = "distance"
        const val LOCATION_PERMISSION_REQUEST_CODE = 102
    }
}