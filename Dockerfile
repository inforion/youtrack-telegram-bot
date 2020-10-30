FROM zenika/kotlin:1.3-jdk12-alpine

WORKDIR /opt/youtrack-telegram-bot

ENV TELEGRAM_BOT_VERSION 0.1.2

RUN wget -qO- https://github.com/inforion/youtrack-telegram-bot/releases/download/v0.1.2-rc0/youtrack-telegram-bot-0.1.2.tar.gz | \
    tar xvz --strip-components=1

COPY ./src/config_template.json ./temp/config.json

CMD ["bin/youtrack-telegram-bot", "-c", "temp/config.json", "-d", "1"]
