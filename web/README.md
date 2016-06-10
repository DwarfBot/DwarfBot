Build and run:

```sh
[~/.../DwarfBot/web]$ cd ..
[~/.../DwarfBot]$ ./gradlew shadowJar
[~/.../DwarfBot]$ java -jar web/build/libs/web-all.jar
```

It should take no more than a few seconds to start up (faster without Slack enabled). Once it does, go to [http://localhost:4567/](http://localhost:4567/).

Control-C to exit safely.

### Slack

To submit failure reports with Slack, you will need to make a config file at `~/.config/dwarfbothttp/config.json`, with the format:

```json
{
    "slackToken": "xxxx-xxxxxxxxx-xxxx",
    "slackChannel": "channel-name"
}
```
