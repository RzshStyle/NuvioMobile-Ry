# Nuvio + IPTV Mobile 0.6.12-beta-iptv.4

## What's new

### Sports
- **Official highlights and condensed games** on finished games, for ESPN, NHL and MLB, plus YouTube highlights that play right in the app
- **Replays from your sports addons for every sport,** not just football, labelled Full game, Condensed or Highlights. They load faster and show as each addon answers
- When a highlight or replay finishes, you go back to the game's page
- Team colours are the same everywhere, and games weeks ahead show the real colours
- **AFCON qualifying** added, with each team's qualification status in its tables

### Multiview
- **A cleaner look:** the top bar is gone. Two small round buttons float in the corners, and each pane's channel name sits lightly at the bottom
- Buttons and pane names fade away after a few seconds. Tap to bring them back
- Panes use the whole screen. With the phone upright, two or three panes stack top to bottom
- **Filter the Sports games:** My Sports, All Events, Live Now, or one sport at a time
- Layout buttons show a small picture of each layout

### Live TV
- **A guide for each playlist:** paste a guide link when you add a playlist (Stalker portals too), and switch each playlist's guides on or off under **Guides**
- **On Demand from M3U playlists:** movies and series straight from the playlist
- **DNS setting:** System (default), Cloudflare or Google, under **Live TV settings → Updates**. Try it if channels say your provider's server can't be found. On System, the app tries Cloudflare and Google by itself when it needs to
- **Record series: new episodes only.** Switch it on for any series in Recordings to skip repeats
- **Repeating recordings:** a recording you set by hand can repeat every day, on weekdays, at weekends or on the days you pick
- **Past guide kept** can now be 14 days, and catch-up goes back up to 14 days
- **Hide Stray Entries:** leaves out odd leftovers some providers put in their playlists, like a single episode sitting in its own group
- **Start-Up Report** under **Live TV settings → Help**. Copy or share it when something isn't working
- A playlist that can't load now says which server address couldn't be found

### Profiles
- Profiles you keep matched on the TV share the same Live TV choices on the phone too

### Calendar
- F1, NASCAR and IndyCar weekends show each session on its own day and time
- Golf and tennis tournaments show on every day they're on
- **Airing Today** now lists shows it used to miss (lots of news shows), with their Live TV options

## Fixed
- **Deleting a playlist** now clears everything it left behind (hidden groups, favorites, custom group channels, channel order, reminders and scheduled recordings). Leftovers from playlists you already deleted are cleared too
- **Sync:** a playlist order you change is no longer undone, and favorites, hidden channels and your own groups now combine between your phone and TV instead of one overwriting the other
- **Record series** now records the airing you picked it from, instead of waiting for a later repeat
- **Multiview** no longer crashes on Android 9 phones, and streams that say they're one kind but are another now play
- **Sports:** channels you fixed for a game stay fixed after a channel search or after your provider renames the channel
- Stalker portal update errors no longer show your portal account details
- The VPN can no longer end up on without a profile chosen, even after a sync
- Sports division standings show in the right order (NBA divisions came out worst first)
