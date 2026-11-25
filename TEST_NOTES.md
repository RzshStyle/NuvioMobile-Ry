# Test build notes

<!--
What's changed on dev since the last test build. When Build Test App's "What changed" box is left
empty, the test build uses this list (headings with nothing under them are left out), and then
removes the lines it used, so the next test build only shows what's new since. Add lines under the
headings as changes land on dev. Typing in the box uses the box instead and leaves this file alone.
-->

## Live TV
- Playlists with every group hidden (or hidden on this profile) show dimmed in the playlist menu with the reason, instead of disappearing; tap to manage
- A playlist hidden on your profile can be shown again when there's only one profile, and it says it's hidden on this profile
- The same guide given twice in different forms (e.g. a playlist's Xtream guide link and the one in its playlist file) is downloaded and read once
- Stalker portals that refuse the first login address now try the others instead of giving up
- Live channels connect to the provider once instead of three times (full screen and Multiview), which may stop streams cutting out when Multiview or another device starts
- When a provider drops a live channel, the player reconnects by itself (with Auto-reconnect on), keeping your audio and subtitle choices, instead of stopping
- Live TV player error messages (can't open a channel, provider refused, too many connections, connection lost and the rest) now show in the phone's language

## Recordings
- A series can have its own Start early and Keep recording after times (long-press it in Recordings › Series); Default keeps using the Recordings setting, and episodes already set change too
- Recordings shows up when a network folder is set up, so the TVs' recordings there can be opened with nothing recorded on this phone
- The player shows the recording's poster instead of the channel logo

## Faster and lighter
- Big M3U playlists reload without holding a second copy
- On Demand catalogue updates and Sports guide matching use less memory
- Streams over the VPN reuse a few buffers instead of making new ones
