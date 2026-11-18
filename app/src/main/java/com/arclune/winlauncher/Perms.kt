package com.arclune.winlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** Izin akses file yang dipakai File Explorer, Foto, dan Media Player. */
object Perms {
    fun hasFiles(a: MainActivity): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun requestFiles(a: MainActivity) {
        if (Build.VERSION.SDK_INT >= 30) {
            a.startSafe(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + a.packageName)))
        } else a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 12)
    }

    fun hasContacts(a: MainActivity) =
        a.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun requestContacts(a: MainActivity) { a.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 14) }
}
