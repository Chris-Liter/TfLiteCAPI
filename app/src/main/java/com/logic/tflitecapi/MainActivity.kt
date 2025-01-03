package com.logic.tflitecapi


import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.camera.core.Camera
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.logic.tflitecapi.databinding.ActivityMainBinding
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

interface DetectorListener {
    fun onError(error: String)
    fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    )

    fun onResultsC(
        results: MutableList<Results>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    )
}


class MainActivity : AppCompatActivity() , DetectorListener{

    private var binding: ActivityMainBinding? = null

    private val fragmento
        get() = binding!!

    private val processingThread = HandlerThread("ProcessingThread").apply { start() }
    private val processingHandler = Handler(processingThread.looper)
    //private val gpuDelegateWrapper = GpuDelegateWrapper()

    private lateinit var imagen: ImageView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var camera: Camera? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var objectDetector: ObjectDetector?= null
    private var objectDetectorListener: DetectorListener? = this
    private var sesionIniciada: Boolean = false
    private var cambio: Boolean = false





    var cocoNamesPath : String = ""
    var modelo : String = ""

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector = null
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(fragmento.root)

        // Example of a call to a native method
        fragmento.texto.text = stringFromJNI()
        fragmento.cambio.text = "Api Java"

        fragmento.cambio.setOnClickListener{
            cambio = !cambio
            if(cambio) {
                fragmento.cambio.text = "Api C++"
                objectDetector = null
                //fragmento.overlay.invalidate()
            }else {
                fragmento.cambio.text = "Api Java"
                LiberarRecursos()
            }
        }
        //imagen = findViewById(R.id.imageView)


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val nnapiSupported = android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a") // Asumiendo que tienes un dispositivo con ARM
            if (nnapiSupported) {
                println("GPU support available via NNAPI")
            } else {
                println("No GPU support available")
            }
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        fragmento.camara.post{

            setUpCamera()
        }
        if (allPermissionsGranted()) {
            //setUpCamera()
        } else {
            this.let {
                ActivityCompat.requestPermissions(
                    it,
                    Constants.REQUIRED_PERMISSIONS,
                    Constants.REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    override fun onDestroy() {
        binding = null
        super.onDestroy()

        cameraExecutor.shutdown()
    }



    private fun allPermissionsGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }


    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
//        cameraProvider = cameraProviderFuture.get()
//        startCamera()

        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                startCamera()
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmento.camara.display.rotation)
            .build()

        imageAnalyzer =
            ImageAnalysis.Builder()
                //.setTargetResolution(Size(640, 640))  // Resolución deseada para el análisis
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmento.camara.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()


                .also {
                    it.setAnalyzer(cameraExecutor){ image ->
                        if(!::bitmapBuffer.isInitialized){
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }
                        displayBitmap(image)
                    }
                }
        cameraProvider.unbindAll()


        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            preview?.setSurfaceProvider(fragmento.camara.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        //}, ContextCompat.getMainExecutor(this))
    }


    fun displayBitmap(image: ImageProxy) {
        //val imageView: ImageView = findViewById(R.id.imageView)  // Asegúrate de tener un ImageView con el ID correspondiente
        //val bOut: Bitmap = bitmap.copy(bitmap.config, true)
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        val imageRotation = image.imageInfo.rotationDegrees
        //detect(bitmap, bOut, yoloOnnxPath, cocoNamesPath)
        //val rotate = rotateBitmap(bOut, 90f)
        //imagen.setImageBitmap(bitmapBuffer)


        detects(bitmapBuffer, imageRotation)

    }

    fun setupObjectDetector(){
        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(0.5f)
            .setMaxResults(5)

        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(5)

        baseOptionsBuilder.useNnapi()

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try{
            //yoloOnnxPath = copyRawFileToInternalStorage(this, R.raw.mobilenetv3_model, "mobilenetv3_model.tflite")
            val modelFile = copyModelFromRawToInternalStorage(
                context = this, // Usa `this` si estás en una actividad
                rawResId = R.raw.mobilenetv1, // El ID del archivo en `res/raw`
                outputFileName = "mobilenetv1.tflite" // Nombre del archivo de salida
            )

            if(cambio) {

                modelo = copyRawFileToInternalStorage(this, R.raw.mobilenetv3_model, "mobilenetv3_model.tflite")
                cocoNamesPath = copyRawFileToInternalStorage(this, R.raw.names, "names.txt")

                sesionIniciada = CreateSession(modelo, cocoNamesPath, true)
                if(sesionIniciada == false) {
                    cambio = sesionIniciada
                    fragmento.cambio.isActivated = false
                }
                objectDetector =
                    ObjectDetector.createFromFileAndOptions(modelFile, optionsBuilder.build())
            }
            else
            {

                //cocoNamesPath  = copyRawFileToInternalStorage(this, R.raw.coco, "coco.names")

                //////////////////////////////////////////////////////////////////////////////////////////////////////
                objectDetector =
                    ObjectDetector.createFromFileAndOptions(modelFile, optionsBuilder.build())
            }


        }catch (e: Exception){
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details, ${e.message}"
            )
            Log.e("Test", "TFLite failed to load model with error: " + e.message)

        }
    }

    fun detects(image: Bitmap, imageRotation: Int) {
        if (objectDetector == null) {
            setupObjectDetector()
        }

        var inferenceTime = SystemClock.uptimeMillis()

        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()

        // Preprocess the image and convert it into a TensorImage for detection.

        if(cambio){
            var inferenceTime = SystemClock.uptimeMillis()
            val matriz: Matrix = Matrix()
            matriz.postRotate(90f)
            val imagenRotada = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matriz, true)

            val nombre: String = ""
            val results: String = detector(imagenRotada)

            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            fragmento.latencia.text = "$inferenceTime ms"
            fragmento.obje.text = results

        }
        else
        {
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

            val results= objectDetector?.detect(tensorImage)

            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            fragmento.latencia.text = "$inferenceTime ms"
            objectDetectorListener?.onResults(results, inferenceTime, tensorImage.height, tensorImage.width)

        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Los permisos no se concedieron.", Toast.LENGTH_SHORT).show()

                finish()
            }
        }
    }

    fun copyRawFileToInternalStorage(context: Context, rawResId: Int, fileName: String): String {
        val inputStream = context.resources.openRawResource(rawResId)
        val outputFile = File(/* parent = */ context.filesDir, /* child = */ fileName)

        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outputFile.absolutePath
    }

    fun copyModelFromRawToInternalStorage(context: Context, rawResId: Int, outputFileName: String): File {
        val outputFile = File(context.filesDir, outputFileName)
        if (!outputFile.exists()) {
            context.resources.openRawResource(rawResId).use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return outputFile
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmento.camara.display.rotation
    }

    /**
     * A native method that is implemented by the 'guagua' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
    external fun detector(bitmap: Bitmap): String //MutableList<Results>
    external fun CreateSession(model: String, labels: String, gpu: Boolean): Boolean
    external fun LiberarRecursos()

    companion object {
        // Used to load the 'guagua' library on application startup.
        init {
            System.loadLibrary("tflitecapi")
//            System.loadLibrary("libtensorflow_c")
//            System.loadLibrary("libtensorflow_gpu_delegate")


        }
    }

    override fun onError(error: String) {
        runOnUiThread{
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        runOnUiThread{
            fragmento.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )

            fragmento.overlay.invalidate()
        }
    }

    override fun onResultsC(
        results: MutableList<Results>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        runOnUiThread{
//            fragmento.overlay.setResults(
//                results ?: LinkedList<Results>(),
//                imageHeight,
//                imageWidth
//            )

            fragmento.overlay.invalidate()
        }
    }

}
