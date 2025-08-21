cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
project("com.logic.tflitecapi")

# Configuración del NDK y la toolchain
if (NOT ANDROID_ABI)
    message(FATAL_ERROR "ANDROID_ABI not defined! Please specify target architecture: armeabi-v7a, arm64-v8a, x86, or x86_64.")
endif() # Configura el nivel de API nativo de Android set(ANDROID_NATIVE_API_LEVEL 21)


# Configuraciones de OpenCV
set(OpenCV_STATIC ON)
set(OpenCV_DIR /home/jorge/Libs/OpenCV-android-sdk/sdk/native/jni)
find_package(OpenCV REQUIRED)
include_directories(/home/jorge/Libs/OpenCV-android-sdk/sdk/native/jni/include)

# Configuración de TensorFlow Lite
#set(TFLITE_INCLUDE_DIR "/home/jorgelituma/Librerias/intento90/tensorflow")
#set(TFLITE_INCLUDE_DIR "/home/jorgelituma/AndroidStudioProjects/TfLiteCAPI/app/src/main/cpp/tensorflow")
set(TFLITE_INCLUDE_DIR /home/jorge/Libs/tensorflow/tensorflow)
set(TFLITE_INCLUDE_DIR /home/jorge/Libs/tensorflow)
set(TFLITE_LIB_DIR "/home/jorge/StudioProjects/TfLiteCAPI/app/src/main/jniLibs/${ANDROID_ABI}")
include_directories(${TFLITE_INCLUDE_DIR})

#set(ENABLE_INTERNAL_FLATBUFFERS ON)
#set(FLATBUFFERS_INCLUDE_DIR "/home/jorgelituma/AndroidStudioProjects/TfLiteCAPI/app/src/main/cpp/flatbuffers")
set(FLATBUFFERS_INCLUDE_DIR /home/jorge/Libs/flatbuffers/include)
include_directories(${FLATBUFFERS_INCLUDE_DIR})


# Añadir las bibliotecas de TensorFlow Lite

add_library(tflite SHARED IMPORTED)
set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
        ${TFLITE_LIB_DIR}/libtensorflowlite.so)


add_library(tflitegpu SHARED IMPORTED)
set_target_properties(tflitegpu PROPERTIES IMPORTED_LOCATION
        ${TFLITE_LIB_DIR}/libtensorflowlite_gpu_delegate.so)


# Añadir la librería de tu proyecto
add_library(tflitecapi SHARED native-lib.cpp)

# Encontrar las bibliotecas del sistema
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)

# Vincular las bibliotecas
target_link_libraries(tflitecapi
        tflite
        tflitegpu
        ${OpenCV_LIBS}
        ${jnigraphics-lib}
        ${log-lib}
#        -latomic
#        -lpthread
#        -ldl
)

