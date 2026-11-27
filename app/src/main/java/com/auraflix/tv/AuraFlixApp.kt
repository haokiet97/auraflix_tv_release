package com.auraflix.tv

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.auraflix.tv.core.cast.CastLinkManager
import com.auraflix.tv.core.net.HttpClient
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.core.repo.UserDataRepository
import com.auraflix.tv.core.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service locator tối giản — tránh Hilt/KSP để build nhanh và APK nhỏ.
 */
object Graph {

    /** Scope sống cùng process, dùng cho các tác vụ không được huỷ khi đổi màn hình. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var http: HttpClient
        private set
    lateinit var movies: MovieRepository
        private set
    lateinit var userData: UserDataRepository
        private set
    lateinit var updates: UpdateManager
        private set
    lateinit var cast: CastLinkManager
        private set
    lateinit var app: AuraFlixApp
        private set

    fun init(application: AuraFlixApp) {
        app = application
        http = HttpClient(application)
        movies = MovieRepository(http)
        userData = UserDataRepository(application)
        updates = UpdateManager(application, http)
        cast = CastLinkManager(application, movies, appScope)
        if (BuildConfig.DEBUG) {
            com.auraflix.tv.core.cast.DebugLog.install { Log.d("AuraCast", it) }
        }
        cast.start()
        appScope.launch {
            userData.settings.collect { movies.baseUrl = it.baseUrl }
        }
    }
}

class AuraFlixApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    /**
     * Đăng ký tường minh fetcher mạng của Coil (thay vì dựa vào ServiceLoader)
     * để ảnh poster vẫn tải được sau khi R8 rút gọn ở bản release.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Graph.http.okHttp }))
            }
            .build()
}
