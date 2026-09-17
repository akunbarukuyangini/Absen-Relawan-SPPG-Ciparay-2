package id.sppg.ciparay2.absen

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import id.sppg.ciparay2.absen.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var startUrl: String = ""
    private var allowedHost: String = ""

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingWebPermission: PermissionRequest? = null

    private var lastBackPress = 0L

    // ---------- Activity result / permission launchers ----------

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null

            if (result.resultCode != RESULT_OK) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }

            val data = result.data
            val uris: Array<Uri>? = when {
                data?.data != null -> arrayOf(data.data!!)
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                cameraOutputUri != null -> arrayOf(cameraOutputUri!!)
                else -> null
            }
            callback.onReceiveValue(uris)
            cameraOutputUri = null
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants.values.any { it }
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
            if (!granted) toast(getString(R.string.perm_location_needed))
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingWebPermission
            pendingWebPermission = null
            if (granted && request != null) {
                request.grant(request.resources)
            } else {
                request?.deny()
                if (!granted) toast(getString(R.string.perm_camera_needed))
            }
        }

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startUrl = getString(R.string.start_url)
        allowedHost = Uri.parse(startUrl).host ?: ""

        setupWebView()
        setupSwipeRefresh()
        setupBackHandling()

        if (savedInstanceState == null) {
            loadStartUrl()
        } else {
            binding.webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    // ---------- Setup ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setGeolocationEnabled(true)
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "$userAgentString AbsenSPPGCiparay2/1.0"
            }

            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            webViewClient = AppWebViewClient()
            webChromeClient = AppWebChromeClient()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.brand)
        binding.swipeRefresh.setOnRefreshListener {
            if (isOnline()) binding.webView.reload() else loadStartUrl()
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    finish()
                } else {
                    lastBackPress = now
                    toast(getString(R.string.exit_confirm))
                }
            }
        })
    }

    // ---------- Loading ----------

    private fun loadStartUrl() {
        if (isOnline()) {
            binding.webView.loadUrl(startUrl)
        } else {
            showOfflinePage()
        }
    }

    private fun showOfflinePage() {
        binding.webView.loadUrl("file:///android_asset/offline.html")
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork != null && cm.getNetworkCapabilities(cm.activeNetwork) != null
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---------- JS bridge (used by offline.html) ----------

    inner class AndroidBridge {
        @JavascriptInterface
        fun retry() {
            runOnUiThread { loadStartUrl() }
        }
    }

    // ---------- WebViewClient ----------

    private inner class AppWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url
            val scheme = url.scheme ?: return false

            // Only non-web links (tel:, mailto:, whatsapp:, intent:) leave the app.
            // Every http/https link — any domain, including SSO/login redirects —
            // stays inside this WebView so the user never sees a browser chooser.
            return if (scheme != "http" && scheme != "https") {
                openExternally(url)
            } else {
                false
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.swipeRefresh.isRefreshing = false
            binding.progressBar.visibility = android.view.View.GONE
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                binding.swipeRefresh.isRefreshing = false
                showOfflinePage()
            }
        }

        private fun openExternally(url: Uri): Boolean {
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, url))
                true
            } catch (e: ActivityNotFoundException) {
                toast("Tidak ada aplikasi untuk membuka tautan ini")
                true
            }
        }
    }

    // ---------- WebChromeClient: progress, geolocation, camera, file upload ----------

    private inner class AppWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.apply {
                visibility = if (newProgress in 1..99) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                progress = newProgress
            }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback
        ) {
            val fine = Manifest.permission.ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(this@MainActivity, fine)
                == PackageManager.PERMISSION_GRANTED
            ) {
                callback.invoke(origin, true, false)
                return
            }
            pendingGeoOrigin = origin
            pendingGeoCallback = callback
            locationPermissionLauncher.launch(
                arrayOf(fine, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            if (!wantsCamera) {
                request.deny()
                return
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                request.grant(request.resources)
            } else {
                pendingWebPermission = request
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback

            val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = fileChooserParams.acceptTypes
                    .firstOrNull { it.isNotBlank() } ?: "*/*"
                if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

            val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, contentIntent)
                putExtra(Intent.EXTRA_TITLE, getString(R.string.choose_file))
                createCameraIntent()?.let {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
                }
            }

            return try {
                fileChooserLauncher.launch(chooser)
                true
            } catch (e: ActivityNotFoundException) {
                fileChooserCallback = null
                filePathCallback.onReceiveValue(null)
                false
            }
        }

        private fun createCameraIntent(): Intent? {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) return null

            return try {
                val dir = File(cacheDir, "captures").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val photo = File(dir, "absen_$stamp.jpg")
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    photo
                )
                cameraOutputUri = uri
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
