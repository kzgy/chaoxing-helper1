package com.rush.chaoxing

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.*
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
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
                    try {
                        val hostClassLoader = instance.javaClass.classLoader ?: appClassLoader ?: return@before
                        injectDynamicInterceptor(instance, hostClassLoader)
                    } catch (e: Throwable) {
                        YLog.error("拦截器挂载异常", e)
                    }
                }
            }
        }
    }

    private fun injectDynamicInterceptor(builderInstance: Any, hostClassLoader: ClassLoader) {
        val interceptorClass = hostClassLoader.loadClass("okhttp3.Interceptor")

        // 使用宿主 ClassLoader 动态创建 Interceptor 代理
        val proxyInterceptor = Proxy.newProxyInstance(
            hostClassLoader,
            arrayOf(interceptorClass)
        ) { _, method, args ->
            if (method.name == "intercept" && args != null && args.isNotEmpty()) {
                val chain = args[0]
                try {
                    return@newProxyInstance handleIntercept(chain)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
            if (method.name == "equals") return@newProxyInstance args?.get(0) === builderInstance
            if (method.name == "hashCode") return@newProxyInstance 114514
            if (method.name == "toString") return@newProxyInstance "DynamicRushInterceptor"
            null
        }

        // builder.addInterceptor(proxyInterceptor)
        val addInterceptorMethod = builderInstance.javaClass.getMethod("addInterceptor", interceptorClass)
        addInterceptorMethod.invoke(builderInstance, proxyInterceptor)
        YLog.info("抢答拦截器已动态注入 OkHttpClient")
    }

    private fun handleIntercept(chain: Any): Any {
        val requestMethod = chain.javaClass.getMethod("request")
        val request = requestMethod.invoke(chain) ?: throw IllegalStateException("Request is null")

        val proceedMethod = chain.javaClass.getMethod("proceed", request.javaClass)
        val response = proceedMethod.invoke(chain, request) ?: throw IllegalStateException("Response is null")

        try {
            val urlObj = request.javaClass.getMethod("url").invoke(request)
            val url = urlObj?.toString() ?: ""

            if (url.contains("taskactivelist")) {
                YLog.info("捕获到课堂活动列表请求: $url")
                val peekBodyMethod = response.javaClass.getMethod("peekBody", Long::class.javaPrimitiveType)
                val responseBody = peekBodyMethod.invoke(response, 512L * 1024L)
                val stringMethod = responseBody.javaClass.getMethod("string")
                val jsonStr = stringMethod.invoke(responseBody) as String

                val headersMap = extractHeaders(request)
                parseActiveList(jsonStr, headersMap)
            }
        } catch (e: Throwable) {
            YLog.error("活动数据拦截解析异常", e)
        }

        return response
    }

    private fun extractHeaders(request: Any): Map<String, String> {
        val headerMap = HashMap<String, String>()
        try {
            val headersObj = request.javaClass.getMethod("headers").invoke(request) ?: return headerMap
            val namesMethod = headersObj.javaClass.getMethod("names")
            val names = namesMethod.invoke(headersObj) as? Set<*> ?: return headerMap
            val getHeaderMethod = request.javaClass.getMethod("header", String::class.java)

            for (name in names) {
                val key = name.toString()
                val value = getHeaderMethod.invoke(request, key) as? String
                if (value != null) {
                    headerMap[key] = value
                }
            }
        } catch (e: Throwable) {
            YLog.error("提取请求头失败", e)
        }
        return headerMap
    }

    private fun parseActiveList(jsonStr: String, headersMap: Map<String, String>) {
        val root = JSONObject(jsonStr)
        val activeList = root.optJSONArray("activeList") ?: return

        for (i in 0 until activeList.length()) {
            val item = activeList.getJSONObject(i)
            val activeType = item.optInt("activeType")
            val status = item.optInt("status")
            val activeId = item.optString("id")

            // activeType == 4: 抢答活动; status == 1: 进行中
            if (activeType == 4 && status == 1 && !handledActiveIds.contains(activeId)) {
                handledActiveIds.add(activeId)

                val randomDelay = Random.nextLong(200, 380)
                YLog.info("捕获到抢答活动 ID: $activeId，将在 ${randomDelay}ms 后抢答")

                Thread {
                    try {
                        Thread.sleep(randomDelay)
                        doRush(activeId, headersMap)
                    } catch (e: Throwable) {
                        YLog.error("抢答定时线程执行异常", e)
                    }
                }.start()
            }
        }
    }

    private fun doRush(activeId: String, headersMap: Map<String, String>) {
        try {
            val targetUrl = "https://mobilelearn.chaoxing.com/widget/responder/join?activeId=$activeId"
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                instanceFollowRedirects = true
                for ((k, v) in headersMap) {
                    // 过滤掉自管理字段，保证编码透明解压
                    if (!k.equals("Host", ignoreCase = true) &&
                        !k.equals("Content-Length", ignoreCase = true) &&
                        !k.equals("Accept-Encoding", ignoreCase = true)
                    ) {
                        setRequestProperty(k, v)
                    }
                }
            }

            val respCode = conn.responseCode
            val respText = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Throwable) {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            YLog.info("抢答发包完成 -> 响应码: $respCode, 返回结果: $respText")
            conn.disconnect()
        } catch (e: Throwable) {
            YLog.error("抢答发包请求异常", e)
        }
    }
}
