# WatchCue TV Bridge

Runs on a Windows PC on the same network as the Google TV and forwards queued WatchCue reminders to PiPup.

## Setup

1. Install PiPup on the Google TV.
2. Find the TV IP address and ensure the PC can reach port 7979.
3. In the WatchCue backend Vercel project set TV_BRIDGE_KEY to a long random secret and redeploy.
4. Use the same secret locally as WATCHCUE_TV_BRIDGE_KEY.

PowerShell:

    $env:WATCHCUE_TV_BRIDGE_KEY = "same-secret-as-vercel"
    .\tv-bridge\watchcue-tv-bridge.ps1 -TvHost "192.168.0.123" -DeviceId "living-room-tv"

The bridge polls the secure pending-jobs endpoint, sends each message to PiPup, then acknowledges successful delivery.
