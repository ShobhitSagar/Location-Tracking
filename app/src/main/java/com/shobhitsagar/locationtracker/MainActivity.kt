package com.shobhitsagar.locationtracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.shobhitsagar.locationtracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseDatabase
    private lateinit var dbRef: DatabaseReference

    private lateinit var bind: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        db = FirebaseDatabase.getInstance()
        dbRef = db.getReference("root/users/")

        bind.startBtn.setOnClickListener { startSendingLocation() }
        bind.stopBtn.setOnClickListener { stopSendingLocation() }

    }

    // TODO: Send Current location
    // TODO: Start background service
    // TODO: Start background service using Alarm Manager
    private fun startSendingLocation() {
    }

    // TODO: Stop background Service
    private fun stopSendingLocation() {

    }
}