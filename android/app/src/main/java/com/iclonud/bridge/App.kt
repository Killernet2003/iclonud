package com.iclonud.bridge

import android.app.Application
import com.iclonud.bridge.data.Credentials
import com.iclonud.bridge.net.CalDavRepository
import com.iclonud.bridge.net.CardDavRepository
import com.iclonud.bridge.net.DavClient
import com.iclonud.bridge.net.MailRepository

/**
 * Contenedor sencillo de dependencias. Tras el acceso se construyen los repositorios
 * con las credenciales guardadas; al cerrar sesión se descartan.
 */
class App : Application() {

    lateinit var credentials: Credentials
        private set

    var mail: MailRepository? = null; private set
    var caldav: CalDavRepository? = null; private set
    var carddav: CardDavRepository? = null; private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        credentials = Credentials(this)
        if (credentials.isLoggedIn) buildRepos()
    }

    fun buildRepos() {
        val id = credentials.appleId ?: return
        val pw = credentials.appPassword ?: return
        val dav = DavClient(id, pw)
        mail = MailRepository(id, pw)
        caldav = CalDavRepository(dav)
        carddav = CardDavRepository(dav)
    }

    fun logout() {
        credentials.clear()
        mail = null; caldav = null; carddav = null
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
