# youtrack-telegram-bot

## Description

A Kotlin  (JVM back-end) based Telegram-bot when running connects to a specified YouTrack server, queries specified projects activity issues, parses them and posts to a specified telegram chat. Main features are:

- Configurable projects and chats
- Tags for users and fields
- Links to Youtrack issues and activities
- Mentions Telegram users using user ID (should be configured)
- Telegram SOCKS5 SSL fully workable proxy
- Telegram DNS custom resolver support (when DNS blocked)
- Tested with Youtrack 2019.1

## Installation

Install Oracle JVM version 1.8 or above.

Download and unpack tar file into any directory with permission access and make temp directory:

```
tar -xvf youtrack-telegram-bot-x.y.z.tar -C <youtrack-telegram-bot>
cd <youtrack-telegram-bot>
mkdir temp
```

where 
 - x.y.z - version of package
 - youtrack-telegram-bot - application directory
 
Download config_template.json, rename it to config.json and place it into temp directory. Edit config.json according your settings.

## Running

1. Perform check Youtrack connection and setting using command:
```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -ytc Kopycat
```
You should see in console all project from Youtrack and all issues from specified project (in this case project name Kopycat)

2. Perform check Telegram connection and setting using command:
```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -tgc "Kopycat:m:sending test message to assosite chat"
```
You should see in Telegram chat assosiated in config.json with project Kopycat message "sending test message to assosite chat"

3. Perform dry run parsing Youtrack issues using command (bot will analyze project and parse all activities, print it to console, but won't send to Telegram).
This is required to determine timestamp of last update. If you don't perform dry run then all activities will be post to Telegram.
```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -r
```

4. Run youtrack-telegram-bot in daemon mode using command:
```
<youtrack-telegram-bot>/bin/youtrack-telegram-bot -c <path-to-config.json> -d 1
```
This will start youtrack-telegram-bot in daemon mode with update interval 1 second.
