package ir.mmd.dodota.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import ir.mmd.dodota.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "browser_download_url")
    val browserDownloadUrl: String = "",
    @Json(name = "content_type")
    val contentType: String = ""
)

data class GitHubReleaseDto(
    @Json(name = "tag_name")
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @Json(name = "published_at")
    val publishedAt: String? = null,
    @Json(name = "html_url")
    val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

data class GitHubTagDto(
    val name: String = ""
)

data class GitHubRepoDto(
    @Json(name = "full_name")
    val fullName: String = "",
    @Json(name = "html_url")
    val htmlUrl: String = "",
    @Json(name = "default_branch")
    val defaultBranch: String = "main"
)

data class GitHubWorkflowRunDto(
    val id: Long = 0L,
    val name: String? = null,
    @Json(name = "html_url")
    val htmlUrl: String = "",
    @Json(name = "run_number")
    val runNumber: Int = 0,
    @Json(name = "created_at")
    val createdAt: String? = null
)

data class GitHubWorkflowRunsDto(
    @Json(name = "workflow_runs")
    val workflowRuns: List<GitHubWorkflowRunDto> = emptyList()
)

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubReleaseDto>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 5
    ): Response<List<GitHubReleaseDto>>

    @GET("repos/{owner}/{repo}/tags")
    suspend fun getTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 3
    ): Response<List<GitHubTagDto>>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepoInfo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepoDto>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getSuccessfulRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("status") status: String = "success",
        @Query("per_page") perPage: Int = 1
    ): Response<GitHubWorkflowRunsDto>
}

data class GitHubReleaseInfo(
    val repoSlug: String,
    val versionTag: String,
    val cleanVersion: String,
    val title: String,
    val notes: String,
    val publishedPersianDate: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long
)

sealed interface GitHubUpdateResult {
    data object Idle : GitHubUpdateResult
    data object Loading : GitHubUpdateResult
    data class UpdateAvailable(val release: GitHubReleaseInfo) : GitHubUpdateResult
    data class UpToDate(val release: GitHubReleaseInfo) : GitHubUpdateResult
    data class TagOnlyInfo(
        val repoSlug: String,
        val latestTag: String,
        val isNewer: Boolean,
        val releasesPageUrl: String,
        val actionsUrl: String
    ) : GitHubUpdateResult
    data class NoReleaseYet(
        val repoSlug: String,
        val latestRunNumber: Int?,
        val latestRunDatePersian: String?,
        val latestRunUrl: String?,
        val releasesPageUrl: String,
        val actionsUrl: String
    ) : GitHubUpdateResult
    data class Error(val message: String) : GitHubUpdateResult
}

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val downloadId: Long,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ApkDownloadState
    data class Completed(val fileName: String) : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

object GitHubUpdateHelper {

    const val PROJECT_GITHUB_REPO = "alovemamad/Dodota"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Dodota-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val apiService: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    fun effectiveRepoSlug(savedSlug: String = ""): String {
        val cleanedSaved = normalizeRepoSlug(savedSlug)
        if (!cleanedSaved.isNullOrBlank()) return cleanedSaved
        val cleanedDefault = normalizeRepoSlug(BuildConfig.DEFAULT_GITHUB_REPO)
        if (!cleanedDefault.isNullOrBlank()) return cleanedDefault
        return PROJECT_GITHUB_REPO
    }

    fun normalizeRepoSlug(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        val withoutProtocol = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .trim('/')

        val segments = withoutProtocol.split("/").filter { it.isNotBlank() }
        if (segments.size < 2) return null

        val owner = segments[0].trim()
        val repo = segments[1].trim().removeSuffix(".git")
        val validRegex = Regex("^[A-Za-z0-9_.-]+$")
        if (!validRegex.matches(owner) || !validRegex.matches(repo)) return null

        return "$owner/$repo"
    }

    fun cleanVersionName(tagOrVersion: String): String {
        return tagOrVersion.trim().removePrefix("v").removePrefix("V")
    }

    fun compareSemanticVersions(remoteVersion: String, currentVersion: String): Int {
        val remoteParts = extractVersionSegments(remoteVersion)
        val currentParts = extractVersionSegments(currentVersion)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }

    private fun extractVersionSegments(version: String): List<Int> {
        val cleaned = cleanVersionName(version)
            .substringBefore('-')
            .substringBefore('+')
        return cleaned.split('.').map { segment ->
            segment.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    fun formatIsoDateToPersian(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(isoTimestamp) ?: return ""
            PersianDateHelper.formatToPersianDate(date.time)
        } catch (_: Exception) {
            ""
        }
    }

    fun formatFileSizeMb(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f مگابایت", mb)
    }

    suspend fun checkForUpdates(rawSlug: String = effectiveRepoSlug()): GitHubUpdateResult = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoSlug(rawSlug) ?: PROJECT_GITHUB_REPO
        val parts = normalized.split("/")
        val owner = parts[0]
        val repo = parts[1]
        val releasesUrl = "https://github.com/$normalized/releases"
        val actionsUrl = "https://github.com/$normalized/actions"

        try {
            val latestResponse = apiService.getLatestRelease(owner, repo)
            val releaseDto: GitHubReleaseDto? = when {
                latestResponse.isSuccessful -> latestResponse.body()
                latestResponse.code() == 404 -> {
                    val listResp = apiService.getReleases(owner, repo)
                    if (listResp.isSuccessful) {
                        listResp.body()?.firstOrNull { !it.draft }
                    } else null
                }
                latestResponse.code() == 403 || latestResponse.code() == 429 -> {
                    return@withContext GitHubUpdateResult.Error("محدودیت درخواست گیت‌هاب موقتاً فعال شده است. لطفاً چند دقیقه دیگر دوباره تلاش کنید.")
                }
                else -> {
                    return@withContext GitHubUpdateResult.Error("خطا در ارتباط با گیت‌هاب (کد ${PersianNumberFormatter.formatDigits(latestResponse.code())})")
                }
            }

            if (releaseDto != null && releaseDto.tagName.isNotBlank()) {
                val apkAsset = releaseDto.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true) && it.name.contains("release", ignoreCase = true)
                } ?: releaseDto.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }

                val cleanRemote = cleanVersionName(releaseDto.tagName)
                val info = GitHubReleaseInfo(
                    repoSlug = normalized,
                    versionTag = releaseDto.tagName,
                    cleanVersion = cleanRemote,
                    title = releaseDto.name?.takeIf { it.isNotBlank() } ?: releaseDto.tagName,
                    notes = releaseDto.body?.trim().orEmpty(),
                    publishedPersianDate = formatIsoDateToPersian(releaseDto.publishedAt),
                    releasePageUrl = releaseDto.htmlUrl.ifBlank { releasesUrl },
                    apkDownloadUrl = apkAsset?.browserDownloadUrl?.takeIf { it.isNotBlank() },
                    apkFileName = apkAsset?.name?.takeIf { it.isNotBlank() },
                    apkSizeBytes = apkAsset?.size ?: 0L
                )

                val isNewer = compareSemanticVersions(cleanRemote, BuildConfig.VERSION_NAME) > 0
                return@withContext if (isNewer) {
                    GitHubUpdateResult.UpdateAvailable(info)
                } else {
                    GitHubUpdateResult.UpToDate(info)
                }
            }

            val repoResp = apiService.getRepoInfo(owner, repo)
            if (repoResp.code() == 404) {
                return@withContext GitHubUpdateResult.Error("مخزن «$normalized» در گیت‌هاب در دسترس نیست یا خصوصی (Private) است.")
            }
            if (!repoResp.isSuccessful) {
                return@withContext GitHubUpdateResult.Error("خطا در بررسی مخزن گیت‌هاب (کد ${PersianNumberFormatter.formatDigits(repoResp.code())})")
            }

            val tagsResp = apiService.getTags(owner, repo)
            val latestTag = if (tagsResp.isSuccessful) tagsResp.body()?.firstOrNull()?.name else null
            if (!latestTag.isNullOrBlank()) {
                val isNewer = compareSemanticVersions(latestTag, BuildConfig.VERSION_NAME) > 0
                return@withContext GitHubUpdateResult.TagOnlyInfo(
                    repoSlug = normalized,
                    latestTag = latestTag,
                    isNewer = isNewer,
                    releasesPageUrl = releasesUrl,
                    actionsUrl = actionsUrl
                )
            }

            val runsResp = apiService.getSuccessfulRuns(owner, repo)
            val latestRun = if (runsResp.isSuccessful) runsResp.body()?.workflowRuns?.firstOrNull() else null
            GitHubUpdateResult.NoReleaseYet(
                repoSlug = normalized,
                latestRunNumber = latestRun?.runNumber,
                latestRunDatePersian = formatIsoDateToPersian(latestRun?.createdAt),
                latestRunUrl = latestRun?.htmlUrl,
                releasesPageUrl = releasesUrl,
                actionsUrl = actionsUrl
            )
        } catch (_: IOException) {
            GitHubUpdateResult.Error("اتصال اینترنت برقرار نیست یا دسترسی به گیت‌هاب با خطا مواجه شد.")
        } catch (e: Exception) {
            GitHubUpdateResult.Error("خطای غیرمنتظره در بررسی بروزرسانی: ${e.localizedMessage ?: "نامشخص"}")
        }
    }

    fun startDirectApkDownload(
        context: Context,
        downloadUrl: String,
        versionTag: String,
        preferredFileName: String?
    ): Pair<Long, String>? {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
            val safeVersion = cleanVersionName(versionTag).replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = preferredFileName?.takeIf { it.endsWith(".apk", ignoreCase = true) }
                ?: "Dodota-v$safeVersion.apk"

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("بروزرسانی دودوتا ($versionTag)")
                setDescription("در حال دانلود نسخه جدید برنامه...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }
            val downloadId = dm.enqueue(request)
            downloadId to fileName
        } catch (_: Exception) {
            null
        }
    }

    fun observeDownloadProgress(
        context: Context,
        downloadId: Long,
        fileName: String
    ): Flow<ApkDownloadState> = flow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            emit(ApkDownloadState.Failed("سرویس مدیریت دانلود سیستم در دسترس نیست."))
            return@flow
        }

        val query = DownloadManager.Query().setFilterById(downloadId)
        while (true) {
            val cursor = dm.query(query)
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                emit(ApkDownloadState.Failed("دانلود لغو شد یا پیدا نشد."))
                return@flow
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    emit(ApkDownloadState.Completed(fileName))
                    return@flow
                }
                DownloadManager.STATUS_FAILED -> {
                    emit(ApkDownloadState.Failed("دانلود فایل با خطا مواجه شد. لطفاً اینترنت خود را بررسی کنید."))
                    return@flow
                }
                else -> {
                    val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                    emit(ApkDownloadState.Downloading(downloadId, percent, downloaded, total))
                }
            }
            delay(350)
        }
    }.flowOn(Dispatchers.IO)

    fun openSystemDownloads(context: Context) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "پوشه دانلودها از نوار اعلان قابل دسترسی است.", Toast.LENGTH_LONG).show()
        }
    }

    fun openUrlInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "مرورگر برای باز کردن لینک یافت نشد.", Toast.LENGTH_LONG).show()
        }
    }
}
