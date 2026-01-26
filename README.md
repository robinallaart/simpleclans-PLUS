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

### General Commands
| Command | Description | Permission |
|---------|-------------|------------|
| /clan create <name> | Create a new clan | simpleclans.create |
| /clan invite <player> | Invite a player to your clan | simpleclans.invite |
| /clan join <name> | Accept an invitation | simpleclans.join |
| /clan leave | Leave your current clan | simpleclans.leave |
| /clan info [name] | Display clan details | simpleclans.info |
| /clan update | Update to the newest version | simpleclans.admin |
| /clan chat | Toggle clan chat mode | simpleclans.chat |
| /clan chatmsg <message> | Send a single clan message | simpleclans.chatmsg |
| /clan list | Display all players and clans | simpleclans.list |

### CO-Leader/Leader Commands
| Command | Description | Permission |
|---------|-------------|------------|
| /clan promote <player> | Promote a clan member | simpleclans.promote |
| /clan demote <player> | Demote a clan member | simpleclans.demote |
| /clan disband | Disband your clan (only leader) | simpleclans.disband |

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| /clan admin promote <player> <clan> | Promote a player in a specific clan | simpleclans.admin.promote |
| /clan admin demote <player> <clan> | Demote a player in a specific clan | simpleclans.admin.demote |
| /clan admin kick <player> <clan> | Kick a player from a clan | simpleclans.admin.kick |
| /clan admin disband <clan> | Disband any clan | simpleclans.admin.disband |
| /clan admin purge | Purge clan data (not implemented yet) | simpleclans.admin.purge |
| /clan admin reset | Reset clan stats (not implemented yet) | simpleclans.admin.reset |
| /clan admin place | Place clan-related items/blocks (not implemented yet) | simpleclans.admin.place |
| /clan admin invite <player> <clan> | Invite a player to any clan (not implemented yet) | simpleclans.admin.invite |
| /clan admin reload | Reload plugin configuration | simpleclans.admin.reload |
| /clan admin help | Display all admin commands | simpleclans.admin |

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
