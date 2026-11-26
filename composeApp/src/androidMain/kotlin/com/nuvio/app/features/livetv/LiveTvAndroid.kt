package com.nuvio.app.features.livetv

import android.content.Context
import com.nuvio.iptv.IptvEngine
import com.nuvio.iptv.net.IptvHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** App-wide Live TV state for Android. Initialised from MainActivity. */
object LiveTvAndroid {
    @Volatile
    private var instance: IptvRepository? = null

    fun initialize(context: Context) {
        if (instance != null) return
        synchronized(this) {
            if (instance != null) return
            // Own client: keeps the provider's User-Agent and keeps credential-bearing URLs out of shared logging.
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.MINUTES)
                .build()
            val repo = IptvRepository(
                context = context.applicationContext,
                store = LiveTvStore(context.applicationContext),
                engine = IptvEngine(IptvHttp(client)),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
            // Phone app: one Live TV library shared by all profiles for now.
            repo.selectProfile(0)
            instance = repo
        }
    }

    val repository: IptvRepository
        get() = instance ?: error("LiveTvAndroid.initialize() was not called")
}
