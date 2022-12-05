@file:Suppress("UNUSED_PARAMETER")
package au.com.shiftyjelly.pocketcasts.repositories.file

import android.content.Context
import android.os.Environment
import au.com.shiftyjelly.pocketcasts.models.entity.Episode
import au.com.shiftyjelly.pocketcasts.models.entity.Playable
import au.com.shiftyjelly.pocketcasts.models.type.EpisodeStatusEnum
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.utils.FileUtil
import au.com.shiftyjelly.pocketcasts.utils.StringUtil
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorage @Inject constructor(
    private val settings: Settings,
    @ApplicationContext private val context: Context,
) {

    @Throws(StorageException::class)
    fun getPodcastEpisodeFile(episode: Playable): File {
        val fileName = episode.uuid + episode.getFileExtension()
        val directory = if (episode is Episode) getPodcastDirectory() else getCloudFilesFolder()
        return File(directory, fileName)
    }

    @Throws(StorageException::class)
    fun getTempPodcastEpisodeFile(episode: Playable): File {
        val fileName = episode.uuid + episode.getFileExtension()
        return File(getTempPodcastDirectory(), fileName)
    }

    fun getCloudFileImage(uuid: String): File? {
        val fileName = uuid + "_imagefile"
        return try {
            File(getCloudFilesFolder(), fileName)
        } catch (e: StorageException) {
            Timber.e(e)
            null
        }
    }

    @Throws(StorageException::class)
    fun getCloudFilesFolder(): File {
        return getOrCreateDirectory(DIR_CLOUD_FILES)
    }

    @Throws(StorageException::class)
    fun getOpmlFileFolder(): File {
        return getOrCreateDirectory(DIR_OPML_FOLDER)
    }

    @Throws(StorageException::class)
    fun getNetworkImageDirectory(): File {
        return getOrCreateDirectory(DIR_NETWORK_IMAGES)
    }

    @Throws(StorageException::class)
    fun getPodcastDirectory(): File {
        return getOrCreateDirectory(DIR_EPISODES)
    }

    @Throws(StorageException::class)
    fun getTempPodcastDirectory(): File {
        return getOrCreateCacheDirectory(FOLDER_TEMP_EPISODES)
    }

    @Throws(StorageException::class)
    fun getOldTempPodcastDirectory(): File {
        return getOrCreateDirectory(FOLDER_TEMP_EPISODES)
    }

    @Throws(StorageException::class)
    fun getPodcastGroupImageDirectory(): File {
        val dir = File(getStorageDirectory().absolutePath + File.separator + DIR_PODCAST_GROUP_IMAGES)
        createDirectory(dir)
        addNoMediaFile(dir, settings)
        return dir
    }

    @Throws(StorageException::class)
    fun getOrCreateCacheDirectory(name: String): File {
        return getOrCreateDirectory(context.cacheDir, name)
    }

    @Throws(StorageException::class)
    fun getOrCreateDirectory(name: String): File {
        return getOrCreateDirectory(getStorageDirectory(), name)
    }

    @Throws(StorageException::class)
    private fun getOrCreateDirectory(parentDir: File, name: String): File {
        val dir = File(parentDir, name + File.separator)
        createDirectory(dir)
        addNoMediaFile(dir, settings)
        return dir
    }

    @Throws(StorageException::class)
    fun getStorageDirectory(): File {
        val dir = File(getBaseStorageDirectory(), "PocketCasts" + File.separator)
        createDirectory(dir)
        return dir
    }

    @Throws(StorageException::class)
    fun getBaseStorageDirectory(): File {
        return getBaseStorageDirectory(settings.getStorageChoice(), context)
    }

    @Throws(StorageException::class)
    fun getBaseStorageDirectory(choice: String?, context: Context?): File {
        return if (choice == Settings.STORAGE_ON_CUSTOM_FOLDER) {
            val path = settings.getStorageCustomFolder()
            if (path.isBlank()) {
                throw StorageException("Ooops, please set the Custom Folder Location in the settings.")
            }
            val folder = File(path)
            if (!folder.exists()) {
                val success = folder.mkdirs()
                if (!success && !folder.exists()) {
                    throw StorageException("Storage custom folder unavailable.")
                }
            }
            folder
        } else {
            File(choice!!)
        }
    }

    // confirms that all the folders we want to hide from the user have .nomedia files in them
    fun checkNoMediaDirs() {
        // we can do this by getting all the folders
        try {
            addNoMediaFile(getStorageDirectory(), settings)
            getNetworkImageDirectory()
            getPodcastGroupImageDirectory()
            getTempPodcastDirectory()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun clearNoMediaFiles() {
        try {
            val storageDir = getStorageDirectory()
            enableNoMediaFile(storageDir, false)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun addNoMediaFiles() {
        try {
            val storageDir = getStorageDirectory()
            enableNoMediaFile(storageDir, true)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun enableNoMediaFile(dir: File, enable: Boolean) {
        try {
            val noMediaFile = File(dir, ".nomedia")
            if (enable) {
                if (!noMediaFile.exists()) {
                    noMediaFile.createNewFile()
                }
            } else {
                if (noMediaFile.exists()) {
                    noMediaFile.delete()
                }
            }
            val files = dir.listFiles()
            if (files.isNullOrEmpty()) return
            for (file in files) {
                if (file.isDirectory) {
                    enableNoMediaFile(file, enable)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun removeDirectoryFiles(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles()
        if (files.isNullOrEmpty()) return
        for (file in files) {
            if (file.name.startsWith(".")) {
                continue
            }
            file.delete()
        }
    }

    private fun moveFileToDirectory(filePath: String?, directory: File): String? {
        // validate the path, check PocketCasts is in the path so we don't delete something important
        if (filePath.isNullOrBlank() || filePath.indexOf("/PocketCasts") == -1) {
            LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, "Not moving because it's blank or not PocketCasts")
            return filePath
        }
        val file = File(filePath)
        // check we aren't copying to the same directory
        if (file.parentFile == directory) {
            LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, "Not moving because it's the same directory")
            return filePath
        }
        val newFile = File(directory, file.name)
        if (file.exists() && file.isFile) {
            try {
                FileUtil.copyFile(file, newFile)
                val wasDeleted = file.delete()
                LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Moved " + file.absolutePath + " to " + newFile.absolutePath + " wasDeleted: " + wasDeleted)
            } catch (e: IOException) {
                LogBuffer.e(
                    LogBuffer.TAG_BACKGROUND_TASKS, e, "Problems moving a file to a new location. from: " + file.absolutePath + " to: " + newFile.absolutePath
                )
            }
        }
        return newFile.absolutePath
    }

    private fun moveDirectory(fromDirectory: File?, toDirectory: File?) {
        if (fromDirectory == null || !fromDirectory.exists() || !fromDirectory.isDirectory || toDirectory == null) {
            return
        }
        try {
            FileUtil.copyDirectory(fromDirectory, toDirectory)
            fromDirectory.delete()
        } catch (e: IOException) {
            Timber.e(
                e,
                "Problems moving a directory to a new location. from: " + fromDirectory.absolutePath + " to: " + toDirectory.absolutePath
            )
        }
    }

    fun moveStorage(
        oldDirectory: File?,
        newDirectory: File,
        podcastManager: PodcastManager?,
        episodeManager: EpisodeManager
    ) {
        try {
            val pocketCastsDir = File(oldDirectory, "PocketCasts")
            if (pocketCastsDir.exists() && pocketCastsDir.isDirectory) {
                LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Pocket casts directory exists")
                newDirectory.mkdirs()
                val newPocketCastsDir = File(newDirectory, "PocketCasts")
                val folderAlreadyExisted = newPocketCastsDir.exists()

                // check existing media and mark those episodes as downloaded
                val episodeDirectory = getOrCreateDirectory(newPocketCastsDir, DIR_EPISODES)
                if (folderAlreadyExisted) {
//                    var foundMedia = false
                    val files = episodeDirectory.listFiles()
                    if (files != null) {
                        for (file in files) {
                            val fileName = FileUtil.getFileNameWithoutExtension(file)
                            if (fileName.length < 36) {
                                continue
                            }
                            val episode = episodeManager.findByUuid(fileName)
                            if (episode != null) {
                                // Delete the original file if it is already there
                                if (!episode.downloadedFilePath.isNullOrBlank()) {
                                    val originalFile = File(episode.downloadedFilePath!!)
                                    if (originalFile.exists()) {
                                        originalFile.delete()
                                    }
                                }
                                episodeManager.updateDownloadFilePath(
                                    episode,
                                    file.absolutePath,
                                    true
                                )
//                                foundMedia = true
                            }
                        }
                    }
                }

                // move episodes
                val episodes = episodeManager.observeDownloadedEpisodes().blockingFirst()
                for (episode in episodes) {
                    LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Found downloaded episode " + episode.title)
                    val episodeFilePath = episode.downloadedFilePath
                    if (episodeFilePath.isNullOrBlank()) {
                        LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, "Episode had no file path")
                        continue
                    }
                    val file = File(episodeFilePath)
                    if (file.exists() && file.isFile) {
                        episode.downloadedFilePath =
                            moveFileToDirectory(episodeFilePath, episodeDirectory)
                        episodeManager.updateDownloadFilePath(
                            episode,
                            episode.downloadedFilePath!!, false
                        )
                    }
                }

                // move custom folders
                val customFilesDirectory = getOrCreateDirectory(pocketCastsDir, DIR_CUSTOM_FILES)
                val newCustomFilesDirectory =
                    getOrCreateDirectory(newPocketCastsDir, DIR_CUSTOM_FILES)
                if (customFilesDirectory.exists()) {
                    moveDirectory(customFilesDirectory, newCustomFilesDirectory)
                }

                // move network and group images
                val networkImageDirectory = getOrCreateDirectory(pocketCastsDir, DIR_NETWORK_IMAGES)
                val newNetworkImageDirectory =
                    getOrCreateDirectory(newPocketCastsDir, DIR_NETWORK_IMAGES)
                if (networkImageDirectory.exists()) {
                    moveDirectory(networkImageDirectory, newNetworkImageDirectory)
                }
            } else {
                LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, "Old directory did not exist")
            }
        } catch (e: StorageException) {
            LogBuffer.e(LogBuffer.TAG_BACKGROUND_TASKS, e, "Unable to move storage to new location.")
        }
    }

    fun fixBrokenFiles(episodeManager: EpisodeManager) {
        try {
            // get all possible locations
            val folderPaths: MutableList<String> = ArrayList()
            for (location in StorageOptions().getFolderLocations(context)) {
                folderPaths.add(location.filePath)
            }
            folderPaths.add(context.filesDir.absolutePath)
            val customFolder = settings.getStorageCustomFolder()
            if (StringUtil.isPresent(customFolder) && !folderPaths.contains(customFolder) && File(
                    customFolder
                ).exists()
            ) {
                folderPaths.add(customFolder)
            }
//            var foundEpisodes = false

            // search each folder for missing files
            for (folderPath in folderPaths) {
                val folder = File(folderPath)
                if (!folder.exists() || !folder.canRead()) {
                    continue
                }
                val pocketcastsFolder = File(folder, "PocketCasts")
                if (!pocketcastsFolder.exists() || !pocketcastsFolder.canRead()) {
                    continue
                }
                val episodesFolder = File(pocketcastsFolder, DIR_EPISODES)
                if (!episodesFolder.exists() || !episodesFolder.canRead()) {
                    continue
                }
                val files = episodesFolder.listFiles()
                for (file in files!!) {
                    val filename = file.name
                    val dotPosition = filename.lastIndexOf(".")
                    if (dotPosition < 1) {
                        continue
                    }
                    val uuid = filename.substring(0, dotPosition)
                    if (uuid.length != 36) {
                        continue
                    }
                    val episode = episodeManager.findByUuid(uuid)
                    if (episode != null) {
                        if (episode.downloadedFilePath != null && File(episode.downloadedFilePath!!).exists() && episode.isDownloaded) {
                            // skip as the episode download already exists
                            continue
                        }
                        LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Restoring downloaded file for " + episode.title + " from " + file.absolutePath)
                        // link to the found episode
                        episode.episodeStatus = EpisodeStatusEnum.DOWNLOADED
                        episode.downloadedFilePath = file.absolutePath
                        episodeManager.update(episode)
//                        foundEpisodes = true
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    companion object {
        private const val FOLDER_POCKETCASTS = "PocketCasts"
        private const val FOLDER_TEMP_EPISODES = "downloadTmp"

        const val DIR_OPML_FOLDER = "opml_import"
        const val DIR_CUSTOM_FILES = "custom_episodes"
        const val DIR_PODCAST_THUMBNAILS = "thumbnails"
        const val DIR_NETWORK_IMAGES = "network_images"
        const val DIR_WEB_CACHE = "web_cache"
        const val DIR_PODCAST_IMAGES = "images"
        const val DIR_EPISODES = "podcasts"
        val DIR_PODCAST_GROUP_IMAGES = "network_images" + File.separator + "groups" + File.separator
        const val DIR_CLOUD_FILES = "cloud_files"

        fun createDirectory(dir: File): File {
            dir.mkdirs()
            return dir
        }

        private fun addNoMediaFile(folder: File?, settings: Settings) {
            if (folder == null || !folder.exists() || settings.allowOtherAppsAccessToEpisodes()) {
                return
            }
            val file = File(folder, ".nomedia")
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }

        fun isExternalStorageAvailable(): Boolean {
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }

        fun moveFile(oldPath: String?, newPath: String?): Boolean {
            if (oldPath.isNullOrBlank() || newPath.isNullOrBlank()) return false

            val oldFile = File(oldPath)
            val newFile = File(newPath)
            if (!oldFile.exists()) return false

            try {
                FileUtil.copyFile(oldFile, newFile)
                oldFile.delete()
                return true
            } catch (e: IOException) {
                Timber.e(e, "Problems moving a file to a new location. from: " + oldFile.absolutePath + " to: " + newFile.absolutePath)
            }

            return false
        }
    }
}
