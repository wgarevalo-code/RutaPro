package com.rutapro.analyzer.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rutapro.analyzer.OverlayService
import com.rutapro.analyzer.R
import com.rutapro.analyzer.analyzer.AppSettings
import com.rutapro.analyzer.analyzer.OfferGate
import com.rutapro.analyzer.analyzer.RideAnalyzer
import com.rutapro.analyzer.analyzer.Verdict
import com.rutapro.analyzer.location.LocationTracker
import com.rutapro.analyzer.parser.RideParser

/**
 * Captura la pantalla y la lee con OCR (todo dentro del telefono, nada sale a internet).
 * Reemplaza al antiguo servicio de accesibilidad: aqui el permiso que se pide es
 * el de "grabar pantalla", que es el mismo que usan las apps de este tipo.
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var settings: AppSettings
    private lateinit var analyzer: RideAnalyzer
    private lateinit var tracker: LocationTracker

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val handler = Handler(Looper.getMainLooper())

    private var lastProcessed = 0L
    private var busy = false
    private var lastText = ""

    private var width = 0
    private var height = 0
    private var density = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        analyzer = RideAnalyzer(settings)
        tracker = LocationTracker(this)
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)

        if (resultCode != 0 && data != null && projection == null) {
            startCapture(resultCode, data)
        }
        tracker.start()
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        if (projection == null) {
            Log.e(TAG, "No se pudo obtener MediaProjection")
            return
        }

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                releaseCapture()
            }
        }, handler)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        density = metrics.densityDpi
        // Capturamos a media resolucion: alcanza para leer y gasta mucha menos bateria.
        width = metrics.widthPixels / 2
        height = metrics.heightPixels / 2

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
            if (image == null) return@setOnImageAvailableListener

            // Solo se analiza si esta encendido y como maximo cada 1.5 s.
            if (!settings.running || busy || now - lastProcessed < 1500) {
                image.close()
                return@setOnImageAvailableListener
            }
            lastProcessed = now
            busy = true

            val bitmap = try { imageToBitmap(image) } catch (e: Exception) { null }
            image.close()

            if (bitmap == null) { busy = false; return@setOnImageAvailableListener }
            runOcr(bitmap)
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "RutaProCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        Log.i(TAG, "Captura iniciada ${width}x$height")
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPadding = rowStride - pixelStride * image.width

        val bmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(plane.buffer)
        return if (bmp.width != image.width) {
            Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        } else bmp
    }

    private fun runOcr(bitmap: Bitmap) {
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                handleText(result.text)
                bitmap.recycle()
                busy = false
            }
            .addOnFailureListener {
                bitmap.recycle()
                busy = false
            }
    }

    private fun handleText(text: String) {
        if (text.isBlank() || text == lastText) return
        lastText = text

        if (settings.debugMode) {
            OverlayService.instance?.showDebug("OCR / Uber", text)
        }

        val parsed = RideParser.parseWithApp(text) ?: return
        if (!parsed.offer.isValid) return

        // Cuenta y muestra solo la primera vez que aparece esta oferta.
        if (!OfferGate.isNew(parsed.offer)) return

        val analysis = analyzer.analyze(parsed.offer)
        if (analysis.verdict == Verdict.BAD) settings.recordReject() else settings.recordAccept()

        OverlayService.instance?.showAnalysis(parsed.offer, analysis, parsed.app)
    }

    private fun releaseCapture() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        projection = null
    }

    private fun startForegroundNotification() {
        val channelId = "ruta_pro_capture"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "RutaPro captura", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        val notification = builder
            .setContentTitle("RutaPro leyendo la pantalla")
            .setContentText("Analizando carreras · todo queda en tu teléfono")
            .setSmallIcon(R.drawable.ic_arrow_white)
            .build()

        // El tipo de ubicacion solo se declara si el permiso esta concedido:
        // declararlo sin permiso hace que Android 14 mate el servicio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (LocationTracker(this).hasPermission()) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(2, notification, types)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tracker.stop()
        releaseCapture()
        try { recognizer.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "RutaCapture"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "stop"
    }
}
