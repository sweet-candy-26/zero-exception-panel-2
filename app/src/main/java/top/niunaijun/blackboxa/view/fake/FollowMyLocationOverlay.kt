package top.niunaijun.blackboxa.view.fake

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackboxa.databinding.ActivityOsmdroidBinding
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import java.util.Locale

/**
 *
 * @Author: BlackBoxing
 * @CreateDate: 2022/3/14
 */
class FollowMyLocationOverlay : AppCompatActivity() {
    val TAG: String = "FollowMyLocationOverlay"

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1001

    private val binding: ActivityOsmdroidBinding by inflate()

    lateinit var startPoint: GeoPoint
    private var marker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(binding.root)

        initMap()
        initLocation()
        initListeners()
    }

    private fun initMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.isTilesScaledToDpi = true
        binding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                selectLocation(p)
                return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                selectLocation(p)
                return false
            }
        }
        binding.map.overlays.add(MapEventsOverlay(mapEventsReceiver))
    }

    private fun initLocation() {
        val location: BLocation? = intent.getParcelableExtra("location")

        if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
            startPoint = GeoPoint(location.latitude, location.longitude)
            setupInitialPoint(startPoint, 15.0)
        } else {
            val deviceLoc = getDeviceLocation()
            if (deviceLoc != null) {
                startPoint = deviceLoc
                setupInitialPoint(startPoint, 15.0)
            } else {
                startPoint = GeoPoint(23.8103, 90.4125)
                setupInitialPoint(startPoint, 12.0)
                checkLocationPermissionAndRequest()
            }
        }
    }

    private fun setupInitialPoint(point: GeoPoint, zoomLevel: Double) {
        val mapController = binding.map.controller
        mapController.setZoom(zoomLevel)
        mapController.setCenter(point)

        marker = Marker(binding.map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.map.overlays.add(marker)
        updateCoordinateDisplay(point)
    }

    private fun selectLocation(p: GeoPoint) {
        startPoint = p
        if (marker == null) {
            marker = Marker(binding.map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            binding.map.overlays.add(marker)
        }
        marker?.position = p
        binding.map.invalidate()
        updateCoordinateDisplay(p)
    }

    private fun updateCoordinateDisplay(point: GeoPoint) {
        binding.tvCoordinates.text = String.format(
            Locale.US,
            "Lat: %.6f, Lng: %.6f",
            point.latitude,
            point.longitude
        )
    }

    private fun initListeners() {
        binding.btnConfirm.setOnClickListener {
            finishWithResult(startPoint)
        }

        binding.fabMyLocation.setOnClickListener {
            if (hasLocationPermission()) {
                val loc = getDeviceLocation()
                if (loc != null) {
                    selectLocation(loc)
                    binding.map.controller.animateTo(loc)
                } else {
                    toast("Unable to get current location")
                }
            } else {
                checkLocationPermissionAndRequest()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermissionAndRequest() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun getDeviceLocation(): GeoPoint? {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            if (!hasLocationPermission()) return null

            val providers = lm.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = try {
                    lm.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                } ?: continue

                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }

            if (bestLocation != null) {
                return GeoPoint(bestLocation.latitude, bestLocation.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                val loc = getDeviceLocation()
                if (loc != null) {
                    selectLocation(loc)
                    binding.map.controller.animateTo(loc)
                }
            }
        }
    }

    override fun onBackPressed() {
        finishWithResult(startPoint)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    private fun finishWithResult(geoPoint: GeoPoint) {
        intent.putExtra("latitude", geoPoint.latitude)
        intent.putExtra("longitude", geoPoint.longitude)
        setResult(Activity.RESULT_OK, intent)
        val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.run {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
        finish()
    }
}
