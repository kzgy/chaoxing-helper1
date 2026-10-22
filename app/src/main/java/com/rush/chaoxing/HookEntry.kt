package com.rush.chaoxing

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.*
import com.highcapable.yukihookapi.hook.log.YLog
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
        val handledActiveIds = ConcurrentHashMap.newKeySet<String>()
    }

    override fun onInit() {
        configs {
            isDebug = true
        }
        YLog.Configs.tag = "ChaoxingRush"
    }

    override fun onHook() = encase {
        loadApp(name = "com.chaoxing.mobile") {
            YLog.info("已成功挂载到学习通进程: $processName")

            val targetClassName = "okhttp3.OkHttpClient" + "$" + "Builder"
            targetClassName.toClass().method {
                name = "build"
            }.hook {
                before {
                    val builder = instance as OkHttpClient.Builder
                    builder.addInterceptor(RushInterceptor())
                    YLog.info("抢答拦截器已注入")
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
                YLog.error("解析活动列表失败", e)
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
                YLog.info("捕获到抢答活动: $activeId，将在 ${randomDelay}ms 后抢答")

                // 在独立子线程等待并执行，不依赖主线程 Looper
                Thread {
                    try {
                        Thread.sleep(randomDelay)
                        doRush(activeId, templateRequest)
                    } catch (e: Throwable) {
                        YLog.error("抢答定时执行异常", e)
                    }
                }.start()
            }
        }
    }

    private fun doRush(activeId: String, templateRequest: Request) {
        try {
            val targetUrl = "https://mobilelearn.chaoxing.com/widget/responder/join?activeId=$activeId"
            val rushRequest = Request.Builder()
                .url(targetUrl)
                .headers(templateRequest.headers)
                .get()
                .build()

            val resp = OkHttpClient().newCall(rushRequest).execute()
            YLog.info("抢答请求已发出，响应码: ${resp.code}")
            resp.close()
        } catch (e: Throwable) {
            YLog.error("抢答请求发送异常", e)
        }
    }
}
