package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

import com.margelo.nitro.ch.jls.reactnative.mapboxnavigation.views.HybridReactNativeMapboxNavigationManager

class ReactNativeMapboxNavigationPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return null
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider { HashMap() }
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(HybridReactNativeMapboxNavigationManager())
    }

    companion object {
        init {
            System.loadLibrary("ch_jls_reactnative_mapboxnavigation")
        }
    }
}
