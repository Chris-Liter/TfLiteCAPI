#include <jni.h>
#include <string>
#include <android/log.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "android/bitmap.h"
#include <fstream>

#include <opencv2/opencv.hpp>


#include <tensorflow/lite/model.h>
#include <tensorflow/lite/kernels/register.h>
#include <tensorflow/lite/interpreter.h>
#include <tensorflow/lite/delegates/gpu/delegate.h>
#include <tensorflow/lite/builtin_op_data.h>
#include <tensorflow/lite/version.h>

#include <opencv2/objdetect/objdetect.hpp>

//#include <tensorflow/lite/delegates/gpu/metal_delegate.h>

#define LOG_TAG "TFLite-Error"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

unique_ptr<tflite::FlatBufferModel> _model;
unique_ptr<tflite::Interpreter> interprete = nullptr;
unique_ptr<TfLiteDelegate, decltype(&TfLiteGpuDelegateV2Delete)> gpuDelegate(nullptr, &TfLiteGpuDelegateV2Delete);

vector<string> cocoNames;

extern "C" JNIEXPORT jstring JNICALL
Java_com_logic_tflitecapi_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


///////////////////////
//flatc --cpp schema.fbs
////////////////////////



struct BoundingBox {
    int x;
    int y;
    int width;
    int height;

    BoundingBox() : x(0), y(0), width(0), height(0) {}
    BoundingBox(int x_, int y_, int width_, int height_)
            : x(x_), y(y_), width(width_), height(height_) {}

    float area() const { return static_cast<float>(width * height); }

    BoundingBox intersect(const BoundingBox &other) const {
        int xStart = std::max(x, other.x);
        int yStart = std::max(y, other.y);
        int xEnd = std::min(x + width, other.x + other.width);
        int yEnd = std::min(y + height, other.y + other.height);
        int intersectWidth = std::max(0, xEnd - xStart);
        int intersectHeight = std::max(0, yEnd - yStart);
        return BoundingBox{xStart, yStart, intersectWidth, intersectHeight};
    }
};

class Categoria{
public:
    string label;
    float score;
};


class Resultados{
public:
    BoundingBox boundingBox;
    vector<Categoria> categories;
};



void bitmapToMat(JNIEnv * env, jobject bitmap, cv::Mat &dst, jboolean needUnPremultiplyAlpha) {
    AndroidBitmapInfo info;
    void* pixels = 0;
    try {
        // Verificar información del bitmap
        CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
        CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 || info.format == ANDROID_BITMAP_FORMAT_RGB_565);

        // Bloquear píxeles
        CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
        CV_Assert(pixels);

        dst.create(info.height, info.width, CV_8UC4); // Crear matriz de destino

        // Procesar según formato
        if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if (needUnPremultiplyAlpha) cvtColor(tmp, dst, cv::COLOR_mRGBA2RGBA);
            else tmp.copyTo(dst);
        } else { // RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            cvtColor(tmp, dst, cv::COLOR_BGR5652RGBA);
        }

        // Liberar los píxeles
        AndroidBitmap_unlockPixels(env, bitmap);
    } catch (const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nBitmapToMat}");
    }
}

std::vector<std::string> getClassNames(const std::string& labelsPath) {
    std::vector<std::string> names;
    std::ifstream infile(labelsPath);
    std::string line;
    while (std::getline(infile, line)) {
        names.push_back(line);
    }
    return names;
}

extern "C"{
    JNIEXPORT jobject Java_com_logic_tflitecapi_MainActivity_detector(JNIEnv* env, jobject /**/, jobject bitmapIn){
        Mat inputMat;
        Mat copiaSize;

        bitmapToMat(env, bitmapIn, inputMat, false);

        //Mat norma;
        inputMat.copyTo(copiaSize);
        resize(inputMat, inputMat, Size(320, 320));
        cvtColor(inputMat, inputMat, cv::COLOR_BGR2RGB);

        inputMat.convertTo(inputMat, CV_8UC3);
        uint8_t* input = interprete->typed_input_tensor<uint8_t>(0); // Obtener puntero al tensor

        memcpy(input, inputMat.data, 320 * 320 * 3);

        if (interprete->Invoke() != kTfLiteOk) {
            LOGE("Error durante la invocación del intérprete TFLite.");
            //return nullptr;
        }

        float* boxes = interprete->typed_output_tensor<float>(0);  // TFLite_Detection_PostProcess:1
        float* classes = interprete->typed_output_tensor<float>(1);  // TFLite_Detection_PostProcess:2
        float* scores = interprete->typed_output_tensor<float>(2);  // TFLite_Detection_PostProcess:3 interprete->typed_output_tensor<float>(2)[0]
        int num_detections = static_cast<int>(interprete->typed_output_tensor<float>(3)[0]);  // Número de detecciones
        //float* outputTensor = interprete->typed_output_tensor<float>(0); // Identificador: 975


        int num_classes = interprete->tensor(interprete->outputs()[1])->dims->data[1];
        std::cout << "Número de clases: " << num_classes << std::endl;

        // Dibujar las cajas delimitadoras en la imagen original
        string lab = "Nada";
        vector<Resultados> resultadoEnv;


        float scaleX = static_cast<float>(copiaSize.cols) / inputMat.cols;
        float scaleY = static_cast<float>(copiaSize.rows) / inputMat.rows;

        int maximo = 0;

        string ruta = "";
        CascadeClassifier cascada = CascadeClassifier(ruta);

        vector<Rect> body;

        cascada.detectMultiScale(inputMat, body);

        for(int i = 0; i < body.size(); i++){

        }

        for (int i = 0; i < num_detections; i++) {
            if (scores[i] >= 0.5) {  // Umbral de confianza
                if(maximo >= 5) break;
                int ymin = static_cast<int>(boxes[i * 4] * 320 * scaleY );
                int xmin = static_cast<int>(boxes[i * 4 + 1] * 320 * scaleX);
                int ymax = static_cast<int>(boxes[i * 4 + 2] * 320 * scaleY);
                int xmax = static_cast<int>(boxes[i * 4 + 3] * 320 * scaleX);

                xmin = std::max(0, std::min(xmin, copiaSize.cols - 1));
                xmax = std::max(0, std::min(xmax, copiaSize.cols - 1));
                ymin = std::max(0, std::min(ymin, copiaSize.rows - 1));
                ymax = std::max(0, std::min(ymax, copiaSize.rows - 1));



                int class_id = static_cast<int>(classes[i]);
                std::string label = (class_id < cocoNames.size()) ? cocoNames[class_id] : "No se";
                float score = scores[i];

                //cv::rectangle(inputMat, cv::Point(xmin, ymin), cv::Point(xmax, ymax), cv::Scalar(0, 255, 0), 2);

                std::string label_text = label + ": " + std::to_string(score);
                lab = label;
                //cv::putText(inputMat, label_text, cv::Point(xmin, ymin - 10), cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 255, 0), 2);
                Resultados resultados;
                resultados.boundingBox.x = xmin;
                resultados.boundingBox.y = ymin;
                resultados.boundingBox.width = xmax - xmin;
                resultados.boundingBox.height = ymax - ymin;
                Categoria cate;
                cate.label = label;
                cate.score = score;
                resultados.categories.push_back(cate);
                resultadoEnv.push_back(resultados);
                maximo++;
            }
        }
        //return env->NewStringUTF(lab.c_str());

        // Referencias a las clases Java
        jclass arrayListClass = env->FindClass("java/util/ArrayList");
        jmethodID arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "()V");
        jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

        jclass resultsClass = env->FindClass("com/logic/tflitecapi/Results");
        jmethodID resultsConstructor = env->GetMethodID(resultsClass, "<init>", "()V");
        jfieldID boundingBoxField = env->GetFieldID(resultsClass, "boundingBoxes", "Landroid/graphics/RectF;");
        jfieldID categoriesField = env->GetFieldID(resultsClass, "categories", "Ljava/util/List;");

        jclass categoryClass = env->FindClass("com/logic/tflitecapi/Category");
        jmethodID categoryConstructor = env->GetMethodID(categoryClass, "<init>", "()V");
        jfieldID labelField = env->GetFieldID(categoryClass, "label", "Ljava/lang/String;");
        jfieldID scoreField = env->GetFieldID(categoryClass, "score", "F");

        jclass rectFClass = env->FindClass("android/graphics/RectF");
        jmethodID rectFConstructor = env->GetMethodID(rectFClass, "<init>", "(FFFF)V");

        // Crear el ArrayList para los resultados
        jobject resultsList = env->NewObject(arrayListClass, arrayListConstructor);

        for (const auto& result : resultadoEnv) {
            // Crear un objeto Results
            jobject resultsObject = env->NewObject(resultsClass, resultsConstructor);

            // Crear el objeto RectF para el bounding box
            jobject rectFObject = env->NewObject(rectFClass, rectFConstructor,
                                                 static_cast<jfloat>(result.boundingBox.x),
                                                 static_cast<jfloat>(result.boundingBox.y),
                                                 static_cast<jfloat>(result.boundingBox.x + result.boundingBox.width),
                                                 static_cast<jfloat>(result.boundingBox.y + result.boundingBox.height));
            env->SetObjectField(resultsObject, boundingBoxField, rectFObject);

            // Crear el ArrayList para las categorías
            jobject categoriesList = env->NewObject(arrayListClass, arrayListConstructor);

            for (const auto& category : result.categories) {
                // Crear un objeto Category
                jobject categoryObject = env->NewObject(categoryClass, categoryConstructor);

                // Asignar los valores de la categoría
                jstring label = env->NewStringUTF(category.label.c_str());
                env->SetObjectField(categoryObject, labelField, label);
                env->SetFloatField(categoryObject, scoreField, category.score);

                // Agregar la categoría al ArrayList
                env->CallBooleanMethod(categoriesList, arrayListAdd, categoryObject);

                // Liberar referencias locales
                env->DeleteLocalRef(categoryObject);
                env->DeleteLocalRef(label);
            }

            // Asignar la lista de categorías al objeto Results
            env->SetObjectField(resultsObject, categoriesField, categoriesList);

            // Agregar el objeto Results al ArrayList principal
            env->CallBooleanMethod(resultsList, arrayListAdd, resultsObject);

            // Liberar referencias locales
            env->DeleteLocalRef(resultsObject);
            env->DeleteLocalRef(categoriesList);
            env->DeleteLocalRef(rectFObject);
        }

        // Devolver el ArrayList
        return resultsList;

    }


    JNIEXPORT jboolean Java_com_logic_tflitecapi_MainActivity_CreateSession(JNIEnv* env, jobject /**/, jstring modelo, jstring labels, jboolean gpu){
        if(interprete == nullptr){
            const char* model = env->GetStringUTFChars(modelo, nullptr);
            const char* etiqueta = env->GetStringUTFChars(labels, nullptr);
            //const char* gpu = env->GetBoolea(GPU);
            //Path to class labels

            string labelsPath = etiqueta;
            cocoNames = getClassNames(labelsPath);
            const std::string modelPath = model;          // Path to TFLite model
            _model = tflite::FlatBufferModel::BuildFromFile(modelPath.c_str());
            tflite::ops::builtin::BuiltinOpResolver resolver;

            tflite::InterpreterBuilder builder(*_model, resolver);
            builder(&interprete);

            if(gpu == true){

                TfLiteGpuDelegateOptionsV2 options = TfLiteGpuDelegateOptionsV2Default();
                options.inference_priority1 = TFLITE_GPU_INFERENCE_PRIORITY_MIN_LATENCY;
                options.inference_priority2 = TFLITE_GPU_INFERENCE_PRIORITY_MAX_PRECISION;
                options.inference_priority3 = TFLITE_GPU_INFERENCE_PRIORITY_AUTO;
                options.inference_preference = TFLITE_GPU_INFERENCE_PREFERENCE_SUSTAINED_SPEED;
//                options.experimental_flags = TFLITE_GPU_EXPERIMENTAL_FLAGS_GL_ONLY;

                //options.is_precision_loss_allowed = 1;



                auto* gpu_delegate = TfLiteGpuDelegateV2Create(&options); //TFLGpuDelegateCreate(nullptr);
                //gpuDelegate.reset(TfLiteGpuDelegateV2Create(&options));

                if (!interprete) {
                    env->NewStringUTF("Error al crear el intérprete.");
                }

                if (interprete->ModifyGraphWithDelegate(gpu_delegate) != kTfLiteOk){
                    fprintf(stderr, "Error: No se pudo usar el delegado de GPU.\n");
                    LOGE("Error: No se pudo modificar el gráfico con el delegado de GPU.");
                    LOGE(TFLITE_VERSION_STRING);
                    TfLiteGpuDelegateV2Delete(gpu_delegate);
                    //return false;
                }

                if(interprete->AllocateTensors() != kTfLiteOk){
                    fprintf(stderr, "Fallo nuevamente :(");
                    return false;
                }
                return true;
            }

            if(interprete->AllocateTensors() != kTfLiteOk){
                fprintf(stderr, "Fallo nuevamente :(");
                return false;
            }

            return true;
        }
        return true;

    }


    JNICALL void Java_com_logic_tflitecapi_MainActivity_LiberarRecursos(JNIEnv* env, jobject /**/){
//        _model = nullptr;
//        gpuDelegate = nullptr;
//        interprete = nullptr;
        //std _model;
    }
}
