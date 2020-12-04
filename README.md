# youtrack-telegram-bot

## Description

youtrack-telegram-bot is a [Telegram](https://telegram.org/) Bot for JetBrains YouTrack server (tested with Youtrack 2020.2). Written in Kotlin, JVM for back-end (tested on Adopted OpenJDK 11). Youtrack-telegram-bot connects to a specified YouTrack server, queries specified project activity issues, parses and posts them to a specified telegram chat. 

Features:
- configurable projects and chats
- tags for users and fields
- links to Youtrack issues and activities
- mention Telegram users using their user ID (should be configured)
- Telegram SOCKS5 SSL proxy-server support
- Telegram DNS custom resolver support
- Youtrack activities aggregation in one message 

## Installation

Install Adopted OpenJDK version 11.  
Download and extract .zip file to any folder with permission access and create `temp` folder:

```
unzip -o youtrack-telegram-bot-<version>.zip
cd youtrack-telegram-bot-<version> 
```

 - `<version>` - version of package
 
Download [config_template.json](https://github.com/inforion/youtrack-telegram-bot/blob/master/src/config_template.json), rename it to `config.json` and move it to <youtrack-telegram-bot>/temp folder. Edit `config.json` according to your settings.

## Running

Check bot settings:

```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -ytc Kopycat
```

You should see all projects from Youtrack and all issues from specified project listed in a console (in this case project's name is Kopycat)

Then check Telegram connection:

```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -tgc "Kopycat:m:sending test message to associated chat"
```

You should see the message `sending test message to associated chat` in the project Telegram chat (which is associated with project Kopycat in config.json). 

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

## Update to version 0.2.x from 0.1.x

The first requirement is to update JVM because version 0.1.x works with Oracle JDK 1.8 and version 0.2.x tested on Adopted JDK 11.

After Java update basically you should stop your youtrack-telegram-bot, download new distribution of youtrack-telegram-bot, fix config according to new template and run a new version. Timestamp file will be updated automatically to new format. New version saves timestamps for each project independently.  

New version of config template placed in `master` branch as a previous one. All new options commented with some description. One of the most important is **messageWaitInterval**. This option makes possible to aggregate action that user make for one issue. For example, when user set issue is fixed and even if bot in a daemon mode with 1 second update interval bot is just register that Youtrack have some changes but not send it to Telegram. Bot waits **messageWaitInterval** for other user actions to aggregate (group) them in one Telegram message. When timeout elapsed bot send one message with all actions. This option makes bot less "spamy" and more informative.

NOTE: If other issue also have edits bot send all issue immediately. This is not a good behavior but at a current format of timestamp file bot has no other option. This will be fixed in future versions.