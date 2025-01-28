package com.logic.tflitecapi

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private lateinit var limpiar: Button
    private lateinit var detectar: Button
    private lateinit var numeroClas: TextView

    val retro = Retrofit.Builder()
        .baseUrl("http://192.168.0.117:5000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val api = retro.create(ApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //EdgeToEdge.enable(this)
        setContentView(R.layout.activity_clasificar_numeros)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        limpiar = findViewById(R.id.limpiar)
        limpiar.setOnClickListener { area.clean() }

        numeroClas = findViewById(R.id.clasificado)
        detectar = findViewById(R.id.clasificar)
        detectar.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch{
                val numero: Bitmap = area.getBitmap()

                val output = ByteArrayOutputStream()
                numero.compress(Bitmap.CompressFormat.JPEG, 100, output)
                val bytes = output.toByteArray()

                val res = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val multpart = MultipartBody.Part.createFormData("image", "image.jpeg", res)

                val call = api.process_numero(multpart)

                try {
                    val result = call.execute()
                    if (result.isSuccessful) {
                        val body: String? = result.body()?.string()

                        numeroClas.text = body
                        Log.d("ClasificarNumeros", "Response: $body")
                    }
                } catch (e: Exception) {
                    Log.e("ClasificarNumeros", "Error: ${e.message}")
                }

            }
        }
    }
}
