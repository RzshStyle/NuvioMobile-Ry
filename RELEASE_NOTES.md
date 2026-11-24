# Nuvio + IPTV Mobile 1.1.0-beta.3-iptv.1

## What's new

### Recordings
- **Record to your network folder from the phone:** set Storage to the network folder and each recording is copied there when it finishes, one file per show, so your TVs can watch it too. Until the folder can be reached, recordings wait on the phone and still play here
- **Episode descriptions** in a recording's details. When the guide had none, the description comes from TMDB
- The player shows the **recording's poster** on the pause screen and in the notification, instead of the channel logo

### Live TV
- **Backups with headings:** the Backups list groups channels by playlist, add-on or quality, in the order you pick (Settings → Live TV → Watching → Backups & Game Channels). The order is shared with your TVs
- **Shows that already aired stay in the guide** when a guide update stops listing them (up to your Past Shows Kept setting)
- **Find in Nuvio** on a show in the guide, or in the player's channel panel, opens the movie or series page when there's one clear match
- **French:** Live TV, the guide, On Demand, Recordings, the Calendar, Setup Sync and Multiview are now in French

### Sports
- **What you follow loads first** when the app opens, and followed leagues' rows (UFC, F1, golf) are saved between launches, with last week's results

### Faster and lighter
- Big M3U playlists reload without holding a second copy, On Demand catalogue updates and Sports channel matching use less memory, and VPN streams waste less (thanks to bArKEr)

### Nuvio Mobile 1.1.0-beta.3
- Built on Nuvio Mobile 1.1.0-beta.3: Traditional Chinese, subtitle names in the subtitle list, a badge for styled subtitles, bigger subtitle sizes (up to 60), Jellyfin and Emby sign-ins kept per profile, and better MKV, DTS and Blu-ray playback

## Fixed
- **Network folder:** each device keeps its own address, so a phone on Tailscale and the TVs at home no longer overwrite each other. Recordings now says when the folder can't be reached, and copying to it is much lighter on the phone
- A TV recording that stopped before it finished now shows as ended (watchable and deletable) instead of stuck as "recording"
- **VPN:** a quiet WireGuard connection no longer counts as broken, and pressing play checks the connection straight away (thanks to bArKEr)
- **Name (A–Z)** sorts every group, reordered ones included, and skips tags like "US:" (2 comes before 10)
- Live channels no longer land in Continue Watching, where they couldn't play
- A custom Nuvio server stays chosen after the app is closed
- A slow DNS lookup can no longer hold up a playlist or channel
- Seek thumbnails on recordings no longer slow down playback
- On a first start, playlists load straight away

## Special thanks
- [@ghbarker](https://github.com/ghbarker) (bArKEr) for the VPN fixes and the speed and memory work
