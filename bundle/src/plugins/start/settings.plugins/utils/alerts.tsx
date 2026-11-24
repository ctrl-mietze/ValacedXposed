import {
    AlertActionCreators,
    ToastActionCreators,
} from '@valenced/discord/actions'
import { Design } from '@valenced/discord/design'
import { RootNavigationRef } from '@valenced/discord/modules/main_tabs_v2'
import { Component as ReactComponent, useState, useRef } from 'react'
import { Modal, View, Text as NativeText, Pressable } from 'react-native'
import type { ReactNode } from 'react'
import type { PluginApi } from '@valenced/plugins/types'
import {
    deleteStorageForPlugin,
    getPluginDependents,
    isPluginEnabledInActiveSlot,
    isPluginStarted,
    uninstallExternalPlugin,
} from '@valenced/plugins/_'
import { getErrorStack } from '@valenced/utils/error'
import { deleteJsonStorageForPlugin } from '~plugins/preinit/api/json-storage'
import PluginClearDataConfirmationAlert from '../components/PluginClearDataConfirmationAlert'
import PluginDependentsChoiceAlert from '../components/PluginDependentsChoiceAlert'
import PluginHasDependenciesAlert from '../components/PluginHasDependenciesAlert'
import PluginMissingDependenciesAlert from '../components/PluginMissingDependenciesAlert'
import PluginUninstallConfirmationAlert from '../components/PluginUninstallConfirmationAlert'
import RepoRemoveConfirmationAlert from '../components/RepoRemoveConfirmationAlert'
import type { AnyPlugin } from '@valenced/plugins/_'
import type { Repo } from '@valenced/plugins/_/repositories'

const { Text } = Design

export function showPluginClearDataConfirmation(
    plugin: AnyPlugin,
    callback: () => void,
) {
    const KEY = 'plugin-clear-data-confirmation'

    async function action() {
        try {
            await deleteStorageForPlugin(plugin)
            // Trigger update for JSON storage
            await deleteJsonStorageForPlugin(plugin)
        } catch (e) {
            alert(getErrorStack(e))
        }
        callback()
    }

    AlertActionCreators.openAlert(
        KEY,
        <PluginClearDataConfirmationAlert plugin={plugin} action={action} />,
    )
}

export function showPluginUninstallConfirmation(
    plugin: AnyPlugin,
    callback: () => void,
) {
    const KEY = 'plugin-uninstall-confirmation'

    async function action() {
        // Uninstalling also deletes data
        await uninstallExternalPlugin(plugin)
        callback()
    }

    AlertActionCreators.openAlert(
        KEY,
        <PluginUninstallConfirmationAlert
            plugin={plugin}
            dependencies={getCascadeDisabledDependents(plugin)}
            action={action}
        />,
    )
}

/** Enabled required dependents that disabling this plugin cascades transitively. */
function getCascadeDisabledDependents(plugin: AnyPlugin) {
    const disabled = new Set<AnyPlugin>()
    if (!isPluginEnabledInActiveSlot(plugin)) return []

    const visit = (p: AnyPlugin) => {
        for (const dep of getPluginDependents(p))
            if (!disabled.has(dep) && isPluginEnabledInActiveSlot(dep)) {
                disabled.add(dep)
                visit(dep)
            }
    }

    visit(plugin)
    return [...disabled]
}

const SettingsOverlayKey = 'vexcord-plugin-settings-fallback'

class SettingsErrorBoundary extends ReactComponent<{ children: ReactNode }, { error?: string }> {
    state: { error?: string } = {}
    static getDerivedStateFromError(error: unknown) { return { error: String(error) } }
    render() {
        if (this.state.error) return (
            <View style={{padding: 16}}>
                <NativeText selectable style={{color: '#f1eaff'}}>Plugin settings could not render: {this.state.error}</NativeText>
            </View>
        )
        return this.props.children
    }
}
function PluginSettingsOverlay({plugin}: {plugin: AnyPlugin}) {
    const Screen = plugin.SettingsComponent
    const [visible, setVisible] = useState(true)
    const closing = useRef(false)
    const close = () => {
        if (closing.current) return
        closing.current = true
        // Close the native modal independently of Discord's alert store.
        setVisible(false)
        setTimeout(() => {
            try { AlertActionCreators.dismissAlert(SettingsOverlayKey) } catch {}
        }, 350)
    }
    if (!visible) return null
    return (
        <Modal visible={visible} animationType="slide" onRequestClose={close}>
            <View style={{flex:1,backgroundColor:'#10111a'}}>
                <View style={{padding:16,paddingTop:28,flexDirection:'row',alignItems:'center'}}>
                    <NativeText style={{color:'#f1eaff',fontWeight:'700',fontSize:19,flex:1}}>{plugin.manifest.name}</NativeText>
                    <Pressable accessibilityRole="button" accessibilityLabel="Close plugin settings" onPress={close} style={{padding:12}}>
                        <NativeText style={{color:'#c9b3ff',fontWeight:'600'}}>Close</NativeText>
                    </Pressable>
                </View>
                <View style={{flex:1}}>
                    <SettingsErrorBoundary>
                        {Screen && plugin.api ? <Screen api={plugin.api as PluginApi<any>}/> :
                            <NativeText style={{color:'#f1eaff',padding:16}}>Start the plugin to open its settings.</NativeText>}
                    </SettingsErrorBoundary>
                </View>
            </View>
        </Modal>
    )
}

export function openPluginSettings(plugin: AnyPlugin) {
    if (!plugin.SettingsComponent || !isPluginStarted(plugin)) return
    // Wait for the options sheet to close and for a lazily registered route.
    void import('../plugins').then(({ensurePluginSettingsRoute}) => {
        ensurePluginSettingsRoute(plugin)
    }).catch(() => {}).finally(() => {
        setTimeout(() => {
            if (!isPluginStarted(plugin)) return
            try {
                const navigation = RootNavigationRef.getRootNavigationRef()
                // navigate() silently ignores routes registered in a different
                // navigator; use the explicit overlay if the root cannot open it.
                if (navigation.isReady() && navigation.getRootState()?.routeNames?.includes(plugin.manifest.id)) {
                    navigation.navigate(plugin.manifest.id)
                    return
                }
            } catch {}
            try {
                AlertActionCreators.dismissAlert(SettingsOverlayKey)
                AlertActionCreators.openAlert(SettingsOverlayKey, <PluginSettingsOverlay plugin={plugin}/>)
            } catch (e) {
                showErrorToast(String(e), 'PLUGIN_SETTINGS_OPEN_ERROR')
            }
        }, 250)
    })
}

export function showPluginHasDependenciesAlert(
    plugin: AnyPlugin,
    dependencies: AnyPlugin[],
    action: () => Promise<void>,
) {
    AlertActionCreators.openAlert(
        'plugin-has-dependencies',
        <PluginHasDependenciesAlert
            plugin={plugin}
            dependencies={dependencies}
            action={action}
        />,
    )
}

export function showPluginMissingDependenciesAlert(
    plugin: AnyPlugin,
    dependencies: { id: string; range: string }[],
    action: () => unknown,
) {
    AlertActionCreators.openAlert(
        'plugin-missing-dependencies',
        <PluginMissingDependenciesAlert
            plugin={plugin}
            dependencies={dependencies}
            action={action}
        />,
    )
}

export function showRemoveRepoConfirmation(
    repo: Repo,
    callback: () => Promise<void> | void,
) {
    const KEY = 'repo-remove-confirmation'

    async function action() {
        await callback()
    }

    AlertActionCreators.openAlert(
        KEY,
        <RepoRemoveConfirmationAlert repo={repo} action={action} />,
    )
}

export function showPluginHasDependentsAlert(
    plugin: AnyPlugin,
    required: AnyPlugin[],
    optional: AnyPlugin[],
    action: (keepRunning: Set<string>) => Promise<void>,
) {
    AlertActionCreators.openAlert(
        'plugin-has-dependents',
        <PluginDependentsChoiceAlert
            title="Plugin currently in use"
            content={
                <Text color="text-default">
                    Other plugins are currently using{' '}
                    <Text variant="text-md/semibold" color="text-default">
                        {plugin.manifest.name}
                    </Text>
                    .{'\n'}
                    {required.length > 0 &&
                        'Plugins that need it will be disabled. '}
                    {optional.length > 0 &&
                        'Select which plugins to keep running. They will restart without this plugin.'}
                </Text>
            }
            confirmText="Continue"
            toggleLabel="Keep running"
            locked={required}
            selectable={optional}
            action={action}
        />,
    )
}

/** Offers disabling dependencies nothing else uses after this plugin is disabled. */
export function showPluginUnusedDependenciesAlert(
    plugin: AnyPlugin,
    dependencies: AnyPlugin[],
    action: (disable: Set<string>) => Promise<void>,
) {
    AlertActionCreators.openAlert(
        'plugin-unused-dependencies',
        <PluginDependentsChoiceAlert
            title="Disable unused plugins?"
            content={
                <Text color="text-default">
                    These plugins were enabled for{' '}
                    <Text variant="text-md/semibold" color="text-default">
                        {plugin.manifest.name}
                    </Text>
                    , and no other plugin uses them now. Select which plugins to
                    disable.
                </Text>
            }
            confirmText="Disable"
            cancelText="Keep all"
            toggleLabel="Disable"
            locked={[]}
            selectable={dependencies}
            action={action}
        />,
    )
}

/** Offers restarting the running plugins that could not link this one when they started. */
export function showPluginRelinkAlert(
    plugin: AnyPlugin,
    dependents: AnyPlugin[],
    action: (restart: Set<string>) => Promise<void>,
) {
    AlertActionCreators.openAlert(
        'plugin-relink-dependents',
        <PluginDependentsChoiceAlert
            title="Restart plugins for features?"
            content={
                <Text color="text-default">
                    These plugins started while{' '}
                    <Text variant="text-md/semibold" color="text-default">
                        {plugin.manifest.name}
                    </Text>{' '}
                    was unavailable. You may receive new features if you restart
                    them now.
                </Text>
            }
            confirmText="Restart"
            cancelText="No thanks"
            confirmVariant="primary"
            toggleLabel="Restart"
            locked={[]}
            selectable={dependents}
            action={action}
        />,
    )
}

export function showErrorToast(message: string, key: string) {
    ToastActionCreators.open(`VALENCED_PLUGINS_ERROR:${key}`, {
        text: message,
        variant: 'critical',
    })
}
