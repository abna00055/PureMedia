package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.model.Artist
import com.example.data.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mockTracks = listOf(
        MusicTrack(
            id = 2001L,
            title = "نسيم الصباح (Morning Breeze)",
            artist = "صوت الأندلس (Andalusia Voice)",
            album = "ميراث الشرق",
            duration = 184000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            albumArtUri = "https://picsum.photos/id/101/600/600",
            isFavorite = false
        ),
        MusicTrack(
            id = 2002L,
            title = "نبض الصحراء (Pulse of the Desert)",
            artist = "كمال الراضي (Kamal Al-Radi)",
            album = "رموز الرمل",
            duration = 210000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            albumArtUri = "https://picsum.photos/id/102/600/600",
            isFavorite = true
        ),
        MusicTrack(
            id = 2003L,
            title = "ليالي بغداد (Baghdad Nights)",
            artist = "أوركسترا دجلة (Tigris Orchestra)",
            album = "مقامات حرة",
            duration = 245000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            albumArtUri = "https://picsum.photos/id/103/600/600",
            isFavorite = false
        ),
        MusicTrack(
            id = 2004L,
            title = "نور الأمل (Hope's Ray)",
            artist = "رحاب خالد (Rehab Khaled)",
            album = "أطياف الروح",
            duration = 195000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            albumArtUri = "https://picsum.photos/id/104/600/600",
            isFavorite = false
        ),
        MusicTrack(
            id = 2005L,
            title = "عازف العود (The Oud Player)",
            artist = "كمال الراضي (Kamal Al-Radi)",
            album = "أوتار الزمن",
            duration = 222000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            albumArtUri = "https://picsum.photos/id/105/600/600",
            isFavorite = true
        ),
        MusicTrack(
            id = 2006L,
            title = "نوافذ الذاكرة (Windows of Memory)",
            artist = "صوت الأندلس (Andalusia Voice)",
            album = "فكرة عابرة",
            duration = 178000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            albumArtUri = "https://picsum.photos/id/106/600/600",
            isFavorite = false
        ),
        MusicTrack(
            id = 2007L,
            title = "رقصة السلام (Dance of Peace)",
            artist = "أوركسترا دجلة (Tigris Orchestra)",
            album = "مقامات حرة",
            duration = 312000L,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            albumArtUri = "https://picsum.photos/id/107/600/600",
            isFavorite = false
        )
    )

    suspend fun loadMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<MusicTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        try {
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durCol)
                    val path = cursor.getString(dataCol) ?: ""

                    // Fetch album art Uri
                    val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val artUriStr = if (albumIdCol != -1) {
                        val albumId = cursor.getLong(albumIdCol)
                        val artworkUri = Uri.parse("content://media/external/audio/albumart")
                        ContentUris.withAppendedId(artworkUri, albumId).toString()
                    } else {
                        null
                    }

                    resultList.add(
                        MusicTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = path,
                            albumArtUri = artUriStr,
                            isFavorite = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (resultList.isEmpty()) {
            mockTracks
        } else {
            resultList
        }
    }

    suspend fun getArtists(): List<Artist> {
        val tracks = loadMusic()
        return tracks.groupBy { it.artist }
            .mapValues { (artistName, list) ->
                val id = artistName.hashCode().toLong()
                val avatarUri = list.firstOrNull()?.albumArtUri
                Artist(id, artistName, list.size, avatarUri)
            }.values.toList()
    }

    fun getLyricsForTrack(track: MusicTrack): String {
        // Return highly synced, high-fidelity LRC format based on track titles
        return when {
            track.title.contains("نسيم الصباح") || track.id == 2001L -> {
                """
                [00:00.00]مرحباً بك في مشغل بيورميديا (PureMedia Live)
                [00:04.00]نسمات الفجر تطل علينا بالدفء والأمل
                [00:09.00]وصوت الموسيقى يحلق في سماء الأندلس العتيقة
                [00:15.00]هدوء الليل ينجلي، ونور الشمس يشرق رويداً رويداً
                [00:23.00]تسمع نبضات العود ترسم ألحاناً من الخلود
                [00:30.00]كأنها تحكي قصصاً من ماضٍ لا يزول
                [00:40.00]عبق الزمان يداعب أوتار القلوب
                [00:50.00]فنبتسم للمستقبل واللحن الجميل يغمرنا بالسلام
                [01:05.00]بين رملة البحر ونسمة النهر نلتقي مجدداً
                [01:20.00]لتستمر الرحلة وتدوم نغمات الحب والوداد
                [01:40.00]تتلاشى الألحان كما تذوب قطرات الندى على خد الورد
                [02:00.00]النهاية - شكراً لاستماعكم لألحان مشغل بيور ميديا
                """.trimIndent()
            }
            track.title.contains("نبض الصحراء") || track.id == 2002L -> {
                """
                [00:00.00]أهلاً بك في رحاب نبض الصحراء الشاسعة
                [00:05.00]صوت خطى القوافل تحت رداء الليل المقمر
                [00:11.00]رمال الصحراء تحاكي بريق النجوم البعيدة
                [00:18.00]أوتار الربابة والعود تعانق نسائم الليل والبراري
                [00:26.00]كل حبة رمل في هذا المدى شهدت حكاية قديمة
                [00:35.00]الهدوء يسود الكون، ونبض الوجدان يتعالى
                [00:45.00]إن حكمة الرمال في صبرها وجاذبيتها الساحرة
                [01:00.00]هنا في قلب الواحة نجد الأمان والاستقرار
                [01:20.00]مع نجوم القطب وبوصلة الضياء نسير إلى الغد
                [01:50.00]يخبو النغم العذب ويظل أثر الشغف باقياً في أرواحنا
                """.trimIndent()
            }
            else -> {
                """
                [00:00.00]مرحباً بك في مشغل الموسيقى الذكي بيور ميديا
                [00:05.00]لحن حر ينساب ليريح النفوس ويبعث الطمأنينة
                [00:12.00]أوتار سحرية تعزف مقاماً للراحة والتركيز
                [00:20.00]استمتع بالمؤثرات الصوتية والبيئية المنسجمة
                [00:30.00]الخلفية الموسيقية تتكامل مع تفاصيل الأداء
                [00:45.00]كل نبضة تبني جسراً من الإبداع والمشاعر الصادقة
                [01:00.00]مستمرون في العطاء وتقديم أجمل المقامات الشرقية
                [01:30.00]الانسجام التام بين الكلمة واللحن والوجدان المتكامل
                [02:00.00]سحر الأنغام يملأ طيات الفراغ بالجمال اللامتناهي
                [02:30.00]تشارف السيمفونية المدهشة على نهايتها السعيدة
                """.trimIndent()
            }
        }
    }
}
