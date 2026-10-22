package com.rush.chaoxing

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    companion object {
        const val TAG = "ChaoxingRush"
        val handledActiveIds = ConcurrentHashMap.newKeySet<String>()
        val mainHandler = Handler(Looper.getMainLooper())
    }
    override fun onInit() = configs { isDebug = false }
    override fun onHook() = encase {
        loadApp(name = "com.chaoxing.mobile") {
            "okhttp3.OkHttpClient`$Builder".toClass().method { name = "build" }.hook {
                before {
                    val builder = instance as OkHttpClient.Builder
                    builder.addInterceptor(RushInterceptor())
                    Log.i(TAG, "抢答拦截器已注入")
                }
            }
        }
    }
}

class RushInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toString()
        if (url.contains("taskactivelist")) {
            try {
                val peek = response.peekBody(1024 * 512)
                val json = peek.string()
                parseActiveList(json, request)
            } catch (e: Throwable) {
                Log.e(HookEntry.TAG, "解析失败", e)
            }
        }
        return response
    }

    private fun parseActiveList(jsonStr: String, templateRequest: Request) {
        val root = JSONObject(jsonStr)
        val activeList = root.optJSONArray("activeList") ?: return
        for (i in 0 until activeList.length()) {
            val item = activeList.getJSONObject(i)
            val activeType = item.optInt("activeType")
            val status = item.optInt("status")
            val activeId = item.optString("id")
            if (activeType == 4 && status == 1 && !HookEntry.handledActiveIds.contains(activeId)) {
                HookEntry.handledActiveIds.add(activeId)
                val randomDelay = Random.nextLong(200, 380)
                HookEntry.mainHandler.postDelayed({ doRush(activeId, templateRequest) }, randomDelay)
            }
        }
    }

    private fun doRush(activeId: String, templateRequest: Request) {
        Thread {
            try {
                val targetUrl = "https://mobilelearn.chaoxing.com/widget/responder/join?activeId=$activeId"
                val rushRequest = Request.Builder().url(targetUrl).headers(templateRequest.headers).get().build()
                OkHttpClient().newCall(rushRequest).execute().close()
            } catch (e: Throwable) {
                Log.e(HookEntry.TAG, "请求异常", e)
            }
        }.start()
    }
}
