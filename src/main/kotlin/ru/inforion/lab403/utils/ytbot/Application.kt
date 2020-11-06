package ru.inforion.lab403.utils.ytbot

import ru.inforion.lab403.common.extensions.BlockingValue
import ru.inforion.lab403.common.extensions.argparse.parseArguments
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.checkers.TelegramChecker
import ru.inforion.lab403.utils.ytbot.checkers.YoutrackChecker
import ru.inforion.lab403.utils.ytbot.common.TimestampFile
import ru.inforion.lab403.utils.ytbot.common.YoutrackTelegramBot
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime


object Application {
    val log = logger()

    private fun daemonize(bot: YoutrackTelegramBot, options: Options) {
        log.info { "Starting daemon... print 'quit' without quotes and enter to stop daemon" }

        val stopNotify = BlockingValue<Int>()

        if (!options.dontStartServices)
            bot.createCommandServices()

        val worker = thread {
            while (stopNotify.poll(options.daemon * 1000L) == null) {
                bot.execute(options)
            }
        }

        val reader = System.`in`.bufferedReader()
        do { val line = reader.readLine() } while (line != "quit")

        stopNotify.offer(0)
        worker.join()
        log.info { "Stopping youtrack-telegram-bot..." }

        exitProcess(0)
    }

    /**
     * Function creates ssl socket factory for self-signed trusted certificate
     */
    private fun getSSLSocketFactory(file: File?): SSLSocketFactory {
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

        val timestampFile = TimestampFile(appConfig.timestampFilePath).also {
            log.config { "Validating timestamp file: '${appConfig.timestampFilePath}'" }
            it.validateTimestampFile(appConfig.projects, options.timestamp)
        }

        val bot = YoutrackTelegramBot(appConfig, timestampFile)

        with(options) {
            log.info { "Starting bot with options: send2Telegram=${!dontSendMessage} use services=${!dontStartServices}" }
            if (daemon > 0) {
                // here we don't use options.timestamp because it redefined in timestamp file
                daemonize(bot, options)
            } else {
                bot.execute(options)
            }
        }
    }
}


