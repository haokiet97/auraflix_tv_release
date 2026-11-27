package com.auraflix.mobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.auraflix.tv.core.cast.CastClient
import com.auraflix.tv.core.cast.CastStore
import com.auraflix.tv.core.cast.TvRemote
import com.auraflix.tv.core.net.HttpClient
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.core.repo.UserDataRepository
import com.auraflix.tv.core.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service locator tối giản cho bản Mobile. Dùng chung tầng dữ liệu và ViewModel
 * với app TV (module :shared) nhưng có kênh cập nhật OTA riêng.
 */
object MobileGraph {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var http: HttpClient
        private set
    lateinit var movies: MovieRepository
        private set
    lateinit var userData: UserDataRepository
        private set
    lateinit var updates: UpdateManager
        private set
    lateinit var castClient: CastClient
        private set
    lateinit var tvRemote: TvRemote
        private set
    lateinit var app: MobileApp
        private set

    fun init(application: MobileApp) {
        app = application
        http = HttpClient(application)
        movies = MovieRepository(http)
        userData = UserDataRepository(application)
        updates = UpdateManager(
            context = application,
            http = http,
            latestReleaseUrl = UpdateManager.MOBILE_RELEASE_URL,
            apkNamePrefix = "AuraFlixMobile",
        )
        castClient = CastClient(http.okHttp)
        tvRemote = TvRemote(castClient, CastStore(application), appScope)
        appScope.launch {
            userData.settings.collect { movies.baseUrl = it.baseUrl }
        }
    }
}

class MobileApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        MobileGraph.init(this)
    }

    /**
     * Đăng ký tường minh fetcher mạng của Coil để ảnh poster vẫn tải được
     * sau khi R8 rút gọn ở bản release.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { MobileGraph.http.okHttp }))
            }
            .build()
}
