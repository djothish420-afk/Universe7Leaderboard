# Universe7Leaderboard (source project)

A small Paper plugin source project for Universe 7 SMP. It reads known players' Bukkit statistics and uses Vault for current economy balances, then creates simple in-world TextDisplay leaderboards.

## Commands
- `/u7lb help`
- `/u7lb top kills`
- `/u7lb top deaths`
- `/u7lb top playtime`
- `/u7lb top money`
- `/u7lb create kills` (run at the desired hologram location)
- `/u7lb create deaths`
- `/u7lb create playtime`
- `/u7lb create money`
- `/u7lb refresh`
- `/u7lb remove kills` (also `deaths`, `playtime`, `money`)

## Build
This ZIP contains source code, not a prebuilt JAR.
1. Install Java 21+ and Apache Maven on a computer.
2. Extract the ZIP.
3. Open a terminal in the extracted `Universe7Leaderboard` folder.
4. Run `mvn package`.
5. Copy `target/Universe7Leaderboard.jar` to your server's `plugins/` folder.
6. Restart the server.

## Notes
- Compile target is Paper 1.21.4 API. Your server reports Paper 26.2, so you must compile/test against your exact server API if the 1.21.4 dependency is not compatible. Do not install on a production server before testing a backup/staging server.
- The plugin uses Bukkit's saved offline statistics; availability depends on the server's saved stats files and API support.
- Playtime is converted from ticks to time. Economy leaderboard needs a Vault-compatible economy provider.
- This is a starter implementation and has not been compiled or tested against your host's Paper 26.2 build. If Maven reports API incompatibilities, the dependency and API calls need adjustment for your server version.
