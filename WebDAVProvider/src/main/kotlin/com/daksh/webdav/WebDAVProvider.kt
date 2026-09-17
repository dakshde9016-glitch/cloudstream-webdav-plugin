package com.daksh.webdav

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory

class WebDAVProvider : MainAPI() {
    override var name = "WebDAV Drive"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var hasMainPage = true
    override var hasQuickSearch = false

    override var mainUrl: String
        get() = WebDAVPlugin.getServerUrl().ifBlank { "https://webdav.local" }
        set(_) {}

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "flv", "m4v", "ts", "wmv", "3gp", "mpg", "mpeg", "m2ts"
        )
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    data class WebDAVResource(
        val href: String,
        val displayName: String,
        val isDirectory: Boolean,
        val contentType: String?
    )

    private fun getAuthHeader(): String? {
        val user = WebDAVPlugin.getUsername()
        val pass = WebDAVPlugin.getPassword()
        if (user.isEmpty() && pass.isEmpty()) return null
        return Credentials.basic(user, pass)
    }

    // ==========================================
    // Network & Directory Traversal
    // ==========================================

    private suspend fun propfind(url: String, depth: Int = 1): String = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="utf-8" ?>
            |<D:propfind xmlns:D="DAV:">
            |  <D:allprop/>
            |</D:propfind>""".trimMargin()

        val reqBuilder = Request.Builder()
            .url(url)
            .method("PROPFIND", xmlBody.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
            .header("Depth", depth.toString())

        getAuthHeader()?.let { auth ->
            reqBuilder.header("Authorization", auth)
        }

        val request = reqBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throw IOException("PROPFIND failed with HTTP ${response.code}: ${response.message}")
            }
            response.body?.string().orEmpty()
        }
    }

    // ==========================================
    // Robust XML Parser for <d:multistatus>
    // ==========================================

    private fun parseWebDAVXml(xml: String): List<WebDAVResource> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            try {
                setFeature("http://xml.org/sax/features/namespaces", false)
                setFeature("http://xml.org/sax/features/validation", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {
            }
        }

        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()

        val responseNodes = doc.getElementsByTagName("*")
        val results = mutableListOf<WebDAVResource>()

        for (i in 0 until responseNodes.length) {
            val node = responseNodes.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            val nodeLocalName = node.nodeName.substringAfter(':').lowercase()
            if (nodeLocalName != "response") continue

            val responseEl = node as Element
            val href = findFirstTagText(responseEl, "href")?.trim() ?: continue

            val propEl = findFirstTagElement(responseEl, "prop")
            var isCollection = false
            var contentType: String? = null
            var displayName: String? = null

            if (propEl != null) {
                val resourceTypeEl = findFirstTagElement(propEl, "resourcetype")
                if (resourceTypeEl != null) {
                    isCollection = findFirstTagElement(resourceTypeEl, "collection") != null ||
                            resourceTypeEl.textContent.contains("collection", ignoreCase = true)
                }
                contentType = findFirstTagText(propEl, "getcontenttype")?.trim()
                displayName = findFirstTagText(propEl, "displayname")?.trim()
            }

            if (!isCollection && href.endsWith('/')) {
                isCollection = true
            }

            val computedName = if (!displayName.isNullOrBlank()) {
                displayName
            } else {
                val decoded = try {
                    URLDecoder.decode(href.trimEnd('/'), "UTF-8")
                } catch (_: Exception) {
                    href.trimEnd('/')
                }
                decoded.substringAfterLast('/').ifBlank { decoded }
            }

            results.add(
                WebDAVResource(
                    href = href,
                    displayName = computedName,
                    isDirectory = isCollection,
                    contentType = contentType
                )
            )
        }

        return results
    }

    private fun findFirstTagElement(parent: Element, targetName: String): Element? {
        val target = targetName.lowercase()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val name = child.nodeName.substringAfter(':').lowercase()
                if (name == target) return child
            }
        }
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val match = findFirstTagElement(child, target)
                if (match != null) return match
            }
        }
        return null
    }

    private fun findFirstTagText(parent: Element, targetName: String): String? {
        return findFirstTagElement(parent, targetName)?.textContent
    }

    // ==========================================
    // Path Normalization & Filtering
    // ==========================================

    private fun normalizePath(urlOrPath: String): String {
        val decoded = try {
            URLDecoder.decode(urlOrPath, "UTF-8").trim()
        } catch (_: Exception) {
            urlOrPath.trim()
        }

        return try {
            val uri = URI(decoded)
            (uri.path ?: decoded).trimEnd('/').trimStart('/')
        } catch (_: Exception) {
            decoded.substringAfter("://")
                .substringAfter('/', "")
                .trimEnd('/')
                .trimStart('/')
        }
    }

    private fun isSelfOrParent(href: String, requestedUrl: String): Boolean {
        val normHref = normalizePath(href)
        val normTarget = normalizePath(requestedUrl)

        if (normHref.equals(normTarget, ignoreCase = true)) return true

        val last = normHref.substringAfterLast('/')
        return last == "." || last == ".."
    }

    private fun buildFullUrl(href: String): String {
        return if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            val base = mainUrl.trimEnd('/')
            val path = if (href.startsWith('/')) href else "/$href"
            "$base$path"
        }
    }

    private fun isMediaResource(resource: WebDAVResource): Boolean {
        if (resource.isDirectory) return false
        val ct = resource.contentType?.lowercase()
        if (ct != null && ct.startsWith("video/")) return true
        val ext = resource.href.substringBefore('?').substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    private fun isMediaUrl(url: String): Boolean {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    // ==========================================
    // Cloudstream Provider API Lifecycle
    // ==========================================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val currentServer = WebDAVPlugin.getServerUrl()
        if (currentServer.isBlank()) {
            val unconfigured = listOf(
                newMovieSearchResponse("WebDAV Not Configured (Open Settings)", "", TvType.Movie) {
                    this.posterUrl = null
                }
            )
            return newHomePageResponse(listOf(HomePageList("Configuration Required", unconfigured)), hasNext = false)
        }

        if (page > 1) {
            return newHomePageResponse(emptyList())
        }

        val rootUrl = currentServer.trimEnd('/') + "/"
        val xml = propfind(rootUrl, depth = 1)
        val items = parseWebDAVXml(xml).filterNot { isSelfOrParent(it.href, rootUrl) }

        val seriesResponses = mutableListOf<SearchResponse>()
        val movieResponses = mutableListOf<SearchResponse>()

        for (item in items) {
            val fullUrl = buildFullUrl(item.href)
            if (item.isDirectory) {
                seriesResponses.add(
                    newMovieSearchResponse(item.displayName, fullUrl, TvType.TvSeries) {
                        this.posterUrl = null
                    }
                )
            } else if (isMediaResource(item)) {
                movieResponses.add(
                    newMovieSearchResponse(item.displayName, fullUrl, TvType.Movie) {
                        this.posterUrl = null
                    }
                )
            }
        }

        val homeLists = mutableListOf<HomePageList>()
        if (seriesResponses.isNotEmpty()) {
            homeLists.add(HomePageList("Directories & Series", seriesResponses))
        }
        if (movieResponses.isNotEmpty()) {
            homeLists.add(HomePageList("Media Files & Movies", movieResponses))
        }
        if (homeLists.isEmpty()) {
            homeLists.add(HomePageList("WebDAV Drive", emptyList()))
        }

        return newHomePageResponse(homeLists, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        val currentServer = WebDAVPlugin.getServerUrl()
        if (currentServer.isBlank()) {
            throw IOException("WebDAV server URL is not configured. Please open extension settings in the Extensions page.")
        }

        val title = URLDecoder.decode(url.trimEnd('/').substringAfterLast('/'), "UTF-8")

        // Direct video link clicked
        if (isMediaUrl(url)) {
            val cleanTitle = title.substringBeforeLast('.')
            return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url)
        }

        // Target is a folder: fetch contents to populate episodes or detect single movie
        val folderUrl = if (url.endsWith('/')) url else "$url/"
        val xml = propfind(folderUrl, depth = 1)
        val resources = parseWebDAVXml(xml).filterNot { isSelfOrParent(it.href, folderUrl) }

        val directVideos = resources.filter { isMediaResource(it) }
        val subFolders = resources.filter { it.isDirectory }

        // Folder containing a single movie file
        if (directVideos.size == 1 && subFolders.isEmpty()) {
            val video = directVideos.first()
            return newMovieLoadResponse(title, url, TvType.Movie, buildFullUrl(video.href))
        }

        // Folder containing series episodes or season sub-directories
        val episodes = mutableListOf<Episode>()

        // 1. Direct videos in current folder
        directVideos.forEachIndexed { index, video ->
            val videoUrl = buildFullUrl(video.href)
            val videoName = video.displayName.substringBeforeLast('.')
            val seasonNum = Regex("""(?i)(?:season|s)\s*(\d+)""").find(video.displayName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val episodeNum = Regex("""(?i)(?:episode|ep|e)\s*(\d+)""").find(video.displayName)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

            episodes.add(
                newEpisode(videoUrl) {
                    this.name = videoName
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            )
        }

        // 2. Scan sub-directories (e.g., Season 01, Season 02)
        for ((folderIndex, subFolder) in subFolders.withIndex()) {
            val subFolderUrl = buildFullUrl(subFolder.href).let { if (it.endsWith('/')) it else "$it/" }
            val seasonMatch = Regex("""(?i)(?:season|s)\s*(\d+)""").find(subFolder.displayName)?.groupValues?.get(1)?.toIntOrNull()
            val derivedSeason = seasonMatch ?: (folderIndex + 1)

            try {
                val subXml = propfind(subFolderUrl, depth = 1)
                val subVideos = parseWebDAVXml(subXml)
                    .filterNot { isSelfOrParent(it.href, subFolderUrl) }
                    .filter { isMediaResource(it) }

                subVideos.forEachIndexed { subIndex, subVideo ->
                    val fileUrl = buildFullUrl(subVideo.href)
                    val epName = subVideo.displayName.substringBeforeLast('.')
                    val epNum = Regex("""(?i)(?:episode|ep|e)\s*(\d+)""").find(subVideo.displayName)?.groupValues?.get(1)?.toIntOrNull() ?: (subIndex + 1)

                    episodes.add(
                        newEpisode(fileUrl) {
                            this.name = epName
                            this.season = derivedSeason
                            this.episode = epNum
                        }
                    )
                }
            } catch (_: Exception) {
            }
        }

        episodes.sortWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamUrl = buildFullUrl(data)
        val headers = mutableMapOf<String, String>()
        getAuthHeader()?.let { auth ->
            headers["Authorization"] = auth
        }

        val link = newExtractorLink(
            source = this.name,
            name = this.name,
            url = streamUrl,
            type = INFER_TYPE
        ) {
            this.referer = mainUrl
            this.quality = Qualities.P1080.value
            this.headers = headers
        }

        callback.invoke(link)
        return true
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val currentServer = WebDAVPlugin.getServerUrl()
        if (currentServer.isBlank()) return emptyList()

        val rootUrl = currentServer.trimEnd('/') + "/"
        val xml = propfind(rootUrl, depth = 1)
        val matches = parseWebDAVXml(xml)
            .filterNot { isSelfOrParent(it.href, rootUrl) }
            .filter { it.displayName.contains(query, ignoreCase = true) }

        return matches.mapNotNull { res ->
            val fullUrl = buildFullUrl(res.href)
            if (res.isDirectory) {
                newMovieSearchResponse(res.displayName, fullUrl, TvType.TvSeries)
            } else if (isMediaResource(res)) {
                newMovieSearchResponse(res.displayName, fullUrl, TvType.Movie)
            } else {
                null
            }
        }
    }
}
