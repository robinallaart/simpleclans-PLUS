# File Layout - src Directory

## Directory Structure

```
src/
├── main/
│   ├── java/
│   │   └── simpleclans/
│   │       └── simpleclans/
│   │           ├── ModrinthUpdater.java
│   │           ├── SimpleclansPlugin.java
│   │           └── UpdateNotifyListener.java
│   ├── resources/
│   │   ├── config.yml
│   │   └── plugin.yml
│   │
│   └── language/
│   │   ├── AR.yml
│   │   └── BE.yml
│   │   ├── BH.yml
│   │   └── CH.yml
│   │   ├── DE.yml
│   │   └── EN.yml
│   │   ├── FR.yml
│   │   └── GU.yml
│   │   ├── HA.yml
│   │   └── HI.yml
│   │   ├── IN.yml
│   │   └── IT.yml
│   │   ├── JA.yml
│   │   └── JI.yml
│   │   ├── KA.yml
│   │   └── KO.yml
│   │   ├── MA.yml
│   │   └── MI.yml
│   │   ├── NL.yml
│   │   └── OR.yml
│   │   └── PO.yml
│   │   ├── PU.yml
│   │   └── RU.yml
│   │   ├── SP.yml
│   │   └── TA.yml
│   │   ├── TE.yml
│   │   └── TU.yml
│   │   ├── UR.yml
│   │   └── VI.yml
│   │   ├── WU.yml
```

## Beschrijving van bestanden

### Java Classes (`src/main/java/simpleclans/simpleclans/`)

- **SimpleclansPlugin.java** - Main plugin class voor de Simpleclans plugin
- **ModrinthUpdater.java** - Handelt updates van Modrinth af
- **UpdateNotifyListener.java** - Luistert naar update notifications

### Resources (`src/main/resources/`)

- **plugin.yml** - Plugin manifest bestand met plugin metadata
- **config.yml** - Configuratiebestand voor de plugin

## Summary

| Element | Type | Beschrijving |
|---------|------|-------------|
| java/ | Folder | Java source code |
| resources/ | Folder | Plugin configuration files |
| 3 Java classes | Files | Plugin logic implementation |
| 2 Config files | Files | Plugin configuration |
