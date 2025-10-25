package com.logic.tflitecapi

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.gson.Gson
import com.logic.tflitecapi.AreaDibujo.area
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds

class ClasificarNumeros : AppCompatActivity() {
    private var showingEnglish = true


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_clasificar_numeros)

        MobileAds.initialize(this) {}
//        val adView = findViewById<AdView>(R.id.adView)
//        val adRequest = AdRequest.Builder().build()
//        adView.loadAd(adRequest)

        val textDescription = findViewById<TextView>(R.id.textDescription)
       // val btnTranslate = findViewById<Button>(R.id.btnTranslate)
        val btnBack = findViewById<Button>(R.id.btnBack)

        val englishText = """
            This application is natively developed in Kotlin and Java, while the object recognition process is implemented in C++. 
            Inferences are performed using TensorFlow Lite every millisecond for each frame processed by the camera in the native C++ library. 
            Kotlin sends the frame in Bitmap format to the C++ layer, where it is converted into an OpenCV matrix. 
            This matrix is then processed through the TensorFlow Lite C++ API for object detection. 
            Once the detection is completed, the C++ code returns the coordinates of the detected objects, 
            along with their class names and confidence scores. 
            Finally, Kotlin uses this information to draw the detected objects on the screen.
        """.trimIndent()

        val spanishText = """
            Esta aplicación está desarrollada de forma nativa en Kotlin y Java, mientras que el proceso de reconocimiento de objetos está implementado en C++. 
            Las inferencias se realizan con TensorFlow Lite cada milisegundo por cada fotograma procesado por la cámara en la biblioteca nativa C++. 
            Kotlin envía el fotograma en formato Bitmap a la capa C++, donde se convierte en una matriz de OpenCV. 
            Esta matriz se procesa luego mediante la API C++ de TensorFlow Lite para realizar la detección de objetos. 
            Una vez completada la detección, el código C++ devuelve las coordenadas de los objetos detectados, 
            junto con el nombre de la clase y el nivel de confianza. 
            Finalmente, Kotlin utiliza esta información para dibujar los objetos detectados en la pantalla.
        """.trimIndent()

        // Texto inicial en inglés
        textDescription.text = englishText

//        btnTranslate.setOnClickListener {
//            if (showingEnglish) {
//                textDescription.text = spanishText
//                btnTranslate.text = "EN"
//                btnBack.text = "BACK"
//            } else {
//                textDescription.text = englishText
//                btnTranslate.text = "ES"
//                btnBack.text = "REGRESAR"
//            }
//            showingEnglish = !showingEnglish
//        }
        btnBack.setOnClickListener {
            finish()
        }
    }
}
