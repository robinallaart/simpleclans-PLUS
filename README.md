#  Simpleclans-PLUS


**Simpleclans-PLUS** is a lightweight and efficient clan management plugin for servers.  
It provides a structured system for creating, managing, and communicating within clans.  
Designed for **performance**, **simplicity**, and **modern Minecraft compatibility**.



### Features

**Update notify**
• Get notified when the version is outdated and simply update it using the command 
`/clan update`.  

**Clan Management**
• Create, join, leave, and disband clans  
• Role hierarchy: `Leader`, `Co-Leader`, `Member`, `Recruit`  
• Invite, promote, and demote members with permission control  

**Clan Chat System**
• Private clan chat toggle → `/clanchat`  
• Send a one-time clan message → `/clanchatmsg <message>`  
• Clear formatting for in-clan communication  

**Clan Statistics**
• Automatic clan level calculation based on kills  
• View information via `/clan info`  
• List all players and their clans with `/clanlist`  
### LuckPerms Permissions
| Command | Permissions |
|----------|-------------|
| `/clan create <name>` | simpleclans.create |
| `/clan invite <player>` | simpleclans.invite |
| `/clan join <name>` | simpleclans.join |
| `/clan leave` | simpleclans.leave |
| `/clan info [name]` | simpleclans.info |
| `/clan chat` | simpleclans.chat |
| `/clan chatmsg <message>` | simpleclans.chatmsg |
| `/clan list` | simpleclans.list |
| `/clan promote <player>` | simpleclans.promote |
| `/clan demote <player>` | simpleclans.demote |
| `/clan disband` | simpleclans.disband |
| `/clan update` | simpleclans.admin |

**PlaceholderAPI Integration**
• Seamless support for PlaceholderAPI  
• Works in scoreboards, chat formats, and tab lists  
```
%simpleclans_clan_name%
%simpleclans_clan_role%
%simpleclans_clan_level%
%simpleclans_clan_kills%
%simpleclans_member_count%
%simpleclans_online_members%
%simpleclans_clan_leader%
```

**Database System**
• Uses local **SQLite** database  
• No manual configuration required  
• Automatically manages clans, members, and invites  



### Commands

### general commands
| Command | Description |
|----------|-------------|
| `/clan create <name>` | Create a new clan |
| `/clan invite <player>` | Invite a player to your clan |
| `/clan join <name>` | Accept an invitation |
| `/clan leave` | Leave your current clan |
| `/clan info [name]` | Display clan details |
| `/clan update` | Updates to the newsest version |
| `/clan chat` | Toggle clan chat mode |
| `/clan chatmsg <message>` | Send a single clan message |
| `/clan list` | Display all players and clans |

### CO-Leader/Leader commands
| Command | Description |
|----------|-------------|
| `/clan promote <player>` | Promote a clan member |
| `/clan demote <player>` | Demote a clan member |
| `/clan disband` | Disband your clan(only leader) |
### Configuration


**Database:** SQLite (auto-created)  
**Config File:** Minimal configuration for message and prefix customization  



### Compatibility


• Fully compatible with **Spigot**, **Paper**, and **Purpur**  
• Automatically detects and integrates with **PlaceholderAPI**  



### Planned Additions


• Clan alliances and rivalries  
• Clan homes and teleportation  
• PvP statistics and leaderboards   
• Adding menus



### Plugin Information


**Name:** Simpleclans-PLUS  
**Author:** Robin  
**License:** MIT  
**Database:** SQLite  



### Summary


Simpleclans-PLUS provides a complete, stable, and expandable clan system  
built for modern Minecraft servers. It is simple to configure, efficient,  
and integrates smoothly with PlaceholderAPI and other major plugins.
