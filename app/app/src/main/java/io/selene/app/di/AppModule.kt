package io.selene.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.selene.app.data.db.MediaDao
import io.selene.app.data.db.SeleneDatabase
import io.selene.app.data.db.TaskDao
import io.selene.app.data.db.VariantDao
import io.selene.app.downloading.DashDownloader
import io.selene.app.downloading.DownloadEngine
import io.selene.app.downloading.HlsDownloader
import io.selene.app.downloading.SegmentDownloader
import io.selene.app.downloading.YtDlpDownloader
import io.selene.app.resolver.DashResolver
import io.selene.app.resolver.DirectUrlResolver
import io.selene.app.resolver.ExternalResolver
import io.selene.app.resolver.HlsResolver
import io.selene.app.resolver.MediaResolver
import io.selene.app.resolver.ResolverRegistry
import io.selene.app.resolver.YtDlpResolver
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SeleneDatabase =
        Room.databaseBuilder(context, SeleneDatabase::class.java, "selene.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun mediaDao(database: SeleneDatabase): MediaDao = database.mediaDao()

    @Provides
    fun variantDao(database: SeleneDatabase): VariantDao = database.variantDao()

    @Provides
    fun taskDao(database: SeleneDatabase): TaskDao = database.taskDao()

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun downloadEngine(client: OkHttpClient): DownloadEngine = DownloadEngine(client)

    @Provides
    @Singleton
    fun segmentDownloader(engine: DownloadEngine): SegmentDownloader = SegmentDownloader(engine)

    @Provides
    @Singleton
    fun hlsDownloader(client: OkHttpClient, segments: SegmentDownloader): HlsDownloader =
        HlsDownloader(client, segments)

    @Provides
    @Singleton
    fun dashDownloader(
        client: OkHttpClient,
        engine: DownloadEngine,
        segments: SegmentDownloader,
    ): DashDownloader = DashDownloader(client, engine, segments)

    @Provides
    @Singleton
    fun ytDlpDownloader(): YtDlpDownloader = YtDlpDownloader()

    /**
     * Résolveurs disponibles, du plus spécifique au plus général : HLS avant l'URL directe,
     * qui accepterait n'importe quelle adresse `http(s)`. L'ensemble est extensible sans
     * toucher au reste de l'application (DASH, RSS, pont externe).
     */
    @Provides
    @Singleton
    fun resolverRegistry(
        direct: DirectUrlResolver,
        hls: HlsResolver,
        dash: DashResolver,
        external: ExternalResolver,
        ytDlp: YtDlpResolver,
    ): ResolverRegistry {
        val resolvers: Set<MediaResolver> = setOf(direct, hls, dash, external, ytDlp)
        return ResolverRegistry(resolvers)
    }
}
