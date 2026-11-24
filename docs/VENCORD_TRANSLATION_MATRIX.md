# Vencord → ValancedX: vollständiger AST-Inventarbericht

Revision: ce9355e9a93e369c7295fa162ad563f7f97b1ff7

## Umfang

- userPlugins: 167
- internalDefinitions: 20
- files: 425
- codeFiles: 308
- parseErrors: 0
- bytes: 1461719
- modes: {"compiled":8,"bundled":5,"repository":24,"manual":130}
- features: {"Auth-API":10,"CSS":49,"Commands":5,"ContextMenu":22,"DOM/Browser":32,"Desktop-Native":13,"Dynamischer Code":1,"Flux":42,"JSX/React":85,"MessageEvents":6,"Modulsuche":39,"Nachrichtenaktionen":3,"Netzwerk":26,"Node/Electron":7,"Settings":99,"UI-Hooks":51,"Vencord-API":119,"Webpack":115,"Webpack-Patches":129}

## Plugin-Matrix

| Plugin | Dateien | Hooks | Ergebnis |
|---|---:|---:|---|
| AccountPanelServerProfile | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| AddAttachments | 2 | 0 | repository · wolfie.add-attachments |
| AlwaysAnimate | 1 | 6 | bundled · veyra.port.animate |
| AlwaysExpandRoles | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| AlwaysTrust | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| AnonymiseFileNames | 1 | 2 | repository · k1ngop.anonymous-file-names |
| WebRichPresence (arRPC) | 1 | 0 | manual · Vencord-APIs: Notices |
| AutoDNDWhilePlaying | 2 | 0 | manual · Vencord-APIs: Settings, UserSettings |
| BetterFolders | 4 | 5 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| BetterGifAltText | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| BetterGifPicker | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| BetterRoleContext | 2 | 3 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings, UserSettings · Plugin-Abhängigkeiten: UserSettingsAPI |
| BetterRoleDot | 1 | 4 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| BetterSearch | 1 | 3 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| BetterSessions | 8 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: DataStore, Notifications, Settings · Plugin-Abhängigkeiten: betterSessions |
| BetterSettings | 3 | 6 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings, Styles · Plugin-Abhängigkeiten: vencordToolbox |
| BetterUploadButton | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| BetterYouTubeEmbeds | 5 | 0 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: Settings |
| BiggerStreamPreview | 1 | 0 | manual · Vencord-APIs: ContextMenu |
| BlurNSFW | 1 | 1 | repository · thororen.nsfw-blur |
| CallTimer | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| CharacterCounter | 3 | 2 | bundled · veyra.port.char-counter |
| ClearURLs | 2 | 0 | bundled · veyra.port.clean-urls |
| ClientTheme | 6 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: Settings, Styles · Plugin-Abhängigkeiten: clientTheme |
| ColorSighted | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| ConsoleJanitor | 2 | 11 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| ConsoleShortcuts | 2 | 1 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| CopyEmojiMarkdown | 2 | 0 | compiled · veyra.vencord.generated.copyemojimarkdown |
| CopyFileContents | 3 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| CopyUserURLs | 1 | 0 | compiled · veyra.vencord.generated.copyuserurls |
| CrashHandler | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Notifications, Settings, index |
| CustomCommands | 5 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: Commands, Settings |
| CustomIdle | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Notices, Settings |
| CustomRPC | 4 | 1 | repository · dev.kmmiio99o.custom.rpc |
| Dearrow | 3 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| Decor | 22 | 6 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: DataStore, Settings · Plugin-Abhängigkeiten: decor |
| DevCompanion | 1 | 0 | manual · Vencord-APIs: Notifications, Settings |
| DirectFileDownload | 2 | 2 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| DisableCallIdle | 1 | 2 | repository · k1ngop.disable-call-idle |
| DontRoundMyTimestamps | 1 | 0 | compiled · veyra.vencord.generated.dontroundmytimestamps |
| Experiments | 1 | 7 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| ExpressionManager | 5 | 1 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings |
| F8Break | 1 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden |
| FakeNitro | 1 | 16 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: MessageEvents, Settings · Plugin-Abhängigkeiten: MessageEventsAPI |
| FakeProfileThemes | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| FavoriteEmojiFirst | 2 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| FixCodeblockGap | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| FixImagesQuality | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| FixSpotifyEmbeds | 2 | 0 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · Vencord-APIs: Settings |
| ForceOwnerCrown | 1 | 1 | repository · hakuxyz.force-owner-crown |
| FriendInvites | 1 | 0 | manual · Vencord-APIs: Commands |
| FullSearchContext | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings |
| GameActivityToggle | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: PluginManager, Settings, UserSettings · Plugin-Abhängigkeiten: UserSettingsAPI, vencordToolbox |
| GifPaste | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| GifProviderSwitcher | 4 | 5 | repository · dev.benjii.gif-providers |
| GreetStickerPicker | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| HideMedia | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: DataStore, MessageUpdater, Settings · Plugin-Abhängigkeiten: MessageUpdaterAPI |
| iLoveSpam | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| IgnoreActivities | 2 | 3 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings, UserSettings · Plugin-Abhängigkeiten: UserSettingsAPI, customRPC |
| ImageFilename | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| ImageLink | 2 | 1 | bundled · veyra.port.image-links |
| ImageZoom | 6 | 3 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings · Plugin-Abhängigkeiten: imageZoom |
| ImplicitRelationships | 2 | 9 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| IrcColors | 2 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| JumpToTop | 2 | 1 | repository · dev.tralwdwdd.jump-to-top |
| KeepCurrentChannel | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: DataStore |
| LoadingQuotes | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| MemberCount | 6 | 3 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| MentionAvatars | 3 | 3 | repository · palmdevs.mention-avatars |
| MessageClickActions | 2 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: PluginManager, Settings · Plugin-Abhängigkeiten: noReplyMention |
| MessageLatency | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| MessageLinkEmbeds | 1 | 1 | repository · palmdevs.message-link-preview |
| MessageLogger | 5 | 13 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, MessageUpdater, Settings, Styles · Plugin-Abhängigkeiten: MessageUpdaterAPI |
| MoreQuickReactions | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| MusicRichPresence | 3 | 0 | repository · bleelblep.multi-scrobbler |
| MutedServersActivityBadge | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| MutualGroupDMs | 2 | 2 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NewGuildSettings | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings |
| NoBlockedMessages | 1 | 3 | repository · palmdevs.hide-blocked-messages |
| DisableDeepLinks | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoDevtoolsWarning | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoF1 | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoMaskedUrlPaste | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoMiddleClickPaste | 1 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden |
| NoMosaic | 1 | 3 | repository · dxckie.no-mosaic |
| NoOnboardingDelay | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoPendingCount | 1 | 4 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| NoProfileThemes | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoReplyMention | 1 | 1 | compiled · veyra.vencord.generated.noreplymention |
| NoServerEmojis | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| NoSystemBadge | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoTypingAnimation | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NoUnblockToJump | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| NotificationVolume | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| OnePingPerDM | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| oneko | 1 | 0 | repository · hakuxyz.oneko |
| OpenInApp | 3 | 2 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| OverrideForumDefaults | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| PauseInvitesForever | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| PermissionFreeWill | 2 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| PermissionsViewer | 6 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings · Plugin-Abhängigkeiten: betterRoleContext, permissionsViewer |
| petpet | 1 | 0 | repository · dev.cocobo1.petpet |
| PictureInPicture | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| PinDMs | 6 | 4 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings · Plugin-Abhängigkeiten: pinDms |
| PlainFolderIcon | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| PlatformIndicators | 2 | 3 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Badges, MemberListDecorators, MessageDecorations, Settings · Plugin-Abhängigkeiten: MemberListDecoratorsAPI, MessageDecorationsAPI |
| PreviewMessage | 2 | 0 | manual · Vencord-APIs: ChatButtons, Commands |
| QuickMention | 2 | 0 | manual · Noch keine geprüfte mobile Semantik/ABI |
| QuickReply | 2 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: PluginManager, Settings · Plugin-Abhängigkeiten: noBlockedMessages, noReplyMention |
| ReactErrorDecoder | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| ReadAllNotificationsButton | 2 | 0 | repository · dev.nexza.read-all-notifications |
| RelationshipNotifier | 5 | 3 | repository · bleelblep.relationship-notifier |
| ReplaceGoogleSearch | 2 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: ContextMenu, Settings |
| ReplyTimestamp | 3 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| RevealAllSpoilers | 1 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| ReverseImageSearch | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu |
| ReviewDB | 13 | 3 | repository · bleelblep.review-db |
| RoleColorEverywhere | 1 | 8 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| SecretRingToneEnabler | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| SendTimestamps | 2 | 0 | compiled · veyra.vencord.generated.sendtimestamps |
| ServerInfo | 4 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: ContextMenu · Plugin-Abhängigkeiten: DynamicImageModalAPI |
| ServerListIndicators | 1 | 0 | manual · Vencord-APIs: ServerList, Settings · Plugin-Abhängigkeiten: ServerListAPI |
| ShikiCodeblocks | 20 | 2 | repository · palmdevs.shiki-codeblocks |
| ShowAllMessageButtons | 1 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| ShowConnections | 3 | 2 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: PluginManager, Settings · Plugin-Abhängigkeiten: openInApp |
| ShowHiddenChannels | 3 | 23 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings · Plugin-Abhängigkeiten: showHiddenChannels |
| ShowHiddenThings | 2 | 5 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| ShowMeYourName | 2 | 1 | repository · bleelblep.show-tag |
| ShowTimeoutDuration | 3 | 1 | repository · palmdevs.show-timeout-duration |
| SilentMessageToggle | 1 | 0 | compiled · veyra.vencord.generated.silentmessagetoggle |
| SilentTyping | 1 | 1 | bundled · veyra.port.no-typing |
| SortFriendRequests | 2 | 2 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| SpotifyControls | 6 | 3 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: PluginManager, Settings, Styles · Plugin-Abhängigkeiten: openInApp |
| SpotifyCrack | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| SpotifyShareCommands | 1 | 0 | manual · Vencord-APIs: Commands |
| StartupTimings | 2 | 0 | manual · Plugin-Abhängigkeiten: _core |
| StickerPaste | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| StreamerModeOnStream | 1 | 0 | manual · Noch keine geprüfte mobile Semantik/ABI |
| SuperReactionTweaks | 2 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| TextReplace | 2 | 0 | compiled · veyra.vencord.generated.textreplace |
| ThemeAttributes | 2 | 4 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| Translate | 9 | 0 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: ChatButtons, ContextMenu, Settings |
| TypingIndicator | 2 | 2 | repository · palmdevs.better-typing-indicators |
| TypingTweaks | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| Unindent | 1 | 1 | compiled · veyra.vencord.generated.unindent |
| UnlockedAvatarZoom | 2 | 1 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| UnsuppressEmbeds | 1 | 0 | manual · Vencord-APIs: ContextMenu |
| UserMessagesPronouns | 5 | 2 | repository · bleelblep.pronouns |
| UserVoiceShow | 4 | 2 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: MemberListDecorators, MessageDecorations, PluginManager, Settings · Plugin-Abhängigkeiten: MemberListDecoratorsAPI, MessageDecorationsAPI, showHiddenChannels |
| USRBG | 1 | 3 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| ValidReply | 2 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| ValidUser | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| VoiceChatDoubleClick | 1 | 2 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| VcNarrator | 3 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: Settings |
| VencordToolbox | 3 | 1 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Notifications, PluginManager, Settings |
| ViewIcons | 1 | 5 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings · Plugin-Abhängigkeiten: DynamicImageModalAPI |
| ViewRaw | 2 | 0 | manual · DOM/CSS muss in React Native nachgebaut werden · Vencord-APIs: ContextMenu, Settings |
| VoiceDownload | 2 | 1 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele |
| VoiceMessages | 8 | 1 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: ContextMenu, Settings |
| VolumeBooster | 2 | 5 | manual · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| WebContextMenus | 1 | 12 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| WebKeybinds | 1 | 2 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| WebPWA | 2 | 3 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Themes |
| WebScreenShare | 2 | 4 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings, UserSettings |
| WebScreenShareFixes | 1 | 3 | manual · DOM/CSS muss in React Native nachgebaut werden · Webpack-Patchanker sind keine Metro-Hook-Ziele · Vencord-APIs: Settings |
| WhoReacted | 2 | 3 | repository · palmdevs.who-reacted |
| XSOverlay | 3 | 0 | manual · Desktop-Native/Node/Electron benötigt Android-Adapter · Vencord-APIs: Settings |

## Interne Vencord-APIs

Diese Komponenten werden als Abhängigkeiten inventarisiert, nicht als Android-Plugins aktiviert.
- BadgeAPI — CSS, Desktop-Native, JSX/React, Netzwerk, UI-Hooks, Vencord-API, Webpack, Webpack-Patches
- ChatInputButtonAPI — Webpack-Patches
- CommandsAPI — Webpack-Patches
- ContextMenuAPI — Webpack, Webpack-Patches
- DynamicImageModalAPI — Webpack-Patches
- MemberListDecoratorsAPI — CSS, Webpack-Patches
- MessageAccessoriesAPI — Webpack-Patches
- MessageDecorationsAPI — CSS, Webpack-Patches
- MessageEventsAPI — Webpack-Patches
- MessagePopoverAPI — Webpack-Patches
- MessageUpdaterAPI — Webpack-Patches
- NoticesAPI — UI-Hooks, Vencord-API, Webpack-Patches
- ServerListAPI — Webpack-Patches
- UserSettingsAPI — Webpack-Patches
- ConcatenatedComponentExtractor — Webpack, Webpack-Patches
- FixDiscordCss — DOM/Browser
- NoTrack — Flux, Settings, Vencord-API, Webpack, Webpack-Patches
- Settings — DOM/Browser, Desktop-Native, JSX/React, Settings, Vencord-API, Webpack, Webpack-Patches
- SupportHelper — Commands, DOM/Browser, Desktop-Native, Flux, JSX/React, Nachrichtenaktionen, Settings, UI-Hooks, Vencord-API, Webpack, Webpack-Patches
- VesktopPatches — DOM/Browser, Desktop-Native, Webpack-Patches

Statische Analyse und Compiler-Ableitung sind keine bestätigten Gerätelaufzeittests.
