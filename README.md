[![Release](https://jitpack.io/v/umjammer/vavi-nio-file-box.svg)](https://jitpack.io/#umjammer/vavi-nio-file-box)
[![Java CI](https://github.com/umjammer/vavi-nio-file-box/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-nio-file-box/actions)
[![CodeQL](https://github.com/umjammer/vavi-nio-file-box/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-nio-file-box/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)
[![Parent](https://img.shields.io/badge/Parent-vavi--apps--fuse-pink)](https://github.com/umjammer/vavi-apps-fuse)

## vavi-nio-file-box

Java filesystem SPI ([JSR-203](https://jcp.org/en/jsr/detail?id=203)) over [DropBox](https://dropbox.com) API.

[original](https://github.com/fge/java7-fs-box)

## Install

### jars

 * https://jitpack.io/#umjammer/vavi-nio-file-box

### ~~selenium chrome driver~~ (obsolete, use os default browser)

 * Download the [chromedriver executable](https://chromedriver.chromium.org/downloads) and locate it into some directory.
   * Don't forget to run jvm with jvm argument `-Dwebdriver.chrome.driver=/usr/local/bin/chromedriver`.

## Usage

First, get box account, then create [box app](https://app.box.com/developers/console).

Next, prepare 2 property files.

 * application credential

```shell
$ cat ${HOME}/.vavifuse/box.properties
box.applicationName=your_application_name
box.clientId=your_client_id
box.clientSecret=your_client_secret
box.redirectUrl=http://localhost:30001
box.scopes=root_readwrite
```

 * user credential

```shell
$ cat ${HOME}/.vavifuse/credentials.properties
box.password.xxx@yyy.zzz=your_password
```

Then write your code! Here is a short example (imports omitted for brevity):

```java
public class Main {

    public static void main(String[] args) throws IOException {
        String email = "xxx@yyy.zzz";

        URI uri = URI.create("box:///?id=" + email);

        FileSystem fs = FileSystems.newFileSystem(uri, env);
            :
    }
}
```

### See also

https://github.com/umjammer/vavi-apps-fuse/blob/master/vavi-nio-file-gathered/src/test/java/vavi/nio/file/box/Main.java

## TODO

  * ~~dev token authenticator~~
  * project name to vavi-nio-file-box

## ⚠ Note to self

 * update `BOX_DEVELOPER_TOKEN` at [github actions secret](https://github.com/umjammer/java7-fs-box/settings/secrets/actions) before deploy