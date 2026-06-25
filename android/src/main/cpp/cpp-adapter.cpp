#include <jni.h>
#include "ch_jls_reactnative_mapboxnavigationOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::ch_jls_reactnative_mapboxnavigation::initialize(vm);
}
