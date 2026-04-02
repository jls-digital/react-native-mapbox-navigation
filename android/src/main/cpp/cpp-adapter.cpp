#include <jni.h>
#include "jlsdigital_reactnativemapboxnavigationOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::jlsdigital_reactnativemapboxnavigation::initialize(vm);
}
