package com.asu.project1

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.AsyncTask
import android.os.Build
import androidx.annotation.RequiresApi

class SlowTask(private val videoFilePath: String, private val listener: SlowTaskListener) :
    AsyncTask<Void, Void, String?>() {
    @Deprecated("Deprecated in Java")
    @RequiresApi(Build.VERSION_CODES.P)

    private var onPostExecuteListener: ((String) -> Unit)? = null


    override fun doInBackground(vararg params: Void?): String? {
        System.out.println("In doInBackground")
        var m_bitmap: Bitmap? = null
        var retriever = MediaMetadataRetriever()
        var frameList = ArrayList<Bitmap>()
        try {
            retriever.setDataSource(videoFilePath)
            var duration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            var aduration = duration!!.toInt()
            var i = 10
            while (i < aduration) {
                val bitmap = retriever.getFrameAtIndex(i)
                frameList.add(bitmap!!)
                i += 5
            }
        } catch (m_e: Exception) {

        } finally {
            System.out.println("In finallyy")
            retriever?.release()
            var redBucket: Long = 0
            var pixelCount: Long = 0
            val a = mutableListOf<Long>()
            for (i in frameList) {
                redBucket = 0
                for (y in 550 until 650) {
                    for (x in 550 until 650) {
                        val c: Int = i.getPixel(x, y)
                        pixelCount++
                        redBucket += Color.red(c) + Color.blue(c) + Color.green(c)
                    }
                }
                a.add(redBucket)
            }
            val b = mutableListOf<Long>()
            for (i in 0 until a.lastIndex - 5) {
                var temp =
                    (a.elementAt(i) + a.elementAt(i + 1) + a.elementAt(i + 2) + a.elementAt(
                        i + 3
                    ) + a.elementAt(
                        i + 4
                    )) / 4
                b.add(temp)
            }
            var x = b.elementAt(0)
            var count = 0
            for (i in 1 until b.lastIndex) {
                var p = b.elementAt(i.toInt())
                if ((p - x) > 3500) {
                    count = count + 1
                }
                x = b.elementAt(i.toInt())
            }
            var rate = ((count.toFloat() / 45) * 60).toInt()
            return (rate / 2).toString()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        listener?.onTaskCompleted(result ?: "")
        onPostExecuteListener?.invoke(result.toString())
    }
    @RequiresApi(Build.VERSION_CODES.P)
    fun setOnPostExecuteListener(listener: (String) -> Unit) {
        onPostExecuteListener = listener
    }


}

