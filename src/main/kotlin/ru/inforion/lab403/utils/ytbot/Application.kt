package ru.inforion.lab403.utils.ytbot

import ru.inforion.lab403.common.extensions.BlockingValue
import ru.inforion.lab403.common.extensions.argparse.*
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread
import kotlin.system.exitProcess


object Application {
    val log = logger()

    private fun daemonize(
        bot: YoutrackTelegramBot,
        options: Options
    ) {
        log.info { "Starting daemon... press enter to stop daemon" }

        val stopNotify = BlockingValue<Int>()
        var currentLastTimestamp = bot.startLastTimestamp

        if (!options.dontStartServices)
            bot.createCommandServices()

        val worker = thread {
            while (stopNotify.poll(options.daemon * 1000L) == null) {
                bot.execute(options, currentLastTimestamp)
                currentLastTimestamp = bot.loadCurrentLastTimestamp()
            }
        }

        thread {
            System.`in`.reader().read()
            stopNotify.offer(0)
        }

        worker.join()

        log.info { "Stopping youtrack-telegram-bot..." }

        // If something goes wrong stop reader hardcore
        System.`in`.close()
    }

    fun getSSLSocketFactory(file: File?): SSLSocketFactory {
        if (file == null) return SSLSocketFactory.getDefault() as SSLSocketFactory

        val certificate = CertificateFactory
            .getInstance("X.509")
            .generateCertificate(file.inputStream())

        val keyStore = KeyStore
            .getInstance(KeyStore.getDefaultType())
            .also {
                it.load(null, null)
                it.setCertificateEntry("server", certificate)
            }

        val trustManagerFactory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .also { it.init(keyStore) }

        val sslContext = SSLContext
            .getInstance("TLS")
            .also { it.init(null, trustManagerFactory.trustManagers, null) }

        return sslContext.socketFactory
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options = args.parseArguments<Options>()

        log.info { options.namespace }

        if (options.certificate != null) {
            log.info { "Setting up trusted certificate: '${options.certificate}'" }
            val factory = getSSLSocketFactory(options.certificate)
            HttpsURLConnection.setDefaultSSLSocketFactory(factory)
        }

        val appConfig = ApplicationConfig.load(options.config)

        var inCheckMode = false

        options.checkTelegram?.let {
            log.warning { "Starting Telegram connection check with $it" }
            val telegramChecker = TelegramChecker(appConfig)
            val data = it.split(":")
            // if unknown type -> start server
            telegramChecker.check(project = data[0], type = data[1], message = data[2])
            inCheckMode = true
        }

        options.checkYoutrack?.let {
            log.warning { "Starting Youtrack connection check with $it" }
            val youtrackChecker = YoutrackChecker(appConfig)
            youtrackChecker.check(project = it)
            inCheckMode = true
        }

        if (inCheckMode) {
            log.info { "youtrack-telegram-bot was in check mode... exiting" }
            exitProcess(0)
        }

        if (options.daemon > 24 * 60 * 60) {
            log.info { "Update period to slow..." }
            exitProcess(-1)
        }

        log.info { "$appConfig" }

        val lastTimestamp = options.timestamp ?: appConfig.loadTimestamp()

        log.info { "Checking ${appConfig.timestampFilePath} to writing..." }
        appConfig
            .runCatching { saveTimestamp(lastTimestamp) }
            .onFailure {
                log.severe { "File ${appConfig.timestampFilePath} can't be written" }
                exitProcess(-1)
            }

        val bot = YoutrackTelegramBot(lastTimestamp, appConfig)

        with(options) {
            val datetime = Youtrack.makeTimedate(lastTimestamp)
            log.info { "Starting last timestamp=$lastTimestamp [$datetime] send=${!dontSendMessage} services=${!dontStartServices}" }
            if (daemon > 0) daemonize(bot, options) else bot.execute(options, lastTimestamp)
        }
    }
}


