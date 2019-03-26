# youtrack-telegram-bot

## Description

[Telegram](https://telegram.org/) Bot for JetBrains YouTrack server (tested with Youtrack 2019.1). Written in Kotlin, JVM for back-end. Youtrack-bot connects to a specified YouTrack server, queries specified project activity issues, parses and posts them to a specified telegram chat. 

Features:
- configurable projects and chats
- tags for users and fields
- links to Youtrack issues and activities
- mention Telegram users using their user ID (should be configured)
- telegram SOCKS5 SSL proxy-server support
- telegram DNS custom resolver support

## Installation

Install Oracle JVM version 1.8 or above.  
Download and extract .tar file to any directory with permission access and create `temp` directory:

```
tar -xvf youtrack-telegram-bot-x.y.z.tar -C <youtrack-telegram-bot>
cd <youtrack-telegram-bot>
mkdir temp
```
 - *x.y.z* - version of package
 - *youtrack-telegram-bot* - application directory
 
Download [config_template.json](https://github.com/inforion/youtrack-telegram-bot/blob/master/src/config_template.json), rename it to `config.json` and move it to <youtrack-telegram-bot>/temp folder. Edit `config.json` according to your settings.

## Running

Check bot settings:

```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -ytc Kopycat
```

You should see all projects from Youtrack and all issues from specified project listed in console (in this case project's name is Kopycat)

Than check Telegram connection:

```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -tgc "Kopycat:m:sending test message to associated chat"
```

You should see the message `sending test message to associated chat` in the project Telegram chat (which is assosiated with project Kopycat in config.json). 

You can perform dry run to parse Youtrack issues. Youtrack-telegram-bot will analyze project and parse all activities, print it to console, but won't actually send anything to Telegram.
**This is required to determine timestamp of the last update**. If you don't perform dry run then all activities will be posted to Telegram.

```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -r
```

Run youtrack-telegram-bot as daemon:

```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -d 1
```

This will start youtrack-telegram-bot in daemon mode with 1 second update interval.
