import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import {
    AlertActionCreators,
    ToastActionCreators,
} from '@valenced/discord/actions'
import {
    refreshSettings,
    registerSettingsItem,
} from '@valenced/discord/modules/settings'
import {
    isPluginErrored,
    isPluginPendingReload,
    isPluginStarted,
    isPluginStopped,
    pEmitter,
    pList,
} from '@valenced/plugins/_'
import { lookupGeneratedIconComponent } from '@valenced/utils/discord'
import { useLayoutEffect } from 'react'
import { View, Pressable, Text } from 'react-native'
import { Setting } from '../settings/constants'
// Alert implementations depend on Discord's design barrel, which is not
// necessarily initialized at startup. Load each UI module only when needed.
import defer * as InstallConfirmationAlert from './components/PluginInstallConfirmAlert'
import defer * as InstallFailedAlert from './components/PluginInstallFailedAlert'
import defer * as FailedToStartAlert from './components/PluginsFailedToStartAlert'
import defer * as RequireReloadAlert from './components/PluginsRequireReloadAlert'
import type { StackScreenProps } from '@react-navigation/stack'
import type { ReactNavigationParamList } from '@valenced/externals/react-navigation'
import type { PluginApi } from '@valenced/plugins/types'

const PluginsRequireReloadAlertKey = 'plugins-require-reload'
const PluginsFailedToStartAlertKey = 'plugins-failed-to-start'

/// SETTINGS ROUTES

// A plugin can already be running when this lazily imported UI is loaded.
const registered = new WeakMap<object, unknown>()
export function ensurePluginSettingsRoute(plugin: import('@valenced/plugins/_').AnyPlugin) {
    if (!isPluginStarted(plugin) || !plugin.api) return
    if (registered.get(plugin) === plugin.api) return

    if (plugin.SettingsComponent) {
        const api = plugin.api as PluginApi<any>
        const Component = plugin.SettingsComponent!

        function PluginSettings({
            navigation,
        }: StackScreenProps<ReactNavigationParamList>) {
            // biome-ignore lint/correctness/useExhaustiveDependencies: We only want to set options once
            useLayoutEffect(() => {
                navigation.setOptions({title: plugin.manifest.name})
            }, [])

            return <View style={{flex:1}}>
                <Pressable accessibilityRole="button" accessibilityLabel="Back to plugins"
                    onPress={() => navigation.goBack()} style={{padding:12}}>
                    <Text style={{color:'#c9b3ff',fontWeight:'600'}}>‹ Back to plugins</Text>
                </Pressable>
                <View style={{flex:1}}><Component api={api}/></View>
            </View>
        }

        const removeRoute = registerSettingsItem(plugin.manifest.id, {
                parent: Setting.Valenced,
                type: 'route',
                IconComponent: plugin.manifest.icon
                    ? () => <TableRowAssetIcon name={plugin.manifest.icon!} />
                    : undefined,
                useTitle: () => plugin.manifest.name,
                screen: {
                    route: plugin.manifest.id,
                    getComponent: () => PluginSettings,
                },
            })
        registered.set(plugin, api)
        api.cleanup(() => {
            if (registered.get(plugin) !== api) return
            registered.delete(plugin)
            removeRoute()
            optionalAlert(refreshSettings)
        })
        optionalAlert(refreshSettings)
    }
}
pEmitter.on('statusUpdate', plugin => {
    optionalAlert(() => ensurePluginSettingsRoute(plugin))
})
for (const plugin of pList.values())
    optionalAlert(() => ensurePluginSettingsRoute(plugin))

/// RELOAD REQUIRED ALERT

function showPendingReloadAlertIfNeeded() {
    const plugins = [...pList.values()].filter(plugin =>
        isPluginPendingReload(plugin),
    )

    if (!plugins.length) return

    AlertActionCreators.dismissAlert(PluginsRequireReloadAlertKey)
    AlertActionCreators.openAlert(
        PluginsRequireReloadAlertKey,
        <RequireReloadAlert.default plugins={plugins} />,
    )
}

function showErrorAlertIfNeeded() {
    const plugins = [...pList.values()].filter(plugin =>
        isPluginErrored(plugin),
    )

    if (!plugins.length) return

    AlertActionCreators.dismissAlert(PluginsFailedToStartAlertKey)
    AlertActionCreators.openAlert(
        PluginsFailedToStartAlertKey,
        <FailedToStartAlert.default plugins={plugins} />,
    )
}

pEmitter.on('stateUpdate', plugin => {
    if (isPluginPendingReload(plugin)) optionalAlert(showPendingReloadAlertIfNeeded)
})

pEmitter.on('statusUpdate', plugin => {
    if (isPluginStopped(plugin) && isPluginErrored(plugin))
        optionalAlert(showErrorAlertIfNeeded)
})

/// PLUGIN INSTALL FEEDBACK

const pluginInstallToastKeyFor = (id: string) => `VALENCED_PLUGIN_INSTALL:${id}`
const PluginInstallFailedAlertKey = 'plugin-install-failed'
const PluginInstallConfirmAlertKey = 'plugin-install-confirm'

pEmitter.on('installReady', prompt => {
    AlertActionCreators.dismissAlert(PluginInstallConfirmAlertKey)
    AlertActionCreators.openAlert(
        PluginInstallConfirmAlertKey,
        <InstallConfirmationAlert.PluginFileInstallConfirmAlert prompt={prompt} />,
    )
})

pEmitter.on('install', result => {
    if (result.error !== false) {
        AlertActionCreators.dismissAlert(PluginInstallFailedAlertKey)
        AlertActionCreators.openAlert(
            PluginInstallFailedAlertKey,
            <InstallFailedAlert.default error={result.error} />,
        )
        return
    }

    if (result.pending) {
        // Applied on disk only, the new version loads at next reload
        showInstallToast(
            `Updated ${pList.get(result.id)?.manifest.name || result.id} ${result.version}, reload to apply`,
            result.id,
        )
        return
    }

    showInstallToast(
        result.updated
            ? `Updated ${result.manifest.name}`
            : `Installed ${result.manifest.name}`,
        result.manifest.id,
    )
})

function getCircleCheckIcon() {
    try { return lookupGeneratedIconComponent('CircleCheckIcon','CircleCheckIcon-secondary','CircleCheckIcon-primary') }
    catch { return undefined }
}

function showInstallToast(text: string, id: string) {
    ToastActionCreators.open(pluginInstallToastKeyFor(id), {
        text,
        icon: getCircleCheckIcon(),
    })
}

function optionalAlert(fn: () => void) {
    try { fn() } catch(e) {
        nativeLoggingHook('[ValancedX] Optional plugin notification unavailable: '+String(e),2)
    }
}
optionalAlert(showErrorAlertIfNeeded)
optionalAlert(showPendingReloadAlertIfNeeded)
