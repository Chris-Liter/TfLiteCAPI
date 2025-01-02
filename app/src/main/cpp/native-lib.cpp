#include <jni.h>
#include <string>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "android/bitmap.h"
#include <fstream>



#include <opencv2/opencv.hpp>


#include <tensorflow/lite/model.h>
#include <tensorflow/lite/kernels/register.h>
#include <tensorflow/lite/interpreter.h>
#include <tensorflow/lite/context.h>
#include <tensorflow/lite/string_util.h>
#include <tensorflow/lite/delegates/gpu/delegate.h>
#include <tensorflow/lite/builtin_op_data.h>
#include <tensorflow/lite/model.h>

using namespace std;
using namespace cv;

unique_ptr<tflite::FlatBufferModel> _model;
unique_ptr<tflite::Interpreter> interprete;
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


        bitmapToMat(env, bitmapIn, inputMat, false);

        resize(inputMat, inputMat, Size(224,224));
        inputMat.convertTo(inputMat, CV_32F);
        inputMat /= 255.0;

        Mat rgbImage;
        cvtColor(inputMat, inputMat, COLOR_BGR2RGB);


        float * input = interprete->typed_input_tensor<float>(0);

        memcpy(input, inputMat.data, inputMat.total() * inputMat.elemSize());

        if(interprete->Invoke() != kTfLiteOk){
            fprintf(stderr, "Error");
            return nullptr;
        }

        float* outputTensor = interprete->typed_output_tensor<float>(0);
        int topClass = 0;
        float maxProb = outputTensor[0];
        for(int i = 1; i < 1001; ++i){
            if(outputTensor[i] > maxProb){
                maxProb = outputTensor[i];
                topClass = i;
            }
        }

        string label = cocoNames[topClass];

        return env->NewStringUTF(label.c_str());
    }


    JNIEXPORT jboolean Java_com_logic_tflitecapi_MainActivity_CreateSession(JNIEnv* env, jobject /**/, jstring modelo, jstring labels, jboolean gpu){
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

            gpuDelegate.reset(TfLiteGpuDelegateV2Create(&options));

            auto* gpu_delegate = TfLiteGpuDelegateV2Create(&options);
//
            if (interprete->ModifyGraphWithDelegate(gpuDelegate.get()) != kTfLiteOk){
                fprintf(stderr, "Error: No se pudo usar el delegado de GPU.\n");
                TfLiteGpuDelegateV2Delete(gpu_delegate);
                //return;
                return false;
            }

            if(interprete->AllocateTensors() != kTfLiteOk){
                fprintf(stderr, "Fallo nuevamente :(");
                //exit(-1);
                return false;
            }
            return true;
        }

        if(interprete->AllocateTensors() != kTfLiteOk){
            fprintf(stderr, "Fallo nuevamente :(");
            //exit(-1);
            return false;
        }

        return true;

    }



    JNICALL void Java_com_logic_MainActivity_LiberarRecursos(){

    }
}

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